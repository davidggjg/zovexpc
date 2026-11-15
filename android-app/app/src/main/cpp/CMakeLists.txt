cmake_minimum_required(VERSION 3.22.1)
project(zovex_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(zovex_native SHARED
    jni_bridge.cpp
    vulkan_probe.cpp
    exec_probe.cpp
    log.cpp
)

target_include_directories(zovex_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Vulkan is loaded dynamically (dlopen) rather than linked, so the app can
# start and report a clear error on devices with no usable Vulkan driver
# instead of failing to load the library at all.
target_link_libraries(zovex_native PRIVATE android log dl)
