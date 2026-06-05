package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the verdict cache: the key composition (every input perturbs the hash),
 * the never-cache-reds rule, the read/write round-trip, the noCache bypass, and fail-open on a garbage
 * cache file. These pin the soundness contract — a stale green is a soundness bug, so over-invalidation
 * is always fine and under-invalidation never is.
 */
class VerdictCacheTest {

    private static final String ENGINE = "jbmc-bundled:cbmc-6.9.0";

    private static BmcRequest req(String classpath) {
        return new BmcRequest("pkg.C", "pkg.C.proof", classpath,
                16, true, 16, false, "", 0);
    }

    private static BmcRequest baseReq() {
        return req("/some/classes");
    }

    @AfterEach
    void clearNoCache() {
        System.clearProperty("bmc.noCache");
    }

    // --- Key composition: each input perturbs the digest -----------------------

    @Test
    void sameInputs_sameKey() {
        assertEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(baseReq(), ENGINE),
                "identical inputs must hash identically");
    }

    @Test
    void engineIdentity_perturbsKey() {
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(baseReq(), "jbmc-bundled:cbmc-7.0.0"),
                "a different engine identity must change the key (swap-engine invalidation)");
    }

    @Test
    void entryFunction_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.otherProof", "/some/classes",
                16, true, 16, false, "", 0);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void unwind_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                8, true, 16, false, "", 0);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void unwindingAssertions_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, false, 16, false, "", 0);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void solver_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, false, "z3", 0);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void maxStringLength_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 4, false, "", 0);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void timeoutSeconds_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, false, "", 30);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void concurrent_perturbsKey() {
        BmcRequest other = new BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, true, "", 0);
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE));
    }

    @Test
    void classpathContent_perturbsKey(@TempDir Path dir) throws IOException {
        Path classes = Files.createDirectory(dir.resolve("classes"));
        Path clazz = classes.resolve("A.class");
        Files.write(clazz, new byte[]{1, 2, 3});
        String before = VerdictCache.classpathContentDigest(classes.toString());

        Files.write(clazz, new byte[]{1, 2, 4}); // edit a "production class"
        String after = VerdictCache.classpathContentDigest(classes.toString());

        assertNotEquals(before, after, "changing a compiled class must change the classpath digest");
    }

    @Test
    void classpathDigest_ignoresClasspathOrdering(@TempDir Path dir) throws IOException {
        Path a = Files.createDirectory(dir.resolve("a"));
        Path b = Files.createDirectory(dir.resolve("b"));
        Files.write(a.resolve("X.class"), new byte[]{1});
        Files.write(b.resolve("Y.class"), new byte[]{2});
        String ab = VerdictCache.classpathContentDigest(a + java.io.File.pathSeparator + b);
        String ba = VerdictCache.classpathContentDigest(b + java.io.File.pathSeparator + a);
        assertEquals(ab, ba, "classpath entry order must not change the digest");
    }

    @Test
    void nonModelJarContent_perturbsKey(@TempDir Path dir) throws IOException {
        // A consumer proof can reach into an ordinary (non-model) library jar's actual bytecode. Upgrading
        // that jar without recompiling app .class files must invalidate the cache, or a stale VERIFIED is
        // served across the dependency change. The jar carries no model coordinates in its name.
        Path jar = dir.resolve("acme-lib-1.0.jar");
        writeJar(jar, "com/acme/Lib.class", new byte[]{1, 2, 3});
        String before = VerdictCache.classpathContentDigest(jar.toString());
        String stable = VerdictCache.classpathContentDigest(jar.toString());
        assertEquals(before, stable, "an unchanged non-model jar must hash identically");

        writeJar(jar, "com/acme/Lib.class", new byte[]{1, 2, 4}); // "upgrade" the library's bytecode
        String after = VerdictCache.classpathContentDigest(jar.toString());
        assertNotEquals(before, after, "changing a non-model jar's content must change the classpath digest");
    }

    @Test
    void modelJarContent_stillPerturbsKey(@TempDir Path dir) throws IOException {
        // Model jars were already folded in; folding ALL jars must not regress them.
        Path jar = dir.resolve("bmc-models-1.0.jar");
        writeJar(jar, "org/bmc4j/models/M.class", new byte[]{1, 2, 3});
        String before = VerdictCache.classpathContentDigest(jar.toString());
        writeJar(jar, "org/bmc4j/models/M.class", new byte[]{1, 2, 4});
        String after = VerdictCache.classpathContentDigest(jar.toString());
        assertNotEquals(before, after, "changing a model jar's content must change the classpath digest");
    }

    /** Write a single-entry jar at {@code jar}, replacing any existing file. */
    private static void writeJar(Path jar, String entryName, byte[] content) throws IOException {
        try (java.util.zip.ZipOutputStream zout =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zout.putNextEntry(new java.util.zip.ZipEntry(entryName));
            zout.write(content);
            zout.closeEntry();
        }
    }

    @Test
    void userModelsContent_perturbsKey(@TempDir Path dir) throws IOException {
        // User models (src/bmcModel) are spliced onto the analysis classpath via the bmc.userModels
        // system property AFTER the cache key is built, so they aren't in request.classpath(). Editing
        // one must still invalidate the cache or a stale green is served (a soundness bug).
        Path models = Files.createDirectory(dir.resolve("bmcModel"));
        Path model = models.resolve("M.class");
        Files.write(model, new byte[]{1, 2, 3});
        String prev = System.getProperty("bmc.userModels");
        System.setProperty("bmc.userModels", models.toString());
        try {
            String before = VerdictCache.computeKey(baseReq(), ENGINE);
            String stable = VerdictCache.computeKey(baseReq(), ENGINE);
            assertEquals(before, stable, "unchanged user models must hash identically");

            Files.write(model, new byte[]{1, 2, 4}); // edit a user model class
            String after = VerdictCache.computeKey(baseReq(), ENGINE);
            assertNotEquals(before, after, "editing a user model must invalidate the cache");
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.userModels");
            } else {
                System.setProperty("bmc.userModels", prev);
            }
        }
    }

    // --- Resolved-config folding (config-pinning false-green fix) --------------

    /**
     * ConfigBytecode bakes the REAL System.getProperty("KEY") value into the analysed bytecode at
     * analysis time, but the app .class files don't change when the property changes — so the resolved
     * config value must be folded into the key, or a config-pinned proof keeps its cached green after the
     * value flips to one that violates the property (the exact false green this fix closes).
     */
    @Test
    void configValue_perturbsKey(@TempDir Path dir) throws IOException {
        Path classes = Files.createDirectory(dir.resolve("classes"));
        // A class that calls Bmc.intFromProperty("test.cfg.port") with a LITERAL key — exactly the
        // call site ConfigBytecode resolves and bakes.
        Files.write(classes.resolve("Cfg.class"),
                configReaderClass("Cfg", "intFromProperty", "(Ljava/lang/String;)I", "test.cfg.port"));
        BmcRequest r = req(classes.toString());

        String prev = System.getProperty("test.cfg.port");
        try {
            System.setProperty("test.cfg.port", "8080");
            String atX = VerdictCache.computeKey(r, ENGINE);
            String stable = VerdictCache.computeKey(r, ENGINE);
            assertEquals(atX, stable, "unchanged config value must hash identically");

            System.setProperty("test.cfg.port", "70000"); // a value that would violate a range proof
            String atY = VerdictCache.computeKey(r, ENGINE);
            assertNotEquals(atX, atY, "changing a referenced config value must change the key");

            System.clearProperty("test.cfg.port"); // toggling presence must also invalidate
            String unset = VerdictCache.computeKey(r, ENGINE);
            assertNotEquals(atY, unset, "unsetting a referenced config value must change the key");
        } finally {
            if (prev == null) {
                System.clearProperty("test.cfg.port");
            } else {
                System.setProperty("test.cfg.port", prev);
            }
        }
    }

    /** A class with NO config call sites must NOT be perturbed by an unrelated property change — the
     *  config fold is scoped to the keys a proof actually references, so non-config proofs keep caching. */
    @Test
    void unreferencedConfigValue_doesNotPerturbKey(@TempDir Path dir) throws IOException {
        Path classes = Files.createDirectory(dir.resolve("classes"));
        Files.write(classes.resolve("Plain.class"), plainClass("Plain"));
        BmcRequest r = req(classes.toString());

        String prev = System.getProperty("test.cfg.unreferenced");
        try {
            System.clearProperty("test.cfg.unreferenced");
            String before = VerdictCache.computeKey(r, ENGINE);
            System.setProperty("test.cfg.unreferenced", "changed");
            String after = VerdictCache.computeKey(r, ENGINE);
            assertEquals(before, after,
                    "a config value the proof does not read must not perturb its key (non-config proofs still cache)");
        } finally {
            if (prev == null) {
                System.clearProperty("test.cfg.unreferenced");
            } else {
                System.setProperty("test.cfg.unreferenced", prev);
            }
        }
    }

    /**
     * The actual soundness hole: a config-reading proof that round-trips VERIFIED under value X must NOT
     * be served that stored green under a value Y that violates the property. With value folded into the
     * key, value Y computes a different key → cache MISS → the engine re-runs (and would see Y).
     */
    @Test
    void configReadingProof_doesNotServeStaleGreen_afterValueChanges(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            Path classes = Files.createDirectory(dir.resolve("classes"));
            Files.write(classes.resolve("Cfg.class"),
                    configReaderClass("Cfg", "intFromProperty", "(Ljava/lang/String;)I", "test.cfg.port"));
            BmcRequest r = req(classes.toString());

            String prev = System.getProperty("test.cfg.port");
            try {
                // Value X: proof verifies, green is cached.
                System.setProperty("test.cfg.port", "8080");
                VerdictCache.storeIfVerified(r, ENGINE, new JbmcResult(true, List.of(), "raw"));
                assertTrue(VerdictCache.isVerified(r, ENGINE), "verified under X is cached and re-served under X");

                // Value Y (would violate the property): MUST NOT serve the stale green from X.
                System.setProperty("test.cfg.port", "70000");
                assertFalse(VerdictCache.isVerified(r, ENGINE),
                        "a stale green from value X must NOT be served under a violating value Y — it re-runs");
            } finally {
                if (prev == null) {
                    System.clearProperty("test.cfg.port");
                } else {
                    System.setProperty("test.cfg.port", prev);
                }
            }
        });
    }

    // --- Read/write round-trip -------------------------------------------------

    @Test
    void verifiedResult_roundTrips_throughCache(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            assertFalse(VerdictCache.isVerified(r, ENGINE), "cold cache: miss");
            VerdictCache.storeIfVerified(r, ENGINE, new JbmcResult(true, List.of(), "raw"));
            assertTrue(VerdictCache.isVerified(r, ENGINE), "after storing a green: hit");
        });
    }

    @Test
    void verifiedResult_storesAndReturnsStubList(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            JbmcResult green = new JbmcResult(true, List.of(), "raw")
                    .withStubbedMethods(List.of("java.util.Formatter.format", "java.util.List.stream"));
            VerdictCache.storeIfVerified(r, ENGINE, green);
            VerdictCache.Hit hit = VerdictCache.lookupVerified(r, ENGINE);
            assertTrue(hit != null, "stored green is a hit");
            assertEquals(List.of("java.util.Formatter.format", "java.util.List.stream"),
                    hit.stubbedMethods(),
                    "the harvested stub fact round-trips through the cache");
        });
    }

    @Test
    void rejudge_fromStoredStubs_withoutEngineRerun(@TempDir Path dir) throws Exception {
        // The KEY is unchanged by stub policy (allowStubs/strictStubs aren't request inputs), so the
        // SAME request hits the cache and the stored stub list is re-judged at read time — no re-run.
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            VerdictCache.storeIfVerified(r, ENGINE, new JbmcResult(true, List.of(), "raw")
                    .withStubbedMethods(List.of("java.util.Formatter.format")));
            VerdictCache.Hit hit = VerdictCache.lookupVerified(r, ENGINE);
            assertTrue(hit != null);
            // strict + no allowlist -> unacknowledged
            StubPolicy strictNoAllow = StubPolicy.judge(hit.stubbedMethods(), List.of(), "");
            assertTrue(strictNoAllow.hasUnacknowledged());
            // edit allowStubs (no re-run, same cache) -> acknowledged
            StubPolicy allowed = StubPolicy.judge(hit.stubbedMethods(),
                    List.of("java.util.Formatter.*"), "");
            assertFalse(allowed.hasUnacknowledged());
        });
    }

    // --- Never-cache-reds rule -------------------------------------------------

    @Test
    void refutedResult_isNeverCached(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            JbmcResult refuted = new JbmcResult(false, List.of(
                    new JbmcResult.Violation("boom", "C.java", 1, List.of(), List.of())), "raw");
            VerdictCache.storeIfVerified(r, ENGINE, refuted);
            assertFalse(VerdictCache.isVerified(r, ENGINE), "REFUTED must never be cached");
        });
    }

    @Test
    void unknownResult_isNeverCached(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            VerdictCache.storeIfVerified(r, ENGINE, JbmcResult.unknown("timed out", "raw"));
            assertFalse(VerdictCache.isVerified(r, ENGINE), "UNKNOWN must never be cached");
        });
    }

    @Test
    void vacuousResult_isNeverCached(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            JbmcResult vacuous = new JbmcResult(false, List.of(
                    new JbmcResult.Violation(BmcReachability.VACUOUS_MESSAGE, null, 0, List.of(), List.of())),
                    "raw", true);
            VerdictCache.storeIfVerified(r, ENGINE, vacuous);
            assertFalse(VerdictCache.isVerified(r, ENGINE), "VACUOUS (a flavour of refuted) must never be cached");
        });
    }

    // --- Bypass + fail-open ----------------------------------------------------

    @Test
    void noCacheProp_disablesReadAndWrite(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            // Store while enabled, then a noCache read must MISS (forces re-verification).
            VerdictCache.storeIfVerified(r, ENGINE, new JbmcResult(true, List.of(), "raw"));
            System.setProperty("bmc.noCache", "true");
            assertTrue(VerdictCache.disabled());
            assertFalse(VerdictCache.isVerified(r, ENGINE), "-Dbmc.noCache=true must force a miss");
            // And a write while disabled must not persist.
            BmcRequest r2 = new BmcRequest("pkg.C", "pkg.C.other", dir.resolve("classes").toString(),
                    16, true, 16, false, "", 0);
            VerdictCache.storeIfVerified(r2, ENGINE, new JbmcResult(true, List.of(), "raw"));
            System.clearProperty("bmc.noCache");
            assertFalse(VerdictCache.isVerified(r2, ENGINE), "no write happens while disabled");
        });
    }

    @Test
    void garbageCacheEntry_isIgnored_failOpen(@TempDir Path dir) throws Exception {
        runIn(dir, () -> {
            BmcRequest r = req(dir.resolve("classes").toString());
            String key = VerdictCache.computeKey(r, ENGINE);
            // Mirror VerdictCache.cacheDir(): build/bmc4j/verdict-cache under the (redirected) user.dir.
            Path entry = Path.of(System.getProperty("user.dir"), "build", "bmc4j", "verdict-cache", key);
            Files.createDirectories(entry.getParent());
            Files.write(entry, "GARBAGE \0\0 not a verdict".getBytes(StandardCharsets.UTF_8));
            assertFalse(VerdictCache.isVerified(r, ENGINE),
                    "a scribbled cache entry must be treated as a miss (fail-open), not a hit");
        });
    }

    /**
     * Bytecode for a class {@code name} with a method {@code m()} that invokes
     * {@code Bmc.<method><desc>("key")} with a LITERAL key — exactly the config call site
     * {@link ConfigBytecode} resolves and bakes (and {@code VerdictCache} scans for the cache key).
     */
    private static byte[] configReaderClass(String name, String method, String desc, String key) {
        org.objectweb.asm.ClassWriter cw =
                new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
        cw.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null,
                "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
                "m", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(key);
        mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", method, desc, false);
        // Discard whatever the reader "returns" (we only care that the call site exists) and return.
        if (desc.endsWith("J") || desc.endsWith("D")) {
            mv.visitInsn(org.objectweb.asm.Opcodes.POP2);
        } else {
            mv.visitInsn(org.objectweb.asm.Opcodes.POP);
        }
        mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Bytecode for a class with NO config call sites (an empty static method). */
    private static byte[] plainClass(String name) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null,
                "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
                "m", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Run {@code body} with the process working directory temporarily pointed at {@code dir}. */
    private static void runIn(Path dir, ThrowingRunnable body) throws Exception {
        String prev = System.getProperty("user.dir");
        // VerdictCache resolves build/bmc4j/verdict-cache relative to the CWD via Path.of(relative),
        // which the JVM resolves against user.dir at access time — so overriding it redirects the cache.
        System.setProperty("user.dir", dir.toAbsolutePath().toString());
        try {
            body.run();
        } finally {
            System.setProperty("user.dir", prev);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
