cmake_minimum_required(VERSION 3.21)

project(
    anitomy-kmp-native
    VERSION 0.1.0
    LANGUAGES CXX
)

option(ANITOMY_KMP_BUILD_JNI "Build the JNI shared library" ON)
option(ANITOMY_KMP_BUILD_TESTS "Build native bridge tests" ON)
option(
    ANITOMY_KMP_STATIC_JNI_RUNTIME
    "Link the GNU C++ runtime into the JNI library instead of requiring it at runtime"
    OFF
)
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

if(ANITOMY_KMP_BUNDLE_CPP_RUNTIME AND CMAKE_SYSTEM_NAME STREQUAL "Linux")
    target_compile_definitions(anitomy-bridge PRIVATE ANITOMY_KMP_OLD_GLIBC_COMPAT=1)
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
    if(ANITOMY_KMP_STATIC_JNI_RUNTIME AND CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        target_link_options(anitomy-kmp-jni PRIVATE -static-libgcc -static-libstdc++)
        if(MINGW)
            target_link_options(anitomy-kmp-jni PRIVATE -static)
        endif()
    endif()
endif()

if(ANITOMY_KMP_BUNDLE_CPP_RUNTIME)
    if(NOT CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        message(FATAL_ERROR "Kotlin/Native runtime bundling requires the GNU C++ toolchain")
    endif()

    set(ANITOMY_KMP_RUNTIME_LIBRARIES libstdc++.a libgcc.a libgcc_eh.a)

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
