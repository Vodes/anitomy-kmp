package pw.vodes.anitomy

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmConcurrencyTest {
    @Test
    fun parsesConcurrentlyThroughOneLoadedJniLibrary() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures =
                executor.invokeAll(
                    List(200) { index ->
                        Callable {
                            parse("[Group$index] Show - S01E02.mkv")
                                .first { it.kind == ElementKind.RELEASE_GROUP }
                                .value
                        }
                    },
                )
            assertEquals(List(200) { "Group$it" }, futures.map { it.get() })
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
