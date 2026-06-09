package org.bmc4j.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for the verdict cache: the key composition (every input perturbs the hash),
 * the only-expectation-matching-deterministic-passes rule, the read/write round-trip, the noCache
 * bypass, and fail-open on a garbage cache file. These pin the soundness contract — a stale green is a
 * soundness bug, so over-invalidation is always fine and under-invalidation never is.
 */
internal class VerdictCacheTest {

    @AfterEach
    fun clearNoCache() {
        System.clearProperty("bmc.noCache")
    }

    // --- Key composition: each input perturbs the digest -----------------------

    @Test
    fun sameInputs_sameKey() {
        assertEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(baseReq(), ENGINE),
                "identical inputs must hash identically")
    }

    @Test
    fun engineIdentity_perturbsKey() {
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(baseReq(), "jbmc-bundled:cbmc-7.0.0"),
                "a different engine identity must change the key (swap-engine invalidation)")
    }

    @Test
    fun slicePolicy_perturbsKey() {
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(baseReq(), ENGINE, slicePolicy = "unsliced"),
                "a verdict computed under a different slicing policy (or none) must never " +
                        "satisfy this proof's lookup — slicing reshapes the analysis classpath " +
                        "after the key is built")
    }

    @Test
    fun slicePolicy_defaultIsTheRealPolicy() {
        assertEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(baseReq(), ENGINE, slicePolicy = ModelSlice.KEEP_POLICY_VERSION),
                "the production key must be computed under ModelSlice's actual policy identity, " +
                        "so a KEEP_POLICY_VERSION bump invalidates prior verdicts automatically")
    }

    @Test
    fun entryFunction_perturbsKey() {
        val other = BmcRequest("pkg.C", "pkg.C.otherProof", "/some/classes",
                16, true, 16, "", 0)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE))
    }

    @Test
    fun unwind_perturbsKey() {
        val other = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                8, true, 16, "", 0)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE))
    }

    @Test
    fun unwindingAssertions_perturbsKey() {
        val other = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, false, 16, "", 0)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE))
    }

    @Test
    fun solver_perturbsKey() {
        val other = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, "z3", 0)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE))
    }

    @Test
    fun resolvedExternalSatPath_perturbsKey() {
        // The RESOLVED fast-solver binary identity (not just the requested name) must bust the cache:
        // swapping the external SAT binary can change a verdict.
        val other = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, "", 0, externalSatPath = "/opt/kissat/bin/kissat", stringRefinementOff = true)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE),
                "the resolved external-SAT binary identity must be part of the cache key")
    }

    @Test
    fun stringRefinementMode_perturbsKey() {
        // A verdict proven with text/String reasoning OFF (external SAT) must NEVER be served for a
        // text-reasoning-ON request, or vice versa — so the refinement mode is part of the key.
        // Keep the external-sat path identical so ONLY the refinement flag differs.
        val refinementOn = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, "", 0, externalSatPath = "/opt/kissat/bin/kissat", stringRefinementOff = false)
        val refinementOff = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, "", 0, externalSatPath = "/opt/kissat/bin/kissat", stringRefinementOff = true)
        assertNotEquals(VerdictCache.computeKey(refinementOn, ENGINE),
                VerdictCache.computeKey(refinementOff, ENGINE),
                "flipping the string-refinement mode must change the cache key (sound vs unsound run)")
    }

    @Test
    fun maxStringLength_perturbsKey() {
        val other = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 4, "", 0)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE))
    }

    @Test
    fun kotlinNullableParams_perturbsKey() {
        // KotlinParamBytecode rewrites proof prologues AFTER the key is built, and the app .class
        // files don't change when the mode flips — fold it in or a green proven under auto-assume
        // would be served in honest-JVM mode (a soundness bug in that direction).
        val prev = System.getProperty("bmc.kotlinNullableParams")
        try {
            System.clearProperty("bmc.kotlinNullableParams")
            val autoAssume = VerdictCache.computeKey(baseReq(), ENGINE)
            System.setProperty("bmc.kotlinNullableParams", "true")
            val honestJvm = VerdictCache.computeKey(baseReq(), ENGINE)
            assertNotEquals(autoAssume, honestJvm,
                    "flipping bmc.kotlinNullableParams must invalidate the cache")
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.kotlinNullableParams")
            } else {
                System.setProperty("bmc.kotlinNullableParams", prev)
            }
        }
    }

    @Test
    fun timeoutSeconds_perturbsKey() {
        val other = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                16, true, 16, "", 30)
        assertNotEquals(VerdictCache.computeKey(baseReq(), ENGINE),
                VerdictCache.computeKey(other, ENGINE))
    }

    // --- Verdict-relevant flag signature (single source of truth with the jbmc command) ----------

    /**
     * The flag signature must carry exactly the verdict-changing flags and NONE of the
     * volatile/non-verdict parts. This is the contract that closes the gap a hard-coded jbmc flag
     * (e.g. `--slice-formula`) opened: such a flag, added to the one builder, lands in BOTH the command
     * and this signature automatically — so it can never silently diverge a cached verdict from reality.
     */
    @Test
    fun flagSignature_includesVerdictFlags_excludesVolatileAndUi() {
        val r = BmcRequest("pkg.C", "pkg.C.proof", "/machine/specific/classpath",
                8, true, 12, "z3", 0)
        val sig = Jbmc.verdictRelevantFlags(r)
        // INCLUDES every verdict-relevant flag.
        assertTrue(sig.contains("--unwind 8"), "the unwind bound is verdict-relevant: $sig")
        assertTrue(sig.contains("--unwinding-assertions"), "unwinding-assertions is verdict-relevant: $sig")
        assertTrue(sig.contains("--max-nondet-string-length 12"),
                "the nondet string-length bound is verdict-relevant: $sig")
        assertTrue(sig.contains("--z3"), "the solver selection is verdict-relevant: $sig")
        // EXCLUDES the executable path, the --classpath VALUE, the --function/entry, and UI flags.
        assertFalse(sig.contains("--classpath"), "the classpath flag/value must be excluded: $sig")
        assertFalse(sig.contains("/machine/specific/classpath"),
                "the classpath value (machine/shard-volatile) must be excluded: $sig")
        assertFalse(sig.contains("--function"), "the entry function must be excluded: $sig")
        assertFalse(sig.contains("pkg.C.proof"), "the entry value must be excluded: $sig")
        assertFalse(sig.contains("--json-ui"), "pure UI flags must be excluded: $sig")
        assertFalse(sig.contains("--trace"), "pure UI flags must be excluded: $sig")
        assertFalse(sig.contains("--verbosity"), "pure UI flags must be excluded: $sig")
    }

    /**
     * Changing a verdict-relevant flag must perturb the key THROUGH the signature — this is the central
     * guarantee. (unwind/ua/msl/solver each already have a dedicated perturbation test above; here we pin
     * that the signature itself differs, i.e. the cache derives its flag identity from the same builder
     * the command uses, so a future hard-coded flag is covered with no further wiring.)
     */
    @Test
    fun changingAVerdictRelevantFlag_perturbsSignatureAndKey() {
        val base = baseReq()
        val differentUnwind = BmcRequest("pkg.C", "pkg.C.proof", "/some/classes",
                8, true, 16, "", 0)
        assertNotEquals(Jbmc.verdictRelevantFlags(base), Jbmc.verdictRelevantFlags(differentUnwind),
                "a verdict-relevant flag change must change the flag signature")
        assertNotEquals(VerdictCache.computeKey(base, ENGINE),
                VerdictCache.computeKey(differentUnwind, ENGINE),
                "...and therefore the cache key")
    }

    /**
     * A volatile-only change must NOT perturb the flag signature: the classpath PATH varies per
     * machine/shard (its CONTENT is keyed via the cone digest, the path is not) and the entry name is
     * keyed separately, so two requests with identical verdict-relevant flags but different classpath
     * paths / entry names produce the SAME signature. (Over-keying on volatile noise would wrongly bust
     * the cache per-shard and break the shard-cache-union design.)
     */
    @Test
    fun volatileOnlyChange_doesNotPerturbSignature() {
        val onShardA = BmcRequest("pkg.C", "pkg.C.proof", "/runner-a/work/classes",
                16, true, 16, "", 0)
        val onShardB = BmcRequest("pkg.C", "pkg.C.proof", "/runner-b/other/classes",
                16, true, 16, "", 0)
        assertEquals(Jbmc.verdictRelevantFlags(onShardA), Jbmc.verdictRelevantFlags(onShardB),
                "a different classpath PATH (same flags) must not change the flag signature")
    }

    @Test
    fun classpathContent_perturbsKey(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        val clazz = classes.resolve("A.class")
        Files.write(clazz, byteArrayOf(1, 2, 3))
        val before = VerdictCache.classpathContentDigest(classes.toString())

        Files.write(clazz, byteArrayOf(1, 2, 4)) // edit a "production class"
        val after = VerdictCache.classpathContentDigest(classes.toString())

        assertNotEquals(before, after, "changing a compiled class must change the classpath digest")
    }

    @Test
    fun classpathDigest_ignoresClasspathOrdering(@TempDir dir: Path) {
        val a = Files.createDirectory(dir.resolve("a"))
        val b = Files.createDirectory(dir.resolve("b"))
        Files.write(a.resolve("X.class"), byteArrayOf(1))
        Files.write(b.resolve("Y.class"), byteArrayOf(2))
        val ab = VerdictCache.classpathContentDigest(a.toString() + File.pathSeparator + b)
        val ba = VerdictCache.classpathContentDigest(b.toString() + File.pathSeparator + a)
        assertEquals(ab, ba, "classpath entry order must not change the digest")
    }

    @Test
    fun nonModelJarContent_perturbsKey(@TempDir dir: Path) {
        // A consumer proof can reach into an ordinary (non-model) library jar's actual bytecode. Upgrading
        // that jar without recompiling app .class files must invalidate the cache, or a stale VERIFIED is
        // served across the dependency change. The jar carries no model coordinates in its name.
        val jar = dir.resolve("acme-lib-1.0.jar")
        writeJar(jar, "com/acme/Lib.class", byteArrayOf(1, 2, 3))
        val before = VerdictCache.classpathContentDigest(jar.toString())
        val stable = VerdictCache.classpathContentDigest(jar.toString())
        assertEquals(before, stable, "an unchanged non-model jar must hash identically")

        writeJar(jar, "com/acme/Lib.class", byteArrayOf(1, 2, 4)) // "upgrade" the library's bytecode
        val after = VerdictCache.classpathContentDigest(jar.toString())
        assertNotEquals(before, after, "changing a non-model jar's content must change the classpath digest")
    }

    @Test
    fun modelJarContent_stillPerturbsKey(@TempDir dir: Path) {
        // Model jars were already folded in; folding ALL jars must not regress them.
        val jar = dir.resolve("bmc-models-1.0.jar")
        writeJar(jar, "org/bmc4j/models/M.class", byteArrayOf(1, 2, 3))
        val before = VerdictCache.classpathContentDigest(jar.toString())
        writeJar(jar, "org/bmc4j/models/M.class", byteArrayOf(1, 2, 4))
        val after = VerdictCache.classpathContentDigest(jar.toString())
        assertNotEquals(before, after, "changing a model jar's content must change the classpath digest")
    }

    @Test
    fun userModelsContent_perturbsKey(@TempDir dir: Path) {
        // User models (src/bmcModel) are spliced onto the analysis classpath via the bmc.userModels
        // system property AFTER the cache key is built, so they aren't in request.classpath(). Editing
        // one must still invalidate the cache or a stale green is served (a soundness bug).
        val models = Files.createDirectory(dir.resolve("bmcModel"))
        val model = models.resolve("M.class")
        Files.write(model, byteArrayOf(1, 2, 3))
        val prev = System.getProperty("bmc.userModels")
        System.setProperty("bmc.userModels", models.toString())
        try {
            val before = VerdictCache.computeKey(baseReq(), ENGINE)
            val stable = VerdictCache.computeKey(baseReq(), ENGINE)
            assertEquals(before, stable, "unchanged user models must hash identically")

            Files.write(model, byteArrayOf(1, 2, 4)) // edit a user model class
            // A real edit always moves size or mtime (recompilation rewrites the file). Make the mtime
            // move explicit: this same-size in-place write could otherwise share a coarse-clock
            // timestamp tick with the original, and the digest memo's fingerprint is (path, size, mtime).
            Files.setLastModifiedTime(model, FileTime.fromMillis(
                    Files.getLastModifiedTime(model).toMillis() + 1_000))
            val after = VerdictCache.computeKey(baseReq(), ENGINE)
            assertNotEquals(before, after, "editing a user model must invalidate the cache")
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.userModels")
            } else {
                System.setProperty("bmc.userModels", prev)
            }
        }
    }

    // --- Reachable-cone scoping (the central change) ---------------------------

    /**
     * The goal behaviour: a class OUTSIDE the proof's reachable cone changing must NOT perturb the key
     * (the proof keeps its cached green), while a class INSIDE the cone changing MUST. The entry
     * `pkg.C.proof` reaches `pkg/Dep` (a `new pkg/Dep`) but never `pkg/Unrelated`.
     */
    @Test
    fun classOutsideCone_doesNotPerturbKey_butInsideConeDoes(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeEntryReaching(classes, "pkg/C", "proof", "pkg/Dep")
        writeEmptyClass(classes, "pkg/Dep")
        writeEmptyClass(classes, "pkg/Unrelated")
        val r = req(classes.toString())

        val before = VerdictCache.computeKey(r, ENGINE)

        // Touch an UNRELATED class (outside the cone): key must be unchanged.
        rewriteEmptyClass(classes, "pkg/Unrelated", marker = 7)
        bumpMtime(classes.resolve("pkg/Unrelated.class"))
        assertEquals(before, VerdictCache.computeKey(r, ENGINE),
                "a class outside the proof's cone changing must not invalidate the proof's cache")

        // Touch a class INSIDE the cone (Dep): key must change.
        rewriteEmptyClass(classes, "pkg/Dep", marker = 9)
        bumpMtime(classes.resolve("pkg/Dep.class"))
        assertNotEquals(before, VerdictCache.computeKey(r, ENGINE),
                "a class inside the proof's cone changing must invalidate the proof's cache")
    }

    /**
     * The end-to-end soundness/benefit: a stored green is STILL SERVED after an unrelated (out-of-cone)
     * class changes (the benefit), and is NOT served after an in-cone class changes (soundness).
     */
    @Test
    fun storedGreen_survivesOutOfConeChange_butNotInConeChange(@TempDir dir: Path) {
        runIn(dir) {
            val classes = Files.createDirectory(dir.resolve("classes"))
            writeEntryReaching(classes, "pkg/C", "proof", "pkg/Dep")
            writeEmptyClass(classes, "pkg/Dep")
            writeEmptyClass(classes, "pkg/Unrelated")
            val r = req(classes.toString())

            VerdictCache.storeIfVerified(r, ENGINE, JbmcResult(true, listOf(), "raw"))
            assertTrue(VerdictCache.isVerified(r, ENGINE), "freshly stored green hits")

            rewriteEmptyClass(classes, "pkg/Unrelated", marker = 3)
            bumpMtime(classes.resolve("pkg/Unrelated.class"))
            assertTrue(VerdictCache.isVerified(r, ENGINE),
                    "the green survives an out-of-cone class change (the benefit: fewer misses)")

            rewriteEmptyClass(classes, "pkg/Dep", marker = 5)
            bumpMtime(classes.resolve("pkg/Dep.class"))
            assertFalse(VerdictCache.isVerified(r, ENGINE),
                    "the green is NOT served after an in-cone class changes (soundness: never stale)")
        }
    }

    /**
     * Soundness fallback: when the cone can't be bounded (here, the entry class isn't on the
     * classpath), the cone digest must fall back to the WHOLE-classpath digest — so ANY class change,
     * in or out of any cone, invalidates. Over-invalidation is always acceptable.
     */
    @Test
    fun unboundableCone_fallsBackToWholeClasspath(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeEmptyClass(classes, "pkg/Other")
        // Entry "pkg.C" is NOT written to the classpath -> cone can't be rooted -> whole-classpath fallback.
        val r = req(classes.toString())
        val before = VerdictCache.computeKey(r, ENGINE)
        rewriteEmptyClass(classes, "pkg/Other", marker = 2)
        bumpMtime(classes.resolve("pkg/Other.class"))
        assertNotEquals(before, VerdictCache.computeKey(r, ENGINE),
                "with an unbounded cone, any class change must invalidate (coarse fallback)")
    }

    @Test
    fun coneContentDigest_resolvedAndWhole_neverCollide(@TempDir dir: Path) {
        // A resolved cone reaching every class must not hash-collide with the whole-classpath fallback,
        // even when the underlying byte sets coincide — the mode is tagged into each digest.
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeEntryReaching(classes, "pkg/C", "proof", "pkg/Dep")
        writeEmptyClass(classes, "pkg/Dep")
        val scoped = VerdictCache.coneContentDigest(classes.toString(), "pkg.C")
        val whole = VerdictCache.coneContentDigest(classes.toString(), "does.not.Exist")
        assertNotEquals(scoped, whole, "a scoped cone digest must never alias the whole-classpath fallback")
    }

    // --- computeKey memoization (a cache hit must be cheap) ---------------------

    /**
     * computeKey runs for every proof, so its expensive inputs (classpath digest + config call-site
     * scan) are memoized behind a (path, size, mtime) fingerprint. Unchanged files must not re-digest;
     * a content change must still invalidate THROUGH the memo (over-invalidation stays fine,
     * under-invalidation never is).
     */
    @Test
    fun computeKey_skipsRecomputingDigests_whenFilesUnchanged(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        val clazz = classes.resolve("A.class")
        Files.write(clazz, byteArrayOf(1, 2, 3))
        val r = req(classes.toString())

        val k1 = VerdictCache.computeKey(r, ENGINE) // first call computes (and memoizes)
        val afterFirst = VerdictCache.MEMO_RECOMPUTES.get()
        val k2 = VerdictCache.computeKey(r, ENGINE)
        assertEquals(k1, k2)
        assertEquals(afterFirst, VerdictCache.MEMO_RECOMPUTES.get(),
                "unchanged files must not re-digest/re-scan the classpath (the cost of a cache hit)")

        Files.write(clazz, byteArrayOf(1, 2, 3, 4)) // content (and size) changes
        val k3 = VerdictCache.computeKey(r, ENGINE)
        assertNotEquals(k1, k3, "a changed class must still invalidate through the memo")
        assertTrue(VerdictCache.MEMO_RECOMPUTES.get() > afterFirst,
                "a changed fingerprint must force a fresh compute")
    }

    // --- Resolved-config folding (config-pinning false-green fix) --------------

    /**
     * ConfigBytecode bakes the REAL System.getProperty("KEY") value into the analysed bytecode at
     * analysis time, but the app .class files don't change when the property changes — so the resolved
     * config value must be folded into the key, or a config-pinned proof keeps its cached green after the
     * value flips to one that violates the property (the exact false green this fix closes).
     */
    @Test
    fun configValue_perturbsKey(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // A class that calls Bmc.intFromProperty("test.cfg.port") with a LITERAL key — exactly the
        // call site ConfigBytecode resolves and bakes.
        Files.write(classes.resolve("Cfg.class"),
                configReaderClass("Cfg", "intFromProperty", "(Ljava/lang/String;)I", "test.cfg.port"))
        val r = req(classes.toString())

        val prev = System.getProperty("test.cfg.port")
        try {
            System.setProperty("test.cfg.port", "8080")
            val atX = VerdictCache.computeKey(r, ENGINE)
            val stable = VerdictCache.computeKey(r, ENGINE)
            assertEquals(atX, stable, "unchanged config value must hash identically")

            System.setProperty("test.cfg.port", "70000") // a value that would violate a range proof
            val atY = VerdictCache.computeKey(r, ENGINE)
            assertNotEquals(atX, atY, "changing a referenced config value must change the key")

            System.clearProperty("test.cfg.port") // toggling presence must also invalidate
            val unset = VerdictCache.computeKey(r, ENGINE)
            assertNotEquals(atY, unset, "unsetting a referenced config value must change the key")
        } finally {
            if (prev == null) {
                System.clearProperty("test.cfg.port")
            } else {
                System.setProperty("test.cfg.port", prev)
            }
        }
    }

    /** A class with NO config call sites must NOT be perturbed by an unrelated property change — the
     *  config fold is scoped to the keys a proof actually references, so non-config proofs keep caching. */
    @Test
    fun unreferencedConfigValue_doesNotPerturbKey(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        Files.write(classes.resolve("Plain.class"), plainClass("Plain"))
        val r = req(classes.toString())

        val prev = System.getProperty("test.cfg.unreferenced")
        try {
            System.clearProperty("test.cfg.unreferenced")
            val before = VerdictCache.computeKey(r, ENGINE)
            System.setProperty("test.cfg.unreferenced", "changed")
            val after = VerdictCache.computeKey(r, ENGINE)
            assertEquals(before, after,
                    "a config value the proof does not read must not perturb its key (non-config proofs still cache)")
        } finally {
            if (prev == null) {
                System.clearProperty("test.cfg.unreferenced")
            } else {
                System.setProperty("test.cfg.unreferenced", prev)
            }
        }
    }

    /**
     * The actual soundness hole: a config-reading proof that round-trips VERIFIED under value X must NOT
     * be served that stored green under a value Y that violates the property. With value folded into the
     * key, value Y computes a different key → cache MISS → the engine re-runs (and would see Y).
     */
    @Test
    fun configReadingProof_doesNotServeStaleGreen_afterValueChanges(@TempDir dir: Path) {
        runIn(dir) {
            val classes = Files.createDirectory(dir.resolve("classes"))
            Files.write(classes.resolve("Cfg.class"),
                    configReaderClass("Cfg", "intFromProperty", "(Ljava/lang/String;)I", "test.cfg.port"))
            val r = req(classes.toString())

            val prev = System.getProperty("test.cfg.port")
            try {
                // Value X: proof verifies, green is cached.
                System.setProperty("test.cfg.port", "8080")
                VerdictCache.storeIfVerified(r, ENGINE, JbmcResult(true, listOf(), "raw"))
                assertTrue(VerdictCache.isVerified(r, ENGINE), "verified under X is cached and re-served under X")

                // Value Y (would violate the property): MUST NOT serve the stale green from X.
                System.setProperty("test.cfg.port", "70000")
                assertFalse(VerdictCache.isVerified(r, ENGINE),
                        "a stale green from value X must NOT be served under a violating value Y — it re-runs")
            } finally {
                if (prev == null) {
                    System.clearProperty("test.cfg.port")
                } else {
                    System.setProperty("test.cfg.port", prev)
                }
            }
        }
    }

    // --- Read/write round-trip -------------------------------------------------

    @Test
    fun verifiedResult_roundTrips_throughCache(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            assertFalse(VerdictCache.isVerified(r, ENGINE), "cold cache: miss")
            VerdictCache.storeIfVerified(r, ENGINE, JbmcResult(true, listOf(), "raw"))
            assertTrue(VerdictCache.isVerified(r, ENGINE), "after storing a green: hit")
        }
    }

    @Test
    fun verifiedResult_storesAndReturnsStubList(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val green = JbmcResult(true, listOf(), "raw")
                    .withStubbedMethods(listOf("java.util.Formatter.format", "java.util.List.stream"))
            VerdictCache.storeIfVerified(r, ENGINE, green)
            val hit = VerdictCache.lookupVerified(r, ENGINE)
            assertTrue(hit != null, "stored green is a hit")
            assertEquals(listOf("java.util.Formatter.format", "java.util.List.stream"),
                    hit!!.stubbedMethods,
                    "the harvested stub fact round-trips through the cache")
        }
    }

    @Test
    fun rejudge_fromStoredStubs_withoutEngineRerun(@TempDir dir: Path) {
        // The KEY is unchanged by stub policy (allowStubs/strictStubs aren't request inputs), so the
        // SAME request hits the cache and the stored stub list is re-judged at read time — no re-run.
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            VerdictCache.storeIfVerified(r, ENGINE, JbmcResult(true, listOf(), "raw")
                    .withStubbedMethods(listOf("java.util.Formatter.format")))
            val hit = VerdictCache.lookupVerified(r, ENGINE)
            assertTrue(hit != null)
            // strict + no allowlist -> unacknowledged
            val strictNoAllow = StubPolicy.judge(hit!!.stubbedMethods, listOf(), "")
            assertTrue(strictNoAllow.hasUnacknowledged())
            // edit allowStubs (no re-run, same cache) -> acknowledged
            val allowed = StubPolicy.judge(hit.stubbedMethods,
                    listOf("java.util.Formatter.*"), "")
            assertFalse(allowed.hasUnacknowledged())
        }
    }

    // --- Never-cache-reds rule -------------------------------------------------

    @Test
    fun refutedResult_isNeverCached(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val refuted = JbmcResult(false, listOf(
                    JbmcResult.Violation("boom", "C.java", 1, listOf(), listOf())), "raw")
            VerdictCache.storeIfVerified(r, ENGINE, refuted)
            assertFalse(VerdictCache.isVerified(r, ENGINE), "REFUTED must never be cached")
        }
    }

    @Test
    fun unknownResult_isNeverCached(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            VerdictCache.storeIfVerified(r, ENGINE,
                    JbmcResult.unknown(UnknownKind.TIMEOUT, "timed out", "raw"))
            assertFalse(VerdictCache.isVerified(r, ENGINE), "UNKNOWN must never be cached")
        }
    }

    @Test
    fun vacuousResult_isNeverCached(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val vacuous = JbmcResult(false, listOf(
                    JbmcResult.Violation(BmcReachability.VACUOUS_MESSAGE, null, 0, listOf(), listOf())),
                    "raw", true)
            VerdictCache.storeIfVerified(r, ENGINE, vacuous)
            assertFalse(VerdictCache.isVerified(r, ENGINE), "VACUOUS (a flavour of refuted) must never be cached")
        }
    }

    // --- Expected-match caching (fail-on-purpose demo passes) -------------------

    @Test
    fun refutedResult_withMatchingExpectation_roundTrips(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val refuted = JbmcResult(false, listOf(
                    JbmcResult.Violation("boom", "C.java", 1, listOf(), listOf())), "raw")
            VerdictCache.storeIfExpectedMatch(r, ENGINE, refuted, org.bmc4j.Verdict.REFUTED)
            val hit = VerdictCache.lookup(r, ENGINE)
            assertTrue(hit != null, "an expectation-matching REFUTED demo pass is cached")
            assertEquals(org.bmc4j.Verdict.REFUTED, hit!!.verdict, "the stored FACT is the verdict itself")
            assertTrue(hit.stubbedMethods.isEmpty(), "non-VERIFIED entries carry no stub fact")
            assertFalse(VerdictCache.isVerified(r, ENGINE),
                    "a stored REFUTED must never read back as a verified hit")
        }
    }

    @Test
    fun vacuousResult_withMatchingExpectation_roundTrips(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val vacuous = JbmcResult(false, listOf(
                    JbmcResult.Violation(BmcReachability.VACUOUS_MESSAGE, null, 0, listOf(), listOf())),
                    "raw", true)
            VerdictCache.storeIfExpectedMatch(r, ENGINE, vacuous, org.bmc4j.Verdict.VACUOUS)
            val hit = VerdictCache.lookup(r, ENGINE)
            assertTrue(hit != null, "an expectation-matching VACUOUS demo pass is cached")
            assertEquals(org.bmc4j.Verdict.VACUOUS, hit!!.verdict)
            assertFalse(VerdictCache.isVerified(r, ENGINE))
        }
    }

    @Test
    fun expectationMismatch_isNeverCached(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            // A REFUTED result offered against expect=VERIFIED: a FAILURE — never cached.
            val refuted = JbmcResult(false, listOf(
                    JbmcResult.Violation("boom", "C.java", 1, listOf(), listOf())), "raw")
            VerdictCache.storeIfExpectedMatch(r, ENGINE, refuted, org.bmc4j.Verdict.VERIFIED)
            assertTrue(VerdictCache.lookup(r, ENGINE) == null,
                    "a failure (actual != expected) must never be cached — mismatches always re-run live")
            // The reverse failure: a VERIFIED result offered against expect=REFUTED (drift) — never cached.
            VerdictCache.storeIfExpectedMatch(r, ENGINE, JbmcResult(true, listOf(), "raw"),
                    org.bmc4j.Verdict.REFUTED)
            assertTrue(VerdictCache.lookup(r, ENGINE) == null,
                    "a green offered against a fail-on-purpose expectation (the dangerous drift) must never be cached")
        }
    }

    @Test
    fun timeoutResult_isNeverCached_evenWhenExpected(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            VerdictCache.storeIfExpectedMatch(r, ENGINE,
                    JbmcResult.unknownTimeout("timed out after 1s", "raw"), org.bmc4j.Verdict.TIMEOUT)
            assertTrue(VerdictCache.lookup(r, ENGINE) == null,
                    "TIMEOUT is a function of machine speed, not of the inputs — never cached, even expected")
        }
    }

    @Test
    fun unknownResult_isNeverCached_evenWhenExpected(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            VerdictCache.storeIfExpectedMatch(r, ENGINE,
                    JbmcResult.unknown(UnknownKind.SOLVER_GAVE_UP, "solver fell over", "raw"),
                    org.bmc4j.Verdict.UNKNOWN)
            assertTrue(VerdictCache.lookup(r, ENGINE) == null,
                    "UNKNOWN is undecided, not a deterministic fact — never cached, even expected")
        }
    }

    @Test
    fun legacyVerifiedEntry_readsBack_asVerifiedHit(@TempDir dir: Path) {
        // Entries written before expected-match caching start "VERIFIED <entry>" with no other change —
        // the generalized lookup must keep serving them (no cache flag-day on upgrade).
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val key = VerdictCache.computeKey(r, ENGINE)
            val entry = Path.of(System.getProperty("user.dir"), "build", "bmc4j", "verdict-cache", key)
            Files.createDirectories(entry.parent)
            Files.write(entry, ("VERIFIED pkg.C.proof\nSTUB java.util.Formatter.format\n")
                    .toByteArray(StandardCharsets.UTF_8))
            val hit = VerdictCache.lookupVerified(r, ENGINE)
            assertTrue(hit != null, "a pre-existing VERIFIED entry still hits after the format generalization")
            assertEquals(org.bmc4j.Verdict.VERIFIED, hit!!.verdict)
            assertEquals(listOf("java.util.Formatter.format"), hit.stubbedMethods)
        }
    }

    // --- Bypass + fail-open ----------------------------------------------------

    @Test
    fun noCacheProp_disablesReadAndWrite(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            // Store while enabled, then a noCache read must MISS (forces re-verification).
            VerdictCache.storeIfVerified(r, ENGINE, JbmcResult(true, listOf(), "raw"))
            System.setProperty("bmc.noCache", "true")
            assertTrue(VerdictCache.disabled())
            assertFalse(VerdictCache.isVerified(r, ENGINE), "-Dbmc.noCache=true must force a miss")
            // And a write while disabled must not persist.
            val r2 = BmcRequest("pkg.C", "pkg.C.other", dir.resolve("classes").toString(),
                    16, true, 16, "", 0)
            VerdictCache.storeIfVerified(r2, ENGINE, JbmcResult(true, listOf(), "raw"))
            System.clearProperty("bmc.noCache")
            assertFalse(VerdictCache.isVerified(r2, ENGINE), "no write happens while disabled")
        }
    }

    @Test
    fun garbageCacheEntry_isIgnored_failOpen(@TempDir dir: Path) {
        runIn(dir) {
            val r = req(dir.resolve("classes").toString())
            val key = VerdictCache.computeKey(r, ENGINE)
            // Mirror VerdictCache.cacheDir(): build/bmc4j/verdict-cache under the (redirected) user.dir.
            val entry = Path.of(System.getProperty("user.dir"), "build", "bmc4j", "verdict-cache", key)
            Files.createDirectories(entry.parent)
            Files.write(entry, "GARBAGE    not a verdict".toByteArray(StandardCharsets.UTF_8))
            assertFalse(VerdictCache.isVerified(r, ENGINE),
                    "a scribbled cache entry must be treated as a miss (fail-open), not a hit")
        }
    }

    companion object {
        private const val ENGINE = "jbmc-bundled:cbmc-6.9.0"

        private fun req(classpath: String): BmcRequest {
            return BmcRequest("pkg.C", "pkg.C.proof", classpath,
                    16, true, 16, "", 0)
        }

        private fun baseReq(): BmcRequest {
            return req("/some/classes")
        }

        /** Write a single-entry jar at `jar`, replacing any existing file. */
        private fun writeJar(jar: Path, entryName: String, content: ByteArray) {
            ZipOutputStream(Files.newOutputStream(jar)).use { zout ->
                zout.putNextEntry(ZipEntry(entryName))
                zout.write(content)
                zout.closeEntry()
            }
        }

        /**
         * Bytecode for a class `name` with a method `m()` that invokes
         * `Bmc.<method><desc>("key")` with a LITERAL key — exactly the config call site
         * [ConfigBytecode] resolves and bakes (and `VerdictCache` scans for the cache key).
         */
        private fun configReaderClass(name: String, method: String, desc: String, key: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null,
                    "java/lang/Object", null)
            val mv = cw.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "m", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn(key)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", method, desc, false)
            // Discard whatever the reader "returns" (we only care that the call site exists) and return.
            if (desc.endsWith("J") || desc.endsWith("D")) {
                mv.visitInsn(Opcodes.POP2)
            } else {
                mv.visitInsn(Opcodes.POP)
            }
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** Write `internalName`'s bytecode under `dir` (creating package subdirs). */
        private fun writeClassFile(dir: Path, internalName: String, bytes: ByteArray) {
            val f = dir.resolve("$internalName.class")
            Files.createDirectories(f.parent)
            Files.write(f, bytes)
        }

        /** A class `entryInternal` with a method `method()` that does `new dep` — so `method` reaches
         *  `dep` in the cone (and nothing else). */
        private fun writeEntryReaching(dir: Path, entryInternal: String, method: String, dep: String) {
            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, entryInternal, null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, method, "()V", null, null)
            mv.visitCode()
            mv.visitTypeInsn(Opcodes.NEW, dep)
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            writeClassFile(dir, entryInternal, cw.toByteArray())
        }

        /** An empty class with the given internal name. */
        private fun writeEmptyClass(dir: Path, internalName: String) {
            writeClassFile(dir, internalName, emptyClassBytes(internalName, 0))
        }

        /** Rewrite an empty class with a [marker] field count so its bytes (and content digest) change. */
        private fun rewriteEmptyClass(dir: Path, internalName: String, marker: Int) {
            writeClassFile(dir, internalName, emptyClassBytes(internalName, marker))
        }

        /** Empty-class bytes; [extraFields] dummy int fields make the content vary deterministically. */
        private fun emptyClassBytes(internalName: String, extraFields: Int): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
            for (i in 0 until extraFields) {
                cw.visitField(Opcodes.ACC_PRIVATE, "f$i", "I", null, null).visitEnd()
            }
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** Move a file's mtime forward so the digest memo's (path, size, mtime) fingerprint changes
         *  even if an in-place rewrite happened to keep the same size on a coarse clock. */
        private fun bumpMtime(file: Path) {
            Files.setLastModifiedTime(file, FileTime.fromMillis(
                    Files.getLastModifiedTime(file).toMillis() + 2_000))
        }

        /** Bytecode for a class with NO config call sites (an empty static method). */
        private fun plainClass(name: String): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null,
                    "java/lang/Object", null)
            val mv = cw.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "m", "()V", null, null)
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** Run `body` with the process working directory temporarily pointed at `dir`. */
        private fun runIn(dir: Path, body: () -> Unit) {
            val prev = System.getProperty("user.dir")
            // VerdictCache resolves build/bmc4j/verdict-cache relative to the CWD via Path.of(relative),
            // which the JVM resolves against user.dir at access time — so overriding it redirects the cache.
            System.setProperty("user.dir", dir.toAbsolutePath().toString())
            try {
                body()
            } finally {
                System.setProperty("user.dir", prev)
            }
        }
    }
}
