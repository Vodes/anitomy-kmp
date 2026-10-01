#!/usr/bin/env bash

set -euo pipefail

test_binary="${1:?usage: check-anitomy-data.sh <test-binary> <test-data-directory>}"
data_dir="${2:?usage: check-anitomy-data.sh <test-binary> <test-data-directory>}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
expected="${script_dir}/anitomy-data-expected-failures.txt"
actual="$(mktemp)"
trap 'rm -f "${actual}"' EXIT

# The pinned fork reports known corpus mismatches with exit status 1.
status=0
(cd "${data_dir}" && "${test_binary}" --test-data) > "${actual}" || status=$?
if [[ "${status}" != 1 ]]; then
    cat "${actual}"
    echo "Expected Anitomy corpus exit status 1; got ${status}" >&2
    exit 1
fi

diff -u --strip-trailing-cr "${expected}" "${actual}"
echo "Anitomy corpus matches the pinned expected failures."
