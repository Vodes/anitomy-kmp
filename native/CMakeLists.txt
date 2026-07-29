cmake_minimum_required(VERSION 3.21)

project(
    anitomy-kmp-native
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
    "Fold the required static GNU C++ runtime objects into the Kotlin/Native bridge"
    OFF
)
option(ANITOMY_KMP_STRIP_JNI "Strip debug and unneeded symbols from the JNI library" OFF)

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
if(ANITOMY_KMP_BUNDLE_CPP_RUNTIME)
    set_target_properties(anitomy-bridge PROPERTIES
        OUTPUT_NAME anitomy-bridge-core
    )
endif()

if(MSVC)
    target_compile_options(anitomy-bridge PRIVATE /permissive- /utf-8 /W4 /Zc:__cplusplus)
else()
    target_compile_options(
        anitomy-bridge
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
            -ffunction-sections
            -fdata-sections
    )
    set_target_properties(anitomy-bridge PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
    )
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
        CXX_VISIBILITY_PRESET hidden
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
        OUTPUT_NAME anitomy-kmp
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
        VISIBILITY_INLINES_HIDDEN YES
    )
    if(NOT MSVC)
        target_compile_options(anitomy-kmp-jni PRIVATE -ffunction-sections -fdata-sections)
    endif()
    if(WIN32)
        set_target_properties(anitomy-kmp-jni PROPERTIES PREFIX "")
    endif()
    if(ANITOMY_KMP_STATIC_JNI_RUNTIME AND CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        target_link_options(anitomy-kmp-jni PRIVATE -static-libgcc -static-libstdc++)
        if(MINGW)
            target_link_options(anitomy-kmp-jni PRIVATE -static)
        elseif(APPLE)
            target_link_options(anitomy-kmp-jni PRIVATE -nodefaultexport -nodefaultrpaths)
        endif()
    endif()
    if(APPLE)
        target_link_options(
            anitomy-kmp-jni
            PRIVATE
                "LINKER:-dead_strip"
                "LINKER:-dead_strip_dylibs"
                "LINKER:-exported_symbol,_Java_pw_vodes_anitomy_internal_JniBindings_parse"
        )
    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        target_link_options(
            anitomy-kmp-jni
            PRIVATE
                "LINKER:--exclude-libs,ALL"
                "LINKER:--gc-sections"
        )
    elseif(CMAKE_CXX_COMPILER_ID MATCHES "AppleClang|Clang")
        target_link_options(anitomy-kmp-jni PRIVATE "LINKER:-dead_strip")
    endif()
    if(ANITOMY_KMP_STRIP_JNI)
        if(APPLE)
            add_custom_command(
                TARGET anitomy-kmp-jni
                POST_BUILD
                COMMAND ${CMAKE_STRIP} -S -x $<TARGET_FILE:anitomy-kmp-jni>
                VERBATIM
            )
        elseif(NOT MSVC)
            add_custom_command(
                TARGET anitomy-kmp-jni
                POST_BUILD
                COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:anitomy-kmp-jni>
                VERBATIM
            )
        endif()
    endif()
endif()

if(ANITOMY_KMP_BUNDLE_CPP_RUNTIME)
    if(NOT CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        message(FATAL_ERROR "Kotlin/Native runtime bundling requires the GNU C++ toolchain")
    endif()
    if(MINGW)
        message(FATAL_ERROR "The Kotlin/Native mingwX64 bridge is not supported")
    endif()

    set(ANITOMY_KMP_RUNTIME_LIBRARY_NAMES libstdc++.a libgcc.a libgcc_eh.a)
    set(ANITOMY_KMP_RUNTIME_LIBRARIES)

    foreach(RUNTIME_LIBRARY IN LISTS ANITOMY_KMP_RUNTIME_LIBRARY_NAMES)
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
        list(APPEND ANITOMY_KMP_RUNTIME_LIBRARIES "${RUNTIME_LIBRARY_PATH}")
    endforeach()

    set(ANITOMY_KMP_PORTABLE_ARCHIVE "${CMAKE_BINARY_DIR}/libanitomy-bridge.a")
    if(APPLE)
        find_program(ANITOMY_KMP_APPLE_LIBTOOL NAMES libtool REQUIRED)
        add_custom_command(
            OUTPUT "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
            COMMAND ${CMAKE_COMMAND} -E rm -f "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
            COMMAND
                ${ANITOMY_KMP_APPLE_LIBTOOL}
                -static
                -o
                "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
                $<TARGET_FILE:anitomy-bridge>
                ${ANITOMY_KMP_RUNTIME_LIBRARIES}
            DEPENDS
                anitomy-bridge
                ${ANITOMY_KMP_RUNTIME_LIBRARIES}
            COMMAND_EXPAND_LISTS
            VERBATIM
        )
    else()
        set(ANITOMY_KMP_PORTABLE_OBJECT "${CMAKE_BINARY_DIR}/anitomy_bridge_portable.o")
        add_custom_command(
            OUTPUT "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
            COMMAND
                ${CMAKE_CXX_COMPILER}
                -r
                -nostdlib
                -o
                "${ANITOMY_KMP_PORTABLE_OBJECT}"
                "-Wl,--whole-archive"
                $<TARGET_FILE:anitomy-bridge>
                "-Wl,--no-whole-archive"
                "-Wl,--start-group"
                ${ANITOMY_KMP_RUNTIME_LIBRARIES}
                "-Wl,--end-group"
            COMMAND ${CMAKE_STRIP} --strip-debug "${ANITOMY_KMP_PORTABLE_OBJECT}"
            COMMAND ${CMAKE_COMMAND} -E rm -f "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
            COMMAND ${CMAKE_AR} qc "${ANITOMY_KMP_PORTABLE_ARCHIVE}" "${ANITOMY_KMP_PORTABLE_OBJECT}"
            COMMAND ${CMAKE_RANLIB} "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
            DEPENDS
                anitomy-bridge
                ${ANITOMY_KMP_RUNTIME_LIBRARIES}
            COMMAND_EXPAND_LISTS
            VERBATIM
        )
    endif()
    add_custom_target(anitomy-portable-bridge ALL
        DEPENDS "${ANITOMY_KMP_PORTABLE_ARCHIVE}"
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
