cmake_minimum_required(VERSION 3.22.1)

project("morphe")

# Starts the JVM that patches inside the app's process
add_library(jvm_launcher SHARED
        jvm_launcher.cpp)

target_link_libraries(jvm_launcher
        log)
