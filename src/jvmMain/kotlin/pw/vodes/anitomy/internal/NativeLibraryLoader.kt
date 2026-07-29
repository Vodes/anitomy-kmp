package pw.vodes.anitomy.internal

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

internal object NativeLibraryLoader {
    fun load() {
        val platform = detectPlatform()
        val resource = "/META-INF/anitomy-kmp/${platform.directory}/${platform.libraryName}"
        val libraryBytes =
            NativeLibraryLoader::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: error(
                    "The Anitomy native library for ${platform.directory} is missing from the " +
                        "JVM artifact ($resource)",
                )
        val digest =
            HexFormat.of().formatHex(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(libraryBytes),
            )
        val directory =
            Path
                .of(System.getProperty("java.io.tmpdir"))
                .resolve("anitomy-kmp")
                .resolve(digest)
        val library = directory.resolve(platform.libraryName)

        Files.createDirectories(directory)
        if (Files.notExists(library)) {
            val temporary = Files.createTempFile(directory, "${platform.libraryName}.", ".tmp")
            try {
                Files.write(temporary, libraryBytes)
                try {
                    Files.move(
                        temporary,
                        library,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, library, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

        System.load(library.toAbsolutePath().toString())
    }

    private fun detectPlatform(): NativePlatform {
        val osName = System.getProperty("os.name").lowercase()
        val architecture = System.getProperty("os.arch").lowercase()
        val arch =
            when (architecture) {
                "x86_64", "amd64" -> "x64"
                "aarch64", "arm64" -> "arm64"
                else -> throw UnsupportedOperationException(
                    "Anitomy does not provide a JVM native library for architecture '$architecture'",
                )
            }

        return when {
            osName.contains("linux") && arch in setOf("x64", "arm64") ->
                NativePlatform("linux-$arch", "libanitomy-kmp.so")
            osName.contains("windows") && arch == "x64" ->
                NativePlatform("windows-x64", "anitomy-kmp.dll")
            (osName.contains("mac") || osName.contains("darwin")) && arch == "arm64" ->
                NativePlatform("macos-arm64", "libanitomy-kmp.dylib")
            else ->
                throw UnsupportedOperationException(
                    "Anitomy does not support JVM platform '$osName/$architecture'",
                )
        }
    }
}

private class NativePlatform(
    val directory: String,
    val libraryName: String,
)
