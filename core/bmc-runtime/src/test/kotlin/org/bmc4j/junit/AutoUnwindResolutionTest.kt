package org.bmc4j.junit

import org.bmc4j.BmcProof
import org.bmc4j.engine.BmcRequest
import org.bmc4j.engine.JbmcResult
import org.bmc4j.engine.UnknownKind
import org.bmc4j.engine.UnwindCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Resolution + wiring tests for automatic unwind discovery (the AUTO default and its opt-out), plus the
 * discovered-bound [UnwindCache] round-trip. These exercise the bound-RESOLUTION layer (no engine): that
 * absence-of-bound resolves to the AUTO sentinel, a positive bound pins (opt-out unchanged), `0` and
 * `-Dbmc.unwind` pin the build default, and the discovered-bound cache stores/serves a bound keyed by
 * proof identity. The end-to-end climb + zero-extra-solves-on-cache behaviour is proven by the
 * `proofs.loopsunwinding` example proofs against the real engine.
 */
internal class AutoUnwindResolutionTest {

    @AfterEach
    fun clearProps() {
        System.clearProperty("bmc.unwind")
        System.clearProperty("bmc.unwindCap")
        System.clearProperty("bmc.unwindSeed")
    }

    // --- bound resolution: AUTO default vs the opt-out --------------------------

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class UnwindProofs {
        @BmcProof
        fun autoByDefault() {}

        @BmcProof(unwind = BmcProof.AUTO)
        fun explicitAuto() {}

        @BmcProof(unwind = 7)
        fun pinnedSeven() {}

        @BmcProof(unwind = 0)
        fun pinnedBuildDefault() {}
    }

    @Test
    fun noExplicitBound_resolvesToAutoSentinel() {
        System.clearProperty("bmc.unwind")
        assertEquals(BmcProof.AUTO,
                BmcProofExtension.resolveUnwind(annotationOn("autoByDefault")),
                "no explicit unwind => AUTO (auto-discover)")
        assertEquals(BmcProof.AUTO,
                BmcProofExtension.resolveUnwind(annotationOn("explicitAuto")),
                "@BmcProof(unwind = AUTO) is explicit auto-discovery")
        // A null config (a proof method with no @BmcProof at all, e.g. a plain JUnit @Test routed here)
        // also defaults to AUTO.
        assertEquals(BmcProof.AUTO, BmcProofExtension.resolveUnwind(null))
    }

    @Test
    fun positiveBound_pinsIt_optOutUnchanged() {
        System.clearProperty("bmc.unwind")
        assertEquals(7, BmcProofExtension.resolveUnwind(annotationOn("pinnedSeven")),
                "an explicit positive bound pins (the expert opt-out)")
    }

    @Test
    fun zeroBound_pinsBuildCap() {
        System.clearProperty("bmc.unwind")
        System.clearProperty("bmc.unwindCap")
        assertEquals(16, BmcProofExtension.resolveUnwind(annotationOn("pinnedBuildDefault")),
                "unwind = 0 pins the build cap (legacy explicit-default)")
        System.setProperty("bmc.unwindCap", "9")
        assertEquals(9, BmcProofExtension.resolveUnwind(annotationOn("pinnedBuildDefault")),
                "unwind = 0 honors the build cap")
    }

    @Test
    fun buildWideUnwindPin_winsOverAuto() {
        // A project can still pin a fixed bound for every proof via -Dbmc.unwind, overriding AUTO.
        System.setProperty("bmc.unwind", "5")
        assertEquals(5, BmcProofExtension.resolveUnwind(annotationOn("autoByDefault")),
                "a positive -Dbmc.unwind pins a project-wide bound, overriding AUTO")
    }

    @Test
    fun buildWideUnwindSentinel_or_zero_keepsAuto() {
        // The plugin default-forwards the AUTO sentinel (-1); that must NOT be read as a pin.
        System.setProperty("bmc.unwind", "-1")
        assertEquals(BmcProof.AUTO, BmcProofExtension.resolveUnwind(annotationOn("autoByDefault")),
                "a -1 (AUTO) -Dbmc.unwind keeps the proof on auto-discovery")
    }

    @Test
    fun autoUnwindCap_isTheBuildCap_separateFromTheUnwindPin() {
        System.clearProperty("bmc.unwind")
        System.clearProperty("bmc.unwindCap")
        assertEquals(16, BmcProofExtension.autoUnwindCap())
        // A positive -Dbmc.unwind is a PIN, not a cap — it must NOT raise the climb cap.
        System.setProperty("bmc.unwind", "24")
        assertEquals(16, BmcProofExtension.autoUnwindCap(), "the unwind PIN does not change the cap")
        System.setProperty("bmc.unwindCap", "32")
        assertEquals(32, BmcProofExtension.autoUnwindCap(), "the cap is its own -Dbmc.unwindCap knob")
    }

    @Test
    fun pinUnwind_setsTheBound_keepsEverythingElse() {
        val req = BmcRequest("Pkg.C", "Pkg.C.m", "cp", BmcProof.AUTO, true, 16, "z3", 30)
        val pinned = BmcProofExtension.pinUnwind(req, 4)
        assertEquals(4, pinned.unwind)
        assertEquals("Pkg.C", pinned.entryClass)
        assertEquals("cp", pinned.classpath)
        assertEquals(16, pinned.maxStringLength)
        assertEquals("z3", pinned.solver)
        assertEquals(30, pinned.timeoutSeconds)
        assertTrue(pinned.unwindingAssertions)
    }

    @Test
    fun isConclusive_distinguishesVerdicts() {
        assertTrue(BmcProofExtension.isConclusive(JbmcResult(true, emptyList(), null)), "VERIFIED")
        assertTrue(BmcProofExtension.isConclusive(JbmcResult(false, emptyList(), null)), "REFUTED")
        assertTrue(BmcProofExtension.isConclusive(JbmcResult(false, emptyList(), null, true)), "VACUOUS")
        assertFalse(BmcProofExtension.isConclusive(
                JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, "too small", null)), "UNKNOWN")
    }

    @Test
    fun autoUnwindDetail_namesTheBoundToPin() {
        val detail = BmcProofExtension.autoUnwindDetail(5)
        assertTrue(detail.contains("unwind=5"), detail)
        assertTrue(detail.contains("@BmcProof(unwind = 5)"), "tells the user exactly what to pin: $detail")
    }

    // --- discovered-bound cache round-trip --------------------------------------

    @Test
    fun unwindCache_storesAndServesByProofIdentity_independentOfTheSearchedBound(@TempDir tmp: Path) {
        withUserDir(tmp) {
            // The AUTO request carries the sentinel unwind; a later lookup with a DIFFERENT unwind on the
            // same proof still resolves to the recorded bound (the key normalizes the bound away).
            val auto = BmcRequest("Pkg.C", "Pkg.C.m", classpathForTest(), BmcProof.AUTO, true, 16)
            assertNull(UnwindCache.lookup(auto, "jbmc"), "cold: no recorded bound")
            UnwindCache.store(auto, "jbmc", 4)
            assertEquals(4, UnwindCache.lookup(auto, "jbmc"), "served back by proof identity")
            // A request that carries a concrete (different) unwind for the same proof gets the same hit —
            // the discovered-bound key is bound-independent.
            val pinnedDifferently = BmcProofExtension.pinUnwind(auto, 2)
            assertEquals(4, UnwindCache.lookup(pinnedDifferently, "jbmc"),
                    "the key ignores the request's own bound")
        }
    }

    @Test
    fun unwindCache_differsByEngineIdentityAndEntry(@TempDir tmp: Path) {
        withUserDir(tmp) {
            val a = BmcRequest("Pkg.C", "Pkg.C.m", classpathForTest(), BmcProof.AUTO, true, 16)
            val b = BmcRequest("Pkg.C", "Pkg.C.other", classpathForTest(), BmcProof.AUTO, true, 16)
            UnwindCache.store(a, "jbmc", 4)
            assertNull(UnwindCache.lookup(a, "esbmc"), "a different engine identity is a different key")
            assertNull(UnwindCache.lookup(b, "jbmc"), "a different entry function is a different key")
        }
    }

    @Test
    fun unwindCache_failsOpen_onDisabledCache(@TempDir tmp: Path) {
        withUserDir(tmp) {
            val prev = System.getProperty("bmc.noCache")
            try {
                System.setProperty("bmc.noCache", "true")
                val req = BmcRequest("Pkg.C", "Pkg.C.m", classpathForTest(), BmcProof.AUTO, true, 16)
                UnwindCache.store(req, "jbmc", 4)
                assertNull(UnwindCache.lookup(req, "jbmc"), "disabled cache: never stores or serves")
            } finally {
                if (prev == null) System.clearProperty("bmc.noCache")
                else System.setProperty("bmc.noCache", prev)
            }
        }
    }

    // --- helpers ----------------------------------------------------------------

    /** Run [body] with `user.dir` redirected to [tmp] so the cache writes under a temp dir. */
    private fun withUserDir(tmp: Path, body: () -> Unit) {
        val prev = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", tmp.toString())
            body()
        } finally {
            if (prev == null) System.clearProperty("user.dir") else System.setProperty("user.dir", prev)
        }
    }

    /** A non-blank classpath so the cache key's content digest has something stable to hash. */
    private fun classpathForTest(): String = System.getProperty("java.class.path")

    private fun annotationOn(method: String): BmcProof =
            UnwindProofs::class.java.getDeclaredMethod(method).getAnnotation(BmcProof::class.java)
}
