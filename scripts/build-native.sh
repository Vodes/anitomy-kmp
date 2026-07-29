#!/usr/bin/env bash

set -euo pipefail

target="${1:?usage: scripts/build-native.sh <linuxX64|linuxArm64|mingwX64|macosArm64>}"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="${ANITOMY_NATIVE_BUILD_DIR:-${project_dir}/build/native/${target}}"

case "${target}" in
    linuxX64|linuxArm64|mingwX64|macosArm64)
        compiler="${CXX:-g++}"
        bundle_runtime=ON
        ;;
    *)
        echo "unsupported target: ${target}" >&2
        exit 2
        ;;
esac

cmake_args=(
    -S "${project_dir}/native"
    -B "${build_dir}"
    -G Ninja
    -DCMAKE_BUILD_TYPE=Release
    -DCMAKE_CXX_COMPILER="${compiler}"
    -DANITOMY_KMP_BUILD_JNI="${ANITOMY_BUILD_JNI:-ON}"
    -DANITOMY_KMP_BUILD_TESTS="${ANITOMY_BUILD_TESTS:-OFF}"
    -DANITOMY_KMP_BUNDLE_CPP_RUNTIME="${bundle_runtime}"
    -DANITOMY_KMP_STATIC_JNI_RUNTIME="${ANITOMY_STATIC_JNI_RUNTIME:-${bundle_runtime}}"
    -DANITOMY_KMP_STRIP_JNI="${ANITOMY_STRIP_JNI:-ON}"
)

if [[ -n "${ANITOMY_CMAKE_SYSTEM_NAME:-}" ]]; then
    cmake_args+=("-DCMAKE_SYSTEM_NAME=${ANITOMY_CMAKE_SYSTEM_NAME}")
fi
if [[ -n "${ANITOMY_CMAKE_SYSTEM_PROCESSOR:-}" ]]; then
    cmake_args+=("-DCMAKE_SYSTEM_PROCESSOR=${ANITOMY_CMAKE_SYSTEM_PROCESSOR}")
fi

cmake "${cmake_args[@]}"
cmake --build "${build_dir}" --parallel

echo "Native artifacts: ${build_dir}"
echo "Gradle property: -Panitomy.nativeLibraryDir.${target}=${build_dir}"
