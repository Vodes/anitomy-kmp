cmake_minimum_required(VERSION 3.21)

project(
    anitomy-kmp-native
    VERSION 0.1.0
    LANGUAGES CXX
)

option(ANITOMY_KMP_BUILD_JNI "Build the JNI shared library" ON)
option(ANITOMY_KMP_BUILD_TESTS "Build native bridge tests" ON)

add_library(anitomy-bridge STATIC
    src/anitomy_bridge.cpp
)

target_compile_features(anitomy-bridge PUBLIC cxx_std_23)
target_include_directories(anitomy-bridge
    PUBLIC
        ${CMAKE_CURRENT_LIST_DIR}/include
    PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}/../vendor/anitomy/include
)
set_target_properties(anitomy-bridge PROPERTIES
    CXX_EXTENSIONS OFF
    POSITION_INDEPENDENT_CODE ON
)

if(MSVC)
    target_compile_options(anitomy-bridge PRIVATE /permissive- /utf-8 /W4 /Zc:__cplusplus)
else()
    target_compile_options(anitomy-bridge PRIVATE -Wall -Wextra -Wpedantic)
endif()

if(ANITOMY_KMP_BUILD_JNI)
    find_package(JNI REQUIRED)

    add_library(anitomy-kmp-jni SHARED
        src/anitomy_jni.cpp
    )
    target_compile_features(anitomy-kmp-jni PRIVATE cxx_std_23)
    target_include_directories(anitomy-kmp-jni PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(anitomy-kmp-jni PRIVATE anitomy-bridge)
    set_target_properties(anitomy-kmp-jni PROPERTIES
        CXX_EXTENSIONS OFF
        OUTPUT_NAME anitomy-kmp
    )
endif()

if(ANITOMY_KMP_BUILD_TESTS)
    enable_testing()

    add_executable(anitomy-bridge-tests
        test/bridge_test.cpp
    )
    target_compile_features(anitomy-bridge-tests PRIVATE cxx_std_23)
    target_link_libraries(anitomy-bridge-tests PRIVATE anitomy-bridge)

    add_test(NAME anitomy-bridge-tests COMMAND anitomy-bridge-tests)
endif()
