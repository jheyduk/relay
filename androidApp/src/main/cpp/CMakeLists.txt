cmake_minimum_required(VERSION 3.22.1)
project(whisper-jni)

set(CMAKE_CXX_STANDARD 17)

# Path to whisper.cpp source tree (cloned or downloaded separately)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Whisper sources
set(SOURCE_FILES
    ${WHISPER_DIR}/src/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper-jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

# Build ggml from whisper.cpp's bundled copy (official approach from whisper.android example)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

add_library(whisper-jni SHARED ${SOURCE_FILES})

target_compile_definitions(whisper-jni PUBLIC
    GGML_USE_CPU
    WHISPER_VERSION="1.8.3"
)

# ARM NEON / fp16 optimization for ARM64
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper-jni PRIVATE -march=armv8.2-a+fp16)
    target_compile_options(ggml PRIVATE -march=armv8.2-a+fp16)
endif()

# Release optimizations
if(NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(whisper-jni PRIVATE -O3)
    target_compile_options(whisper-jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper-jni PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(whisper-jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper-jni PRIVATE -Wl,--exclude-libs,ALL)
    target_link_options(whisper-jni PRIVATE -flto)
endif()

target_include_directories(whisper-jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
)

target_link_libraries(whisper-jni ${LOG_LIB} android ggml)
