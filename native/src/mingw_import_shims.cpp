#if !defined(__MINGW64__)
#error "The MinGW import shims are only supported on 64-bit MinGW"
#endif

// Current MinGW libstdc++ imports these wide-character helpers, while
// Kotlin/Native provides their static implementations through libmingwex.a.
// Supply the IAT slots so LLD can connect the imported references to those
// static implementations without requiring an extra runtime DLL.
asm(
    ".section .rdata,\"dr\"\n"
    ".balign 8\n"
    ".globl __imp_btowc\n"
    "__imp_btowc:\n"
    ".quad btowc\n"
    ".globl __imp_wctob\n"
    "__imp_wctob:\n"
    ".quad wctob\n"
    ".globl __imp_wctype\n"
    "__imp_wctype:\n"
    ".quad wctype\n"
    ".text\n"
);
