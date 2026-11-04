// Starts a HotSpot JVM inside the calling process and runs a main class in it.
//
// The JVM keeps its heap in native memory, so it is bound by what the device can spare rather than
// the heap limit ART puts on a process. HotSpot cannot be started twice in one process, so the
// JVM stays for as long as the process does.
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <malloc.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <future>
#include <sstream>
#include <string>
#include <vector>

#define TAG "EmbeddedJvm"

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

// The stack trace of throwable as printStackTrace writes it, empty if writing it threw as well
std::string stackTrace(JNIEnv *env, jthrowable throwable) {
    // Any of the calls below may throw, which has to be cleared before JNI is called again
    const auto threw = [env] {
        if (!env->ExceptionCheck()) return false;
        env->ExceptionClear();
        return true;
    };

    jclass stringWriterClass = env->FindClass("java/io/StringWriter");
    if (threw()) return {};
    jclass printWriterClass = env->FindClass("java/io/PrintWriter");
    if (threw()) return {};
    jmethodID newStringWriter = env->GetMethodID(stringWriterClass, "<init>", "()V");
    if (threw()) return {};
    jmethodID newPrintWriter = env->GetMethodID(printWriterClass, "<init>", "(Ljava/io/Writer;)V");
    if (threw()) return {};
    jmethodID printStackTrace = env->GetMethodID(env->GetObjectClass(throwable), "printStackTrace", "(Ljava/io/PrintWriter;)V");
    if (threw()) return {};
    jmethodID toString = env->GetMethodID(stringWriterClass, "toString", "()Ljava/lang/String;");
    if (threw()) return {};

    jobject stringWriter = env->NewObject(stringWriterClass, newStringWriter);
    if (threw()) return {};
    jobject printWriter = env->NewObject(printWriterClass, newPrintWriter, stringWriter);
    if (threw()) return {};
    env->CallVoidMethod(throwable, printStackTrace, printWriter);
    if (threw()) return {};
    // toString of a StringWriter returns a String, which JNI hands out as jobject
    auto text = static_cast<jstring>(env->CallObjectMethod(stringWriter, toString)); // NOLINT(cppcoreguidelines-pro-type-static-cast-downcast)
    if (threw()) return {};

    const char *chars = env->GetStringUTFChars(text, nullptr);
    std::string trace = chars;
    env->ReleaseStringUTFChars(text, chars);
    return trace;
}

// HotSpot only writes which thread an exception ended through logJvmOutput, and the trace itself to
// System.err, which leads nowhere in an app. The trace is logged here instead, a line at a time, as
// the log cuts a message off at a few kilobytes
void describeException(JNIEnv *env) {
    jthrowable throwable = env->ExceptionOccurred();
    if (throwable == nullptr) return;
    env->ExceptionClear();

    const std::string trace = stackTrace(env, throwable);
    if (trace.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "The JVM threw an exception it could not describe");
        return;
    }
    std::istringstream lines(trace);
    for (std::string line; std::getline(lines, line);) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", line.c_str());
    }
}

// Why the JVM did not start, next to the JNI_CreateJavaVM codes, which are all negative
constexpr int LOAD_FAILED = 1;
constexpr int MAIN_MISSING = 2;
constexpr int TAG_CHECKS_ON = 3;

constexpr size_t JVM_THREAD_STACK_SIZE = 8UL * 1024 * 1024;

constexpr size_t MEBIBYTE = 1024UL * 1024;
constexpr jint RESERVATION_STEP_MB = 64;

// HotSpot keeps flags in the top bits of native pointers and drops the memory tag with them, so a
// device that checks tags, which the user can turn on for any app, faults on its first access. The
// checks are turned off for the whole process, which runs nothing but the patcher. Returns false
// where they are still on
bool disableTagChecks() {
#if defined(__aarch64__)
    const int control = prctl(PR_GET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
    // Fails on a kernel or CPU without tagged addresses, where there is nothing to turn off
    if (control < 0 || (control & PR_MTE_TCF_MASK) == 0) return true;
    if (mallopt(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE) == 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Memory tag checks could not be turned off");
        return false;
    }
    __android_log_print(ANDROID_LOG_WARN, TAG, "Memory tag checks turned off for the JVM");
#endif
    return true;
}

// Creates the JVM and finds the main method, returning 0 or why it could not
int createJvm(Launch *launch, JNIEnv **env, jclass *mainClass, jmethodID *main) {
    // A release build starts the process as not dumpable, which hands /proc/self over to root, and
    // the I/O accounting of the run in /proc/self/io is then out of reach of the process itself
    prctl(PR_SET_DUMPABLE, 1);

    // Before the runtime is loaded, as its libraries allocate as they load
    if (!disableTagChecks()) return TAG_CHECKS_ON;

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
        // Ends the process once the run is over, so returning means the host broke down
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

// The largest block of address space up to limitMb that a single reservation gets, in steps of
// RESERVATION_STEP_MB, or 0. The block is handed back at once, as the JVM reserves it itself
extern "C" JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_largestReservationMb(JNIEnv *, jobject, jint limitMb) {
    for (jint mb = limitMb; mb > 0; mb -= RESERVATION_STEP_MB) {
        const size_t size = static_cast<size_t>(mb) * MEBIBYTE;
        void *block = mmap(nullptr, size, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        if (block != MAP_FAILED) {
            munmap(block, size);
            return mb;
        }
    }
    return 0;
}
