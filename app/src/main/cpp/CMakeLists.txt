# CMakeLists.txt for WebRTC VAD JNI wrapper

cmake_minimum_required(VERSION 3.22.1)

project("webrtc_vad_wrapper")

# Create the shared library for WebRTC VAD wrapper
add_library(
    webrtc_audio_preprocessing
    SHARED
    webrtc_vad_wrapper.cpp
)

# Find and link required libraries
find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

# Link libraries
target_link_libraries(
    webrtc_audio_preprocessing
    ${log-lib}
    ${android-lib}
)

# Set C++ standard
set_property(TARGET webrtc_audio_preprocessing PROPERTY CXX_STANDARD 17)

# Add compiler flags for optimization and debugging
target_compile_options(
    webrtc_audio_preprocessing
    PRIVATE
    -Wall
    -Wextra
    -O2
    -ffast-math
)

# Add include directories if needed
# target_include_directories(webrtc_audio_preprocessing PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)