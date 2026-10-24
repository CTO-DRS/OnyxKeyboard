cmake_minimum_required(VERSION 3.22.1)
project("onyx_native" CXX)

add_library(onyx_native SHARED
        onyx_native.cpp)

target_link_libraries(onyx_native
        android
        log)
