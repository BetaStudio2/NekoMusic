cmake_minimum_required(VERSION 3.16)
project(neko_audio_quality LANGUAGES CXX)

find_package(JNI REQUIRED)
add_library(neko_audio_quality SHARED neko_audio_quality.cpp)
target_compile_features(neko_audio_quality PRIVATE cxx_std_17)
target_include_directories(neko_audio_quality PRIVATE ${JNI_INCLUDE_DIRS})
set_target_properties(neko_audio_quality PROPERTIES PREFIX "lib")
