package pw.vodes.anitomy.internal

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.HexFormat

internal object NativeLibraryLoader {
    private const val WORK_DIRECTORY_PROPERTY = "pw.vodes.anitomy.native.workdir"
    private val ownerDirectoryPermissions = PosixFilePermissions.fromString("rwx------")

    fun load() {
        val platform = detectPlatform()
        val resource = "/META-INF/anitomy-kmp/${platform.directory}/${platform.libraryName}"
        val libraryBytes =
            NativeLibraryLoader::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: error(
                    "The Anitomy native library for ${platform.directory} is missing from the " +
                        "JVM artifact ($resource)",
                )

        val library =
            prepareLibrary(
                libraryBytes,
                platform.libraryName,
                configuredWorkDirectory(),
            )
        try {
            System.load(library.toString())
        } catch (failure: UnsatisfiedLinkError) {
            val enriched =
                UnsatisfiedLinkError(
                    "${failure.message.orEmpty()} Native extraction directory: " +
                        "${library.parent}. Override it with " +
                        "-D$WORK_DIRECTORY_PROPERTY=<directory> if the filesystem is mounted " +
                        "with noexec or is otherwise unsuitable for loading native libraries.",
                )
            enriched.initCause(failure)
            throw enriched
        }
    }

    internal fun prepareLibrary(
        libraryBytes: ByteArray,
        libraryName: String,
        workDirectory: Path,
    ): Path {
        val expectedDigest = sha256(libraryBytes)
        val digestName = HexFormat.of().formatHex(expectedDigest)
        val cacheRoot = workDirectory.toAbsolutePath().normalize().resolve("anitomy-kmp")
        val directory = cacheRoot.resolve(digestName)
        val library = directory.resolve(libraryName)

        Files.createDirectories(workDirectory)
        ensureCacheDirectory(cacheRoot)
        ensureCacheDirectory(directory)

        if (!hasDigest(library, expectedDigest)) {
            replaceLibrary(library, libraryBytes)
        }
        check(hasDigest(library, expectedDigest)) {
            "The extracted Anitomy native library failed SHA-256 validation: $library"
        }
        return library
    }

    private fun configuredWorkDirectory(): Path {
        val configured = System.getProperty(WORK_DIRECTORY_PROPERTY)?.takeIf(String::isNotBlank)
        return Path.of(configured ?: System.getProperty("java.io.tmpdir"))
    }

    private fun ensureCacheDirectory(directory: Path) {
        try {
            if ("posix" in directory.fileSystem.supportedFileAttributeViews()) {
                Files.createDirectory(
                    directory,
                    PosixFilePermissions.asFileAttribute(ownerDirectoryPermissions),
                )
            } else {
                Files.createDirectory(directory)
            }
        } catch (_: FileAlreadyExistsException) {
            check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                "Anitomy native cache path is not a directory: $directory"
            }
        }

        if ("posix" in directory.fileSystem.supportedFileAttributeViews()) {
            Files.setPosixFilePermissions(directory, ownerDirectoryPermissions)
        }
    }

    private fun replaceLibrary(
        library: Path,
        libraryBytes: ByteArray,
    ) {
        val temporary =
            Files.createTempFile(library.parent, "${library.fileName}.", ".tmp")
        try {
            Files.write(temporary, libraryBytes)
            try {
                Files.move(
                    temporary,
                    library,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, library, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: FileAlreadyExistsException) {
                Files.move(temporary, library, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun hasDigest(
        library: Path,
        expectedDigest: ByteArray,
    ): Boolean {
        if (!Files.isRegularFile(library, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(library, LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    digest.update(buffer, 0, count)
                }
            }
            MessageDigest.isEqual(expectedDigest, digest.digest())
        } catch (_: java.io.IOException) {
            false
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

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
