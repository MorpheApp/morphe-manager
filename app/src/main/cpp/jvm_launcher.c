// Starts the JVM that patches as a process of the patcher service and waits for it to end.
//
// The JVM is built for the GNU C library, which Android's own cannot load into the service, so the
// loader of that library runs it as a program of its own. The service hands it the pipes of the run
// and outlives it, as nothing but the end of its process stops a JVM.
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>

// The exit code of a child that could not run the program, as a shell reports it
#define CHILD_FAILED 127
#define SIGNAL_EXIT_BASE 128
// How many file descriptors a started process closes at most, the app keeps far fewer open
#define MAX_CLOSED_FD 65536

// A copy of the strings of array, ending with NULL, or NULL if memory ran out
static char **copyStrings(JNIEnv *env, jobjectArray array) {
    const jsize length = (*env)->GetArrayLength(env, array);
    char **strings = calloc((size_t) length + 1, sizeof(char *));
    if (strings == NULL) return NULL;
    for (jsize i = 0; i < length; i++) {
        jstring value = (*env)->GetObjectArrayElement(env, array, i);
        const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
        strings[i] = chars == NULL ? NULL : strdup(chars);
        if (chars != NULL) (*env)->ReleaseStringUTFChars(env, value, chars);
        (*env)->DeleteLocalRef(env, value);
        if (strings[i] == NULL) {
            for (jsize j = 0; j < i; j++) free(strings[j]);
            free(strings);
            return NULL;
        }
    }
    return strings;
}

static void freeStrings(char **strings) {
    for (char **string = strings; *string != NULL; string++) free(*string);
    free(strings);
}

static bool contains(const jint *values, jsize count, int value) {
    for (jsize i = 0; i < count; i++) {
        if (values[i] == value) return true;
    }
    return false;
}

// Starts argv as a process of its own with the environment envp. The process keeps keepFds open
// under the same numbers, writes its standard output and error to outputFd, and is killed once the
// thread that called this ends, so it does not outlive the service. Returns its pid, or a negative errno
JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_spawnProcess(
        JNIEnv *env, jobject thiz, jobjectArray argv, jobjectArray envp, jintArray keepFds, jint outputFd) {
    (void) thiz;
    // Everything the child needs is made here, as it may only make async-signal-safe calls until exec
    char **args = copyStrings(env, argv);
    if (args == NULL) return -ENOMEM;
    char **environment = copyStrings(env, envp);
    if (environment == NULL) {
        freeStrings(args);
        return -ENOMEM;
    }
    const jsize keepCount = (*env)->GetArrayLength(env, keepFds);
    jint *keep = malloc(sizeof(jint) * ((size_t) keepCount + 1));
    if (keep == NULL) {
        freeStrings(args);
        freeStrings(environment);
        return -ENOMEM;
    }
    (*env)->GetIntArrayRegion(env, keepFds, 0, keepCount, keep);

    struct rlimit files;
    const int maxFd = getrlimit(RLIMIT_NOFILE, &files) == 0 && files.rlim_cur != RLIM_INFINITY &&
                      files.rlim_cur < MAX_CLOSED_FD ? (int) files.rlim_cur : MAX_CLOSED_FD;
    const pid_t parent = getpid();

    const pid_t pid = fork();
    if (pid != 0) {
        const int forkError = errno;
        freeStrings(args);
        freeStrings(environment);
        free(keep);
        return pid > 0 ? pid : -forkError;
    }

    // The child. A parent that ended before this point would leave it to init, so it ends at once
    prctl(PR_SET_PDEATHSIG, SIGKILL);
    if (getppid() != parent) _exit(CHILD_FAILED);

    // ART blocks and ignores signals the JVM relies on, which a new program inherits
    sigset_t none;
    sigemptyset(&none);
    sigprocmask(SIG_SETMASK, &none, NULL);
    for (int sig = 1; sig < NSIG; sig++) signal(sig, SIG_DFL);

    const int devNull = open("/dev/null", O_RDONLY);
    if (devNull < 0 || dup2(devNull, STDIN_FILENO) < 0 || dup2(outputFd, STDOUT_FILENO) < 0 ||
        dup2(outputFd, STDERR_FILENO) < 0) {
        _exit(CHILD_FAILED);
    }
    // Nothing of the app but what the JVM is handed reaches it
    for (int fd = STDERR_FILENO + 1; fd < maxFd; fd++) {
        if (!contains(keep, keepCount, fd)) close(fd);
    }
    for (jsize i = 0; i < keepCount; i++) fcntl(keep[i], F_SETFD, 0);

    execve(args[0], args, environment);
    _exit(CHILD_FAILED);
}

// Waits for the process pid started, returning its exit code, or 128 plus the signal that ended it
JNIEXPORT jint JNICALL
Java_app_morphe_manager_patcher_runtime_jvm_EmbeddedJvm_waitProcess(JNIEnv *env, jobject thiz, jint pid) {
    (void) env;
    (void) thiz;
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -errno;
    }
    if (WIFSIGNALED(status)) return SIGNAL_EXIT_BASE + WTERMSIG(status);
    return WEXITSTATUS(status);
}
