// Starts a HotSpot JVM inside the calling process and runs a main class in it.
//
// The JVM keeps its heap in native memory, so it is bound by what the device can spare rather than
// the heap limit ART puts on a process. HotSpot cannot be started twice in one process, so the
// process that calls this is meant to end once the run is over.
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <malloc.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <string>
#include <vector>

#define TAG "EmbeddedJvm"

#ifndef M_BIONIC_SET_HEAP_TAGGING_LEVEL
#define M_BIONIC_SET_HEAP_TAGGING_LEVEL (-204)
#define M_HEAP_TAGGING_LEVEL_NONE 0
#endif

namespace {

using CreateJavaVm = jint (*)(JavaVM **, void **, void *);

struct Launch {
    std::string libjvm;
    std::vector<std::string> preload;
    std::vector<std::string> options;
    std::string mainClass;
    std::vector<std::string> args;
    int exitCode = -1;
};

jint logJvmOutput(FILE *, const char *format, va_list args) {
    __android_log_vprint(ANDROID_LOG_INFO, TAG, format, args);
    return 0;
}

// System.exit ends up here. Nothing of the process is of use after the run, so it ends at once
void exitHook(jint code) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "JVM exited with %d", code);
    _exit(code);
}

std::vector<std::string> toStrings(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> strings;
    const jsize length = env->GetArrayLength(array);
    for (jsize i = 0; i < length; i++) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        const char *chars = env->GetStringUTFChars(value, nullptr);
        strings.emplace_back(chars);
        env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }
    return strings;
}

int describeException(JNIEnv *env) {
    if (!env->ExceptionCheck()) return 0;
    env->ExceptionDescribe();
    env->ExceptionClear();
    return 1;
}

void *runJvm(void *data) {
    auto *launch = static_cast<Launch *>(data);

    // HotSpot keeps flags in the top bits of native pointers, which a tagged heap rejects on free
    mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE);

    // A release build starts the process as not dumpable, which hands /proc/self over to root, and
    // the I/O accounting of the run in /proc/self/io is then out of reach of the process itself
    prctl(PR_SET_DUMPABLE, 1);

    void *handle = dlopen(launch->libjvm.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen failed: %s", dlerror());
        return nullptr;
    }
    // The linker namespace of an app does not search the runtime's directory, so a library another
    // one of it names would not be found. Loaded up front, in order, a name is matched instead
    for (auto &library: launch->preload) {
        if (dlopen(library.c_str(), RTLD_NOW | RTLD_GLOBAL) == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen failed: %s", dlerror());
            return nullptr;
        }
    }
    auto createJavaVm = reinterpret_cast<CreateJavaVm>(dlsym(handle, "JNI_CreateJavaVM"));
    if (createJavaVm == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI_CreateJavaVM missing: %s", dlerror());
        return nullptr;
    }

    std::vector<JavaVMOption> options;
    for (auto &option: launch->options) options.push_back({const_cast<char *>(option.c_str()), nullptr});
    options.push_back({const_cast<char *>("vfprintf"), reinterpret_cast<void *>(logJvmOutput)});
    options.push_back({const_cast<char *>("exit"), reinterpret_cast<void *>(exitHook)});

    JavaVMInitArgs initArgs{};
    // The NDK headers stop at 1.6, which HotSpot still accepts
    initArgs.version = JNI_VERSION_1_6;
    initArgs.nOptions = static_cast<jint>(options.size());
    initArgs.options = options.data();
    initArgs.ignoreUnrecognized = JNI_FALSE;

    JavaVM *vm = nullptr;
    JNIEnv *env = nullptr;
    const jint created = createJavaVm(&vm, reinterpret_cast<void **>(&env), &initArgs);
    if (created != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI_CreateJavaVM failed: %d", created);
        return nullptr;
    }

    jclass mainClass = env->FindClass(launch->mainClass.c_str());
    jmethodID main = mainClass == nullptr ? nullptr
            : env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (main == nullptr) {
        describeException(env);
        launch->exitCode = 1;
    } else {
        jobjectArray args = env->NewObjectArray(static_cast<jsize>(launch->args.size()),
                                                env->FindClass("java/lang/String"), nullptr);
        for (size_t i = 0; i < launch->args.size(); i++) {
            env->SetObjectArrayElement(args, static_cast<jsize>(i), env->NewStringUTF(launch->args[i].c_str()));
        }
        env->CallStaticVoidMethod(mainClass, main, args);
        launch->exitCode = describeException(env);
    }

    // Waits for the threads main left running, like the java launcher does
    vm->DestroyJavaVM();
    return nullptr;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_launchJvm(
        JNIEnv *env, jobject, jstring libjvm, jobjectArray preload, jobjectArray options,
        jstring mainClass, jobjectArray args) {
    const char *libjvmChars = env->GetStringUTFChars(libjvm, nullptr);
    const char *mainClassChars = env->GetStringUTFChars(mainClass, nullptr);
    Launch launch{libjvmChars, toStrings(env, preload), toStrings(env, options), mainClassChars,
                  toStrings(env, args)};
    env->ReleaseStringUTFChars(libjvm, libjvmChars);
    env->ReleaseStringUTFChars(mainClass, mainClassChars);

    // A thread of its own, so HotSpot does not share the caller's stack and thread state with ART
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 8 * 1024 * 1024);
    pthread_t thread;
    pthread_create(&thread, &attr, runJvm, &launch);
    pthread_attr_destroy(&attr);
    pthread_join(thread, nullptr);
    return launch.exitCode;
}
