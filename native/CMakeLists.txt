cmake_minimum_required(VERSION 3.21)

project(
    anitomy-kmp-native
    VERSION 0.1.0
    LANGUAGES CXX
)

option(ANITOMY_KMP_BUILD_JNI "Build the JNI shared library" ON)
option(ANITOMY_KMP_BUILD_TESTS "Build native bridge tests" ON)
option(
    ANITOMY_KMP_BUNDLE_CPP_RUNTIME
    "Copy static GNU C++ runtime archives beside the bridge for Kotlin/Native"
    OFF
)

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
    ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
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
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
        OUTPUT_NAME anitomy-kmp
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
    )
    if(WIN32)
        set_target_properties(anitomy-kmp-jni PROPERTIES PREFIX "")
    endif()
endif()

if(ANITOMY_KMP_BUNDLE_CPP_RUNTIME)
    if(APPLE OR MSVC)
        message(FATAL_ERROR "Static GNU C++ runtime bundling is only for GNU-compatible targets")
    endif()

    set(ANITOMY_KMP_RUNTIME_LIBRARIES libstdc++.a libgcc.a libgcc_eh.a)
    if(MINGW)
        list(APPEND ANITOMY_KMP_RUNTIME_LIBRARIES libwinpthread.a)
    endif()

    foreach(RUNTIME_LIBRARY IN LISTS ANITOMY_KMP_RUNTIME_LIBRARIES)
        execute_process(
            COMMAND ${CMAKE_CXX_COMPILER} -print-file-name=${RUNTIME_LIBRARY}
            OUTPUT_VARIABLE RUNTIME_LIBRARY_PATH
            OUTPUT_STRIP_TRAILING_WHITESPACE
            COMMAND_ERROR_IS_FATAL ANY
        )
        if(NOT IS_ABSOLUTE "${RUNTIME_LIBRARY_PATH}" OR NOT EXISTS "${RUNTIME_LIBRARY_PATH}")
            message(
                FATAL_ERROR
                "${CMAKE_CXX_COMPILER} could not locate the static ${RUNTIME_LIBRARY}"
            )
        endif()
        configure_file(
            "${RUNTIME_LIBRARY_PATH}"
            "${CMAKE_BINARY_DIR}/${RUNTIME_LIBRARY}"
            COPYONLY
        )
    endforeach()
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
