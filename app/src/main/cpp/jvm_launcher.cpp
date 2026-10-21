// Starts a HotSpot JVM inside the app's process and runs a main class in it.
//
// The JVM keeps its heap in native memory, so it is bound by what the device can spare rather than
// the heap limit ART puts on a process. HotSpot cannot be started twice in one process, so the
// JVM stays for as long as the process does.
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <malloc.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <future>
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
    // Set once main is about to be called, to 0, or to why the JVM did not get that far
    std::promise<int> started;
};

// HotSpot hands every write it makes through here, files it opened itself (such as -Xlog:...:file=)
// included, so only the standard streams go to the log
jint logJvmOutput(FILE *stream, const char *format, va_list args) {
    if (stream != stdout && stream != stderr) return vfprintf(stream, format, args);
    __android_log_vprint(ANDROID_LOG_INFO, TAG, format, args);
    return 0;
}

// System.exit ends up here. The JVM cannot be shut down without ending the process, so it ends at once
void exitHook(jint code) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "JVM exited with %d", code);
    _exit(code);
}

std::vector<std::string> toStrings(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> strings;
    const jsize length = env->GetArrayLength(array);
    for (jsize i = 0; i < length; i++) {
        // JNI hands out array elements as jobject, which a String array holds as jstring
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i)); // NOLINT(cppcoreguidelines-pro-type-static-cast-downcast)
        const char *chars = env->GetStringUTFChars(value, nullptr);
        strings.emplace_back(chars);
        env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }
    return strings;
}

void describeException(JNIEnv *env) {
    if (!env->ExceptionCheck()) return;
    env->ExceptionDescribe();
    env->ExceptionClear();
}

// Why the JVM did not start, next to the JNI_CreateJavaVM codes, which are all negative
constexpr int LOAD_FAILED = 1;
constexpr int MAIN_MISSING = 2;

constexpr size_t JVM_THREAD_STACK_SIZE = 8UL * 1024 * 1024;

// Creates the JVM and finds the main method, returning 0 or why it could not
int createJvm(Launch *launch, JNIEnv **env, jclass *mainClass, jmethodID *main) {
    // HotSpot keeps flags in the top bits of native pointers, which a tagged heap rejects on free
    mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE);

    // A release build starts the process as not dumpable, which hands /proc/self over to root, and
    // the I/O accounting of the run in /proc/self/io is then out of reach of the process itself
    prctl(PR_SET_DUMPABLE, 1);

    void *handle = dlopen(launch->libjvm.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen failed: %s", dlerror());
        return LOAD_FAILED;
    }
    // The linker namespace of an app does not search the runtime's directory, so a library another
    // one of it names would not be found. Loaded up front, in order, a name is matched instead
    for (auto &library: launch->preload) {
        if (dlopen(library.c_str(), RTLD_NOW | RTLD_GLOBAL) == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen failed: %s", dlerror());
            return LOAD_FAILED;
        }
    }
    auto createJavaVm = reinterpret_cast<CreateJavaVm>(dlsym(handle, "JNI_CreateJavaVM"));
    if (createJavaVm == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI_CreateJavaVM missing: %s", dlerror());
        return LOAD_FAILED;
    }

    std::vector<JavaVMOption> options;
    options.reserve(launch->options.size() + 2);
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
    const jint created = createJavaVm(&vm, reinterpret_cast<void **>(env), &initArgs);
    if (created != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "JNI_CreateJavaVM failed: %d", created);
        return created;
    }

    *mainClass = (*env)->FindClass(launch->mainClass.c_str());
    *main = *mainClass == nullptr ? nullptr
            : (*env)->GetStaticMethodID(*mainClass, "main", "([Ljava/lang/String;)V");
    if (*main == nullptr) {
        describeException(*env);
        return MAIN_MISSING;
    }
    return 0;
}

void *runJvm(void *data) {
    auto *launch = static_cast<Launch *>(data);

    JNIEnv *env = nullptr;
    jclass mainClass = nullptr;
    jmethodID main = nullptr;
    const int created = createJvm(launch, &env, &mainClass, &main);
    launch->started.set_value(created);
    if (created == 0) {
        jobjectArray args = env->NewObjectArray(static_cast<jsize>(launch->args.size()),
                                                env->FindClass("java/lang/String"), nullptr);
        for (size_t i = 0; i < launch->args.size(); i++) {
            env->SetObjectArrayElement(args, static_cast<jsize>(i), env->NewStringUTF(launch->args[i].c_str()));
        }
        // Serves the app until its process ends, so returning means the host broke down
        env->CallStaticVoidMethod(mainClass, main, args);
        describeException(env);
        __android_log_print(ANDROID_LOG_ERROR, TAG, "The main method of the JVM returned");
    }

    delete launch;
    return nullptr;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_startJvm(
        JNIEnv *env, jobject, jstring libjvm, jobjectArray preload, jobjectArray options,
        jstring mainClass, jobjectArray args) {
    const char *libjvmChars = env->GetStringUTFChars(libjvm, nullptr);
    const char *mainClassChars = env->GetStringUTFChars(mainClass, nullptr);
    auto *launch = new Launch;
    launch->libjvm = libjvmChars;
    launch->preload = toStrings(env, preload);
    launch->options = toStrings(env, options);
    launch->mainClass = mainClassChars;
    launch->args = toStrings(env, args);
    env->ReleaseStringUTFChars(libjvm, libjvmChars);
    env->ReleaseStringUTFChars(mainClass, mainClassChars);
    std::future<int> started = launch->started.get_future();

    // A thread of its own, so HotSpot does not share the caller's stack and thread state with ART
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, JVM_THREAD_STACK_SIZE);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_t thread;
    const int threadCreated = pthread_create(&thread, &attr, runJvm, launch);
    pthread_attr_destroy(&attr);
    if (threadCreated != 0) {
        delete launch;
        return LOAD_FAILED;
    }
    return started.get();
}
