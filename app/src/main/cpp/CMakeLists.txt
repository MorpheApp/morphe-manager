cmake_minimum_required(VERSION 3.22.1)

project("morphe")

# Starts the JVM that patches as a process of the patcher service
add_library(jvm_launcher SHARED
        jvm_launcher.cpp)
