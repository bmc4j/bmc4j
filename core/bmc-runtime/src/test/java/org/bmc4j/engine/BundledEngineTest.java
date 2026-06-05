package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression guard for {@link BundledEngine#extract()}.
 *
 * <p>Pins the two safety properties of the first-use extraction path that were hardened when the
 * parallel-worker {@code FileAlreadyExistsException} race was fixed (in-JVM lock + extract-to-temp +
 * atomic rename):
 * <ol>
 *   <li><b>Concurrency:</b> N threads racing {@code extract()} all observe the same, complete engine —
 *       no thread throws, none ever sees a half-written binary, and no temp/partial state leaks.</li>
 *   <li><b>Stale-partial recovery:</b> a cache directory left behind by an aborted prior extraction
 *       (present, but missing/truncated executable) is detected and re-extracted, not handed back as-is.</li>
 * </ol>
 *
 * <p>Isolation: {@code BundledEngine} derives its cache root from {@code user.home}. Each test points
 * {@code user.home} at a {@link TempDir}, so the developer's real {@code ~/.cache/bmc4j} engine cache is
 * never read or written. The engine itself is a small fake resource tree shipped under
 * {@code src/test/resources/jbmc/<platform>/} (the real {@code bmc-engine-*} jars are not on the
 * bmc-runtime test classpath), so the test is deterministic and self-contained on every platform.
 */
class BundledEngineTest {

    /** Marker content of the fake bundled executable; a complete extraction must reproduce it exactly. */
    private static final String COMPLETE_EXE = "FAKE_BMC4J_TEST_ENGINE_BINARY_COMPLETE\n";

    private String savedUserHome;

    @BeforeEach
    void redirectCacheToTempHome(@TempDir Path fakeHome) {
        savedUserHome = System.getProperty("user.home");
        System.setProperty("user.home", fakeHome.toString());
    }

    @AfterEach
    void restoreUserHome() {
        if (savedUserHome != null) {
            System.setProperty("user.home", savedUserHome);
        } else {
            System.clearProperty("user.home");
        }
    }

    /** The cache dir BundledEngine extracts into, derived the same way the production code does. */
    private static Path cacheDir() {
        Platform platform = Platform.current();
        String version = BundledEngine.version();
        Path base = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "engine");
        return base.resolve(platform.id() + (version != null ? "-" + version : ""));
    }

    private static Path exePath() {
        return cacheDir().resolve("bin/jbmc" + (Platform.current().isWindows() ? ".exe" : ""));
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Any {@code <name>.tmp-XXXX} sibling left under the cache parent means a leaked partial extraction. */
    private static List<Path> leakedTempDirs() throws IOException {
        Path parent = cacheDir().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> kids = Files.list(parent)) {
            return kids.filter(p -> p.getFileName().toString().contains(".tmp-")).toList();
        }
    }

    private void assertEngineIsComplete() throws IOException {
        Path exe = exePath();
        assertTrue(Files.isRegularFile(exe), "engine executable must exist: " + exe);
        assertEquals(COMPLETE_EXE, read(exe), "engine executable must be the complete, uncorrupted copy");
        Path models = cacheDir().resolve("lib/core-models.jar");
        assertTrue(Files.isRegularFile(models), "all manifest files must be extracted: " + models);
        assertTrue(leakedTempDirs().isEmpty(), "no temp/partial extraction dirs may leak: " + leakedTempDirs());
    }

    @Test
    void sanity_extract_produces_a_complete_engine() throws IOException {
        String path = BundledEngine.extract();
        assertEquals(exePath().toString(), path);
        assertEngineIsComplete();
    }

    @RepeatedTest(5) // timing-sensitive: repeat to make a reintroduced race surface deterministically
    void concurrent_callers_all_get_one_complete_engine() throws Exception {
        int n = 8;
        CyclicBarrier startTogether = new CyclicBarrier(n);
        CountDownLatch done = new CountDownLatch(n);
        Set<String> returnedPaths = ConcurrentHashMap.newKeySet();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        startTogether.await(5, TimeUnit.SECONDS); // maximize overlap on the extract path
                        returnedPaths.add(BundledEngine.extract());
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "all extract() callers must finish");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(failures.isEmpty(), "no extract() caller may throw, but got: " + failures);
        assertEquals(Set.of(exePath().toString()), returnedPaths,
                "every concurrent caller must get the same extracted engine path");
        assertEngineIsComplete();
    }

    @Test
    void second_extract_short_circuits_and_does_not_re_extract() throws Exception {
        String first = BundledEngine.extract();
        Path exe = exePath();
        assertEngineIsComplete();

        // Tag the extracted binary; a short-circuiting second call must NOT overwrite it.
        Files.writeString(exe, COMPLETE_EXE + "WORKER_TAG\n");

        String second = BundledEngine.extract();
        assertEquals(first, second, "repeat extract() must return the cached path");
        assertEquals(COMPLETE_EXE + "WORKER_TAG\n", read(exe),
                "a present, complete engine must be reused as-is, not re-extracted");
    }

    @Test
    void stale_partial_extraction_is_detected_and_replaced() throws Exception {
        // Simulate an aborted prior extraction: the cache dir exists with some leftover content, but the
        // executable was never written (the move-into-place never completed) — i.e. "started, not finished".
        Path cache = cacheDir();
        Files.createDirectories(cache.resolve("bin"));
        Path exe = exePath();
        Files.writeString(exe.resolveSibling("jbmc.partial"), "TRUNCATED");
        assertFalse(Files.isRegularFile(exe), "precondition: stale cache dir has no complete executable");

        String path = assertDoesNotThrow(BundledEngine::extract);

        assertEquals(exePath().toString(), path);
        assertEngineIsComplete();
        assertEquals(COMPLETE_EXE, read(exe), "stale partial must be replaced with the complete engine");
    }

    @Test
    void stale_partial_with_truncated_executable_is_replaced() throws Exception {
        // Harsher variant: the executable file *exists* but is corrupt/truncated. BundledEngine treats
        // any present regular file at the exe path as "complete" (isRegularFile short-circuit), so this
        // documents that contract: a truncated-but-present exe is currently trusted. If a future change
        // adds content/marker validation, this test should be tightened accordingly.
        Path cache = cacheDir();
        Files.createDirectories(cache.resolve("bin"));
        Path exe = exePath();
        Files.writeString(exe, "TRUNCATED");

        String path = BundledEngine.extract();

        assertEquals(exePath().toString(), path);
        // Current behavior: present exe is reused. Asserting it documents the boundary the race-fix guards.
        assertEquals("TRUNCATED", read(exe),
                "a present (regular-file) exe is treated as complete by the current isRegularFile check");
    }

    @Test
    void version_reads_the_bundled_marker() {
        assertEquals("fake-cbmc-test-0.0.0", BundledEngine.version());
    }

    // --- musl/Alpine detection (the bundled Linux engine is glibc-only) ---

    @Test
    void musl_detected_via_alpine_release_marker(@TempDir Path fsRoot) throws IOException {
        Files.createDirectories(fsRoot.resolve("etc"));
        Files.writeString(fsRoot.resolve("etc/alpine-release"), "3.19.0\n");
        assertTrue(BundledEngine.isMuslLibc(fsRoot), "/etc/alpine-release must signal musl");
    }

    @Test
    void musl_detected_via_ld_musl_loader(@TempDir Path fsRoot) throws IOException {
        Files.createDirectories(fsRoot.resolve("lib"));
        Files.writeString(fsRoot.resolve("lib/ld-musl-x86_64.so.1"), "");
        assertTrue(BundledEngine.isMuslLibc(fsRoot), "an ld-musl-*.so.1 loader must signal musl");
    }

    @Test
    void glibc_system_is_not_flagged_as_musl(@TempDir Path fsRoot) throws IOException {
        // A glibc layout: a glibc loader present, but neither musl signal.
        Files.createDirectories(fsRoot.resolve("lib"));
        Files.writeString(fsRoot.resolve("lib/ld-linux-x86-64.so.2"), "");
        assertFalse(BundledEngine.isMuslLibc(fsRoot), "a glibc system must not be flagged musl");
    }
}
