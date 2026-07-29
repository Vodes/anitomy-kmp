#if !defined(__MINGW64__)
#error "The MinGW import shims are only supported on 64-bit MinGW"
#endif

// Current MinGW libstdc++ imports wctype, while Kotlin/Native's CRT libraries
// already provide the function. Supply the missing IAT slot so LLD can connect
// the imported reference without pulling libmingwex's conflicting
// implementation.
//
// Do not add btowc or wctob here: their libmingwex objects provide both the
// static implementations and their IAT slots.
asm(
    ".section .rdata,\"dr\"\n"
    ".balign 8\n"
    ".globl __imp_wctype\n"
    "__imp_wctype:\n"
    ".quad wctype\n"
    ".text\n"
);
