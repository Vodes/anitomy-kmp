package pw.vodes.anitomy.internal

import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeLibraryLoaderTest {
    @Test
    fun reusesAValidatedContentAddressedLibrary() =
        withTemporaryDirectory { workDirectory ->
            val bytes = "native-library".encodeToByteArray()
            val first =
                NativeLibraryLoader.prepareLibrary(bytes, "libexample.so", workDirectory)
            val modifiedTime = Files.getLastModifiedTime(first)

            val second =
                NativeLibraryLoader.prepareLibrary(bytes, "libexample.so", workDirectory)

            assertEquals(first, second)
            assertEquals(modifiedTime, Files.getLastModifiedTime(second))
            assertContentEquals(bytes, second.readBytes())
        }

    @Test
    fun replacesACorruptedCachedLibrary() =
        withTemporaryDirectory { workDirectory ->
            val bytes = "expected-native-library".encodeToByteArray()
            val library =
                NativeLibraryLoader.prepareLibrary(bytes, "libexample.so", workDirectory)
            Files.write(library, "corrupted".encodeToByteArray())

            val repaired =
                NativeLibraryLoader.prepareLibrary(bytes, "libexample.so", workDirectory)

            assertEquals(library, repaired)
            assertContentEquals(bytes, repaired.readBytes())
        }

    @Test
    fun preparesTheSameLibraryConcurrently() =
        withTemporaryDirectory { workDirectory ->
            val bytes = ByteArray(64 * 1024) { it.toByte() }
            val executor = Executors.newFixedThreadPool(8)
            try {
                val libraries =
                    executor
                        .invokeAll(
                            List(100) {
                                Callable {
                                    NativeLibraryLoader.prepareLibrary(
                                        bytes,
                                        "libexample.so",
                                        workDirectory,
                                    )
                                }
                            },
                        ).map { it.get() }

                assertEquals(1, libraries.toSet().size)
                assertContentEquals(bytes, libraries.first().readBytes())
            } finally {
                executor.shutdown()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            }
        }

    private fun withTemporaryDirectory(block: (java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("anitomy-loader-test-")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
