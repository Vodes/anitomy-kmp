#!/usr/bin/env bash

set -euo pipefail

target="${1:?usage: scripts/verify-native-artifacts.sh <target> <artifact-directory> [--check-cinterop]}"
artifact_dir="${2:?usage: scripts/verify-native-artifacts.sh <target> <artifact-directory> [--check-cinterop]}"
check_cinterop="${3:-}"
bridge="${artifact_dir}/libanitomy-bridge.a"
jni_symbol="Java_pw_vodes_anitomy_internal_JniBindings_parse"

fail() {
    echo "artifact verification failed: $*" >&2
    exit 1
}

verify_macos_linkage() {
    local binary="$1"
    local forbidden_dependencies='libstdc\+\+|libgcc|/opt/homebrew|/usr/local/(Cellar|opt)'
    local forbidden_paths='/opt/homebrew|/usr/local/(Cellar|opt)'

    if [[ "$(otool -L "${binary}")" =~ ${forbidden_dependencies} ]]; then
        fail "${binary} dynamically depends on the Homebrew GCC runtime"
    fi
    if [[ "$(otool -l "${binary}")" =~ ${forbidden_paths} ]]; then
        fail "${binary} contains a Homebrew load or runtime search path"
    fi
}

[[ -f "${bridge}" ]] || fail "missing ${bridge}"
if [[ "${target}" != "windowsX64" ]]; then
    for runtime in libstdc++.a libgcc.a libgcc_eh.a; do
        [[ ! -e "${artifact_dir}/${runtime}" ]] ||
            fail "${runtime} must be folded into libanitomy-bridge.a"
    done
fi

if [[ "${target}" == "linuxX64" || "${target}" == "linuxArm64" ]]; then
    bridge_members="$(ar t "${bridge}")"
    [[ "${bridge_members}" == "anitomy_bridge_portable.o" ]] ||
        fail "portable bridge must contain only anitomy_bridge_portable.o"
    [[ "$(objdump -h "${bridge}")" == *".debug_"* ]] &&
        fail "${bridge} contains debug sections"
elif [[ "${target}" == "windowsX64" ]]; then
    bridge_members="$(ar t "${bridge}")"
    [[ "${bridge_members}" == "anitomy_bridge.cpp.obj" ]] ||
        fail "Windows JNI bridge must contain only anitomy_bridge.cpp.obj"
    [[ "$(objdump -h "${bridge}")" == *".debug_"* ]] &&
        fail "${bridge} contains debug sections"
fi

case "${target}" in
    linuxX64|linuxArm64)
        jni="${artifact_dir}/libanitomy-kmp.so"
        [[ -f "${jni}" ]] || fail "missing ${jni}"
        file "${jni}" | grep -q "stripped" || fail "${jni} is not stripped"
        [[ "$(readelf --sections "${jni}")" == *".debug_"* ]] &&
            fail "${jni} contains debug sections"
        exports="$(nm -D --defined-only "${jni}" | awk '{print $3}')"
        ;;
    windowsX64)
        jni="${artifact_dir}/anitomy-kmp.dll"
        [[ -f "${jni}" ]] || fail "missing ${jni}"
        [[ "$(objdump -h "${jni}")" == *".debug_"* ]] &&
            fail "${jni} contains debug sections"
        imports="$(objdump -p "${jni}")"
        [[ ! "${imports}" =~ libstdc\+\+-6\.dll|libgcc_s_.*-1\.dll|libwinpthread-1\.dll ]] ||
            fail "${jni} dynamically depends on the MinGW runtime"
        exports="$(
            printf '%s\n' "${imports}" |
                sed -n '/Ordinal\/Name Pointer.*Table/,/^$/p' |
                awk '/\[[[:space:]]*[0-9]+\]/{print $NF}'
        )"
        ;;
    macosArm64)
        jni="${artifact_dir}/libanitomy-kmp.dylib"
        [[ -f "${jni}" ]] || fail "missing ${jni}"
        [[ "$(otool -l "${jni}")" == *"__debug_"* ]] &&
            fail "${jni} contains debug sections"
        verify_macos_linkage "${bridge}"
        verify_macos_linkage "${jni}"
        exports="$(nm -gU "${jni}" | awk '{print $NF}' | sed 's/^_//')"
        ;;
    *)
        fail "unsupported target ${target}"
        ;;
esac

printf '%s\n' "${exports}" | grep -qx "${jni_symbol}" ||
    fail "${jni} does not export ${jni_symbol}"
while IFS= read -r symbol; do
    case "${symbol}" in
        "${jni_symbol}"|_init|_fini|"")
            ;;
        *)
            fail "${jni} unexpectedly exports ${symbol}"
            ;;
    esac
done < <(printf '%s\n' "${exports}")

if [[ "${check_cinterop}" == "--check-cinterop" ]]; then
    cinterop_root="build/classes/kotlin/${target}/main/cinterop"
    included_dir="$(find "${cinterop_root}" -type d -name included -print -quit)"
    [[ -n "${included_dir}" ]] || fail "no cinterop included directory below ${cinterop_root}"
    included_archives="$(
        find "${included_dir}" -maxdepth 1 -type f -name '*.a' -exec basename {} \; | sort
    )"
    [[ "${included_archives}" == "libanitomy-bridge.a" ]] ||
        fail "cinterop must include only libanitomy-bridge.a"

    if [[ "${target}" == "macosArm64" ]]; then
        native_test="$(
            find "build/bin/${target}" -type f -name 'test.kexe' -print -quit
        )"
        [[ -n "${native_test}" ]] ||
            fail "no Kotlin/Native test executable below build/bin/${target}"
        verify_macos_linkage "${native_test}"
    fi
fi

wc -c "${bridge}" "${jni}"
