// Loaded into the JVM that runs on the GNU C library of 32-bit ARM before that library, so the
// calls of the JVM resolve to these.
//
// Android lets an app truncate a file on 32-bit ARM with ftruncate64 alone, the call its own C
// library makes, and ends the process on ftruncate. The build of glibc the app ships makes the
// latter for both, so both go to ftruncate64 here. Built for glibc rather than Android, and it
// links against nothing but the errno of glibc, which it is loaded beside.

#define NR_FTRUNCATE64 194

extern int *__errno_location(void);

static long syscall4(long number, long a, long b, long c, long d) {
    register long r7 __asm__("r7") = number;
    register long r0 __asm__("r0") = a;
    register long r1 __asm__("r1") = b;
    register long r2 __asm__("r2") = c;
    register long r3 __asm__("r3") = d;
    __asm__ volatile("svc #0" : "+r"(r0) : "r"(r7), "r"(r1), "r"(r2), "r"(r3) : "memory");
    return r0;
}

__attribute__((visibility("default")))
int ftruncate64(int fd, long long length) {
    // The 64-bit length takes an even pair of registers, which leaves r1 unused
    const long result = syscall4(NR_FTRUNCATE64, fd, 0, (long) length, (long) (length >> 32));
    if (result < 0 && result > -4096) {
        *__errno_location() = (int) -result;
        return -1;
    }
    return 0;
}

__attribute__((visibility("default")))
int ftruncate(int fd, long length) {
    return ftruncate64(fd, length);
}
