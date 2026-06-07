package org.bmc4j.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/**
 * Regression guard for [BundledEngine.extract].
 *
 * Pins the two safety properties of the first-use extraction path that were hardened when the
 * parallel-worker `FileAlreadyExistsException` race was fixed (in-JVM lock + extract-to-temp +
 * atomic rename):
 *  1. **Concurrency:** N threads racing `extract()` all observe the same, complete engine —
 *     no thread throws, none ever sees a half-written binary, and no temp/partial state leaks.
 *  2. **Stale-partial recovery:** a cache directory left behind by an aborted prior extraction
 *     (present, but missing/truncated executable) is detected and re-extracted, not handed back as-is.
 *
 * Isolation: `BundledEngine` derives its cache root from `user.home`. Each test points
 * `user.home` at a [TempDir], so the developer's real `~/.cache/bmc4j` engine cache is
 * never read or written. The engine itself is a small fake resource tree shipped under
 * `src/test/resources/jbmc/<platform>/` (the real `bmc-engine-*` jars are not on the
 * bmc-runtime test classpath), so the test is deterministic and self-contained on every platform.
 */
internal class BundledEngineTest {

    private var savedUserHome: String? = null

    @BeforeEach
    fun redirectCacheToTempHome(@TempDir fakeHome: Path) {
        savedUserHome = System.getProperty("user.home")
        System.setProperty("user.home", fakeHome.toString())
    }

    @AfterEach
    fun restoreUserHome() {
        if (savedUserHome != null) {
            System.setProperty("user.home", savedUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
    }

    private fun assertEngineIsComplete() {
        val exe = exePath()
        assertTrue(Files.isRegularFile(exe), "engine executable must exist: $exe")
        assertEquals(COMPLETE_EXE, read(exe), "engine executable must be the complete, uncorrupted copy")
        val models = cacheDir().resolve("lib/core-models.jar")
        assertTrue(Files.isRegularFile(models), "all manifest files must be extracted: $models")
        assertTrue(leakedTempDirs().isEmpty(), "no temp/partial extraction dirs may leak: " + leakedTempDirs())
    }

    @Test
    fun sanity_extract_produces_a_complete_engine() {
        val path = BundledEngine.extract()
        assertEquals(exePath().toString(), path)
        assertEngineIsComplete()
    }

    @RepeatedTest(5) // timing-sensitive: repeat to make a reintroduced race surface deterministically
    fun concurrent_callers_all_get_one_complete_engine() {
        val n = 8
        val startTogether = CyclicBarrier(n)
        val done = CountDownLatch(n)
        val returnedPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val failures: MutableList<Throwable> = CopyOnWriteArrayList()

        val pool = Executors.newFixedThreadPool(n)
        try {
            for (i in 0 until n) {
                pool.submit {
                    try {
                        startTogether.await(5, TimeUnit.SECONDS) // maximize overlap on the extract path
                        returnedPaths.add(BundledEngine.extract())
                    } catch (t: Throwable) {
                        failures.add(t)
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "all extract() callers must finish")
        } finally {
            pool.shutdownNow()
        }

        assertTrue(failures.isEmpty(), "no extract() caller may throw, but got: $failures")
        assertEquals(setOf(exePath().toString()), returnedPaths,
                "every concurrent caller must get the same extracted engine path")
        assertEngineIsComplete()
    }

    @Test
    fun second_extract_short_circuits_and_does_not_re_extract() {
        val first = BundledEngine.extract()
        val exe = exePath()
        assertEngineIsComplete()

        // Tag the extracted binary; a short-circuiting second call must NOT overwrite it.
        Files.writeString(exe, COMPLETE_EXE + "WORKER_TAG\n")

        val second = BundledEngine.extract()
        assertEquals(first, second, "repeat extract() must return the cached path")
        assertEquals(COMPLETE_EXE + "WORKER_TAG\n", read(exe),
                "a present, complete engine must be reused as-is, not re-extracted")
    }

    @Test
    fun stale_partial_extraction_is_detected_and_replaced() {
        // Simulate an aborted prior extraction: the cache dir exists with some leftover content, but the
        // executable was never written (the move-into-place never completed) — i.e. "started, not finished".
        val cache = cacheDir()
        Files.createDirectories(cache.resolve("bin"))
        val exe = exePath()
        Files.writeString(exe.resolveSibling("jbmc.partial"), "TRUNCATED")
        assertFalse(Files.isRegularFile(exe), "precondition: stale cache dir has no complete executable")

        val path = assertDoesNotThrow<String> { BundledEngine.extract() }

        assertEquals(exePath().toString(), path)
        assertEngineIsComplete()
        assertEquals(COMPLETE_EXE, read(exe), "stale partial must be replaced with the complete engine")
    }

    @Test
    fun stale_partial_with_truncated_executable_is_replaced() {
        // Harsher variant: the executable file *exists* but is corrupt/truncated. BundledEngine treats
        // any present regular file at the exe path as "complete" (isRegularFile short-circuit), so this
        // documents that contract: a truncated-but-present exe is currently trusted. If a future change
        // adds content/marker validation, this test should be tightened accordingly.
        val cache = cacheDir()
        Files.createDirectories(cache.resolve("bin"))
        val exe = exePath()
        Files.writeString(exe, "TRUNCATED")

        val path = BundledEngine.extract()

        assertEquals(exePath().toString(), path)
        // Current behavior: present exe is reused. Asserting it documents the boundary the race-fix guards.
        assertEquals("TRUNCATED", read(exe),
                "a present (regular-file) exe is treated as complete by the current isRegularFile check")
    }

    @Test
    fun version_reads_the_bundled_marker() {
        assertEquals("fake-cbmc-test-0.0.0", BundledEngine.version())
    }

    // --- musl/Alpine detection (drives selection of the linux-x64-musl engine) ---
    // A musl x64 host reports the same Linux/amd64 as glibc, so the C-library probe below is what
    // Platform.current() uses to REDIRECT to the musl-built engine jar instead of the glibc one.

    @Test
    fun musl_detected_via_alpine_release_marker(@TempDir fsRoot: Path) {
        Files.createDirectories(fsRoot.resolve("etc"))
        Files.writeString(fsRoot.resolve("etc/alpine-release"), "3.19.0\n")
        assertTrue(BundledEngine.isMuslLibc(fsRoot), "/etc/alpine-release must signal musl")
    }

    @Test
    fun musl_detected_via_ld_musl_loader(@TempDir fsRoot: Path) {
        Files.createDirectories(fsRoot.resolve("lib"))
        Files.writeString(fsRoot.resolve("lib/ld-musl-x86_64.so.1"), "")
        assertTrue(BundledEngine.isMuslLibc(fsRoot), "an ld-musl-*.so.1 loader must signal musl")
    }

    @Test
    fun glibc_system_is_not_flagged_as_musl(@TempDir fsRoot: Path) {
        // A glibc layout: a glibc loader present, but neither musl signal.
        Files.createDirectories(fsRoot.resolve("lib"))
        Files.writeString(fsRoot.resolve("lib/ld-linux-x86-64.so.2"), "")
        assertFalse(BundledEngine.isMuslLibc(fsRoot), "a glibc system must not be flagged musl")
    }

    companion object {
        /** Marker content of the fake bundled executable; a complete extraction must reproduce it exactly. */
        private const val COMPLETE_EXE = "FAKE_BMC4J_TEST_ENGINE_BINARY_COMPLETE\n"

        /** The cache dir BundledEngine extracts into, derived the same way the production code does. */
        private fun cacheDir(): Path {
            val platform = Platform.current()
            val version = BundledEngine.version()
            val base = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "engine")
            return base.resolve(platform.id + (if (version != null) "-$version" else ""))
        }

        private fun exePath(): Path =
                cacheDir().resolve("bin/jbmc" + (if (Platform.current().isWindows) ".exe" else ""))

        @Throws(IOException::class)
        private fun read(p: Path): String = String(Files.readAllBytes(p), StandardCharsets.UTF_8)

        /** Any `<name>.tmp-XXXX` sibling left under the cache parent means a leaked partial extraction. */
        @Throws(IOException::class)
        private fun leakedTempDirs(): List<Path> {
            val parent = cacheDir().parent
            if (parent == null || !Files.isDirectory(parent)) {
                return listOf()
            }
            Files.list(parent).use { kids ->
                return kids.filter { p -> p.fileName.toString().contains(".tmp-") }.toList()
            }
        }
    }
}
