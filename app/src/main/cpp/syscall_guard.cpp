// Turns the system calls the seccomp policy for apps forbids into ordinary failures.
//
// The patcher process starts from app_process rather than a zygote fork, so it runs the framework
// class initializers itself and firmware loading a vendor library from one of them gets that
// library running under the policy, where the kernel answers a forbidden call by killing the
// process. EPERM is what an unprivileged process would be told anyway.
#include <cerrno>
#include <unistd.h>

extern "C" int setresgid(gid_t, gid_t, gid_t) {
    errno = EPERM;
    return -1;
}

extern "C" int setresuid(uid_t, uid_t, uid_t) {
    errno = EPERM;
    return -1;
}
