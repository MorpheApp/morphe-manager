cmake_minimum_required(VERSION 3.22.1)

project("morphe")

# Starts the JVM that patches as a process of the patcher service
add_library(jvm_launcher SHARED
        jvm_launcher.cpp)

# What the JVM that runs on the GNU C library of 32-bit ARM needs that its build of glibc gets
# wrong on Android, see glibc_compat.c. Built for glibc's hard-float ABI, which its loader
# requires of every library it loads, and against nothing but glibc, which it is loaded beside
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    add_library(glibc_compat SHARED
            glibc_compat.c)
    # ARM rather than Thumb code, where r7, which takes the number of a system call, is the frame pointer
    set(GLIBC_TARGET --target=armv7a-linux-gnueabihf -mfloat-abi=hard -marm)
    # No unwind tables, whose personality routines would be bionic's
    target_compile_options(glibc_compat PRIVATE ${GLIBC_TARGET} -ffreestanding -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables)
    # errno is glibc's, found once the library is loaded beside it, which the NDK otherwise forbids
    target_link_options(glibc_compat PRIVATE ${GLIBC_TARGET} -nostdlib -Wl,-z,undefs)
    # Nothing of bionic, which the NDK links every C library against
    set(CMAKE_C_STANDARD_LIBRARIES "")
endif ()
