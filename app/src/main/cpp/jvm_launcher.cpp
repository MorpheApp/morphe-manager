// Starts the JVM that patches as a process of the patcher service and waits for it to end.
//
// The JVM is built for the GNU C library, which Android's own cannot load into the service, so the
// loader of that library runs it as a program of its own. The service hands it the pipes of the run
// and outlives it, as nothing but the end of its process stops a JVM.
#include <fcntl.h>
#include <jni.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <csignal>
#include <string>
#include <vector>

namespace {

// The exit code of a child that could not run the program, as a shell reports it
constexpr int CHILD_FAILED = 127;
constexpr int SIGNAL_EXIT_BASE = 128;
// How many file descriptors a started process closes at most, the app keeps far fewer open
constexpr int MAX_CLOSED_FD = 65536;

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

} // namespace

// Starts argv as a process of its own. The process keeps keepFds open under the same numbers,
// writes its standard output and error to outputFd, and is killed once the thread that called this
// ends, so it does not outlive the service. Returns its pid, or a negative errno
extern "C" JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_spawnProcess(
        JNIEnv *env, jobject, jobjectArray argv, jintArray keepFds, jint outputFd) {
    // Everything the child needs is made here, as it may only make async-signal-safe calls until exec
    const std::vector<std::string> args = toStrings(env, argv);
    std::vector<char *> argPointers;
    argPointers.reserve(args.size() + 1);
    for (auto &arg: args) argPointers.push_back(const_cast<char *>(arg.c_str()));
    argPointers.push_back(nullptr);

    const jsize keepCount = env->GetArrayLength(keepFds);
    std::vector<jint> keep(static_cast<size_t>(keepCount));
    env->GetIntArrayRegion(keepFds, 0, keepCount, keep.data());

    rlimit files{};
    const int maxFd = getrlimit(RLIMIT_NOFILE, &files) == 0 && files.rlim_cur != RLIM_INFINITY
                      ? static_cast<int>(std::min<rlim_t>(files.rlim_cur, MAX_CLOSED_FD)) : MAX_CLOSED_FD;
    const pid_t parent = getpid();

    const pid_t pid = fork();
    if (pid < 0) return -errno;
    if (pid > 0) return pid;

    // The child. A parent that ended before this point would leave it to init, so it ends at once
    prctl(PR_SET_PDEATHSIG, SIGKILL);
    if (getppid() != parent) _exit(CHILD_FAILED);

    // ART blocks and ignores signals the JVM relies on, which a new program inherits
    sigset_t none;
    sigemptyset(&none);
    sigprocmask(SIG_SETMASK, &none, nullptr);
    for (int sig = 1; sig < NSIG; sig++) signal(sig, SIG_DFL);

    const int devNull = open("/dev/null", O_RDONLY);
    if (devNull < 0 || dup2(devNull, STDIN_FILENO) < 0 || dup2(outputFd, STDOUT_FILENO) < 0 ||
        dup2(outputFd, STDERR_FILENO) < 0) {
        _exit(CHILD_FAILED);
    }
    // Nothing of the app but what the JVM is handed reaches it
    for (int fd = STDERR_FILENO + 1; fd < maxFd; fd++) {
        if (std::find(keep.begin(), keep.end(), fd) == keep.end()) close(fd);
    }
    for (const jint fd: keep) fcntl(fd, F_SETFD, 0);

    execv(argPointers[0], argPointers.data());
    _exit(CHILD_FAILED);
}

// Waits for the process pid started, returning its exit code, or 128 plus the signal that ended it
extern "C" JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_waitProcess(JNIEnv *, jobject, jint pid) {
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -errno;
    }
    if (WIFSIGNALED(status)) return SIGNAL_EXIT_BASE + WTERMSIG(status);
    return WEXITSTATUS(status);
}
