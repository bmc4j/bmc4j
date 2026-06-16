package org.bmc4j.junit

import org.bmc4j.BmcProof
import org.bmc4j.LoopUnwind
import org.bmc4j.engine.JbmcResult
import org.bmc4j.engine.SmartUnwind
import org.bmc4j.engine.UnknownKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Unit tests for the user-facing `@LoopUnwind` per-loop PIN. Two halves:
 *  1. Annotation reading: a proof method's `@LoopUnwind`s become a `<loopId> -> bound` map seeded onto
 *     the request's [org.bmc4j.engine.BmcRequest.unwindSet], so the pin reaches the engine on every
 *     path. Repeatable, clamps a non-positive bound, ignores a blank id, last-wins on a duplicate.
 *  2. Soundness/precedence: a pinned loop is FIXED — the smart-unwind climb must NEVER raise it (only
 *     the unpinned firing loops climb), and a pin set too LOW fails closed to UNKNOWN, never a false
 *     VERIFIED (the global `--unwinding-assertions` stays on).
 *
 * The fixtures below are plain `@Disabled` nested classes so JUnit never runs their `@BmcProof`
 * methods — they exist only as reflection targets (the same pattern as [BmcProofExtensionTest]).
 */
internal class LoopUnwindTest {

    // --- Fixtures: reflection-only targets, never executed by JUnit -----------

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class PinnedProofs {
        @BmcProof
        fun noPins() {}

        @BmcProof
        @LoopUnwind(loop = "java::pkg.C.scan:()V.0", bound = 8)
        fun onePin() {}

        @BmcProof
        @LoopUnwind(loop = "java::pkg.C.scan:()V.0", bound = 8)
        @LoopUnwind(loop = "java::pkg.C.scan:()V.1", bound = 4)
        fun twoPins() {}

        @BmcProof
        @LoopUnwind(loop = "  java::pkg.C.m:()V.0  ", bound = 0)
        fun clampedAndTrimmed() {}

        @BmcProof
        @LoopUnwind(loop = "   ", bound = 9)
        fun blankId() {}

        @BmcProof
        @LoopUnwind(loop = "java::pkg.C.m:()V.0", bound = 3)
        @LoopUnwind(loop = "java::pkg.C.m:()V.0", bound = 7)
        fun duplicateLastWins() {}
    }

    private fun method(name: String): Method =
            PinnedProofs::class.java.getDeclaredMethod(name)

    // --- Annotation reading ---------------------------------------------------

    @Test
    fun no_loop_unwind_reads_an_empty_pin_map() {
        assertTrue(BmcProofExtension.resolvePinnedLoops(method("noPins")).isEmpty(),
                "a proof with no @LoopUnwind must read an empty pin map (keys identically to none)")
    }

    @Test
    fun a_single_pin_reads_its_loop_and_bound() {
        assertEquals(mapOf("java::pkg.C.scan:()V.0" to 8),
                BmcProofExtension.resolvePinnedLoops(method("onePin")))
    }

    @Test
    fun repeated_loop_unwind_reads_every_pin() {
        // The repeatable container is unwrapped by getAnnotationsByType, so two stacked @LoopUnwind read
        // as two entries.
        assertEquals(mapOf("java::pkg.C.scan:()V.0" to 8, "java::pkg.C.scan:()V.1" to 4),
                BmcProofExtension.resolvePinnedLoops(method("twoPins")))
    }

    @Test
    fun a_nonpositive_bound_clamps_to_one_and_the_id_is_trimmed() {
        assertEquals(mapOf("java::pkg.C.m:()V.0" to 1),
                BmcProofExtension.resolvePinnedLoops(method("clampedAndTrimmed")),
                "bound <= 0 clamps to 1 (an unwind bound is at least one iteration); the id is trimmed")
    }

    @Test
    fun a_blank_loop_id_is_ignored() {
        assertTrue(BmcProofExtension.resolvePinnedLoops(method("blankId")).isEmpty(),
                "a blank loop id must never become an empty --unwindset arg")
    }

    @Test
    fun a_duplicate_loop_id_takes_the_last_pin() {
        assertEquals(mapOf("java::pkg.C.m:()V.0" to 7),
                BmcProofExtension.resolvePinnedLoops(method("duplicateLastWins")),
                "the last @LoopUnwind for a loop wins, deterministically")
    }

    @Test
    fun pins_seed_the_request_unwindset() {
        // requestFor must put the pins on the request's unwindSet so they reach the engine as
        // --unwindset args on every path (explicit / AUTO / smart / cached).
        val pins = mapOf("java::pkg.C.scan:()V.0" to 8, "java::pkg.C.scan:()V.1" to 4)
        val proof: BmcProof = method("twoPins").getAnnotation(BmcProof::class.java)
        val request = BmcProofExtension.requestFor(
                "pkg.C", "pkg.C.twoPins", proof, pinnedLoops = pins)
        assertEquals(pins, request.unwindSet, "the @LoopUnwind pins must seed the request's unwindSet")
    }

    @Test
    fun no_pins_keys_identically_to_no_annotation() {
        val proof: BmcProof = method("noPins").getAnnotation(BmcProof::class.java)
        val withDefault = BmcProofExtension.requestFor("pkg.C", "pkg.C.noPins", proof)
        val withEmpty = BmcProofExtension.requestFor(
                "pkg.C", "pkg.C.noPins", proof, pinnedLoops = emptyMap())
        assertTrue(withDefault.unwindSet.isEmpty() && withEmpty.unwindSet.isEmpty(),
                "an unpinned proof carries an empty unwindSet, keying identically to no annotation")
    }

    // --- Soundness / precedence: a pin is FIXED, never raised by the climb ----

    /** An UNWINDING_ASSERTION result whose firing loops carry the given engine loop ids. */
    private fun tooSmallAt(vararg loopIds: String): JbmcResult =
            JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, "bound too small", null)
                    .withUnwindingLoops(loopIds.map { id ->
                        JbmcResult.UnwindingLoop("pkg.Cls.m", "Cls.java", 7, recursion = false, loopId = id)
                    })

    private fun verified() = JbmcResult(true, emptyList(), null)

    @Test
    fun a_pinned_loop_is_never_raised_by_the_smart_climb() {
        // The pinned loop "p" fires at the same time as the unpinned loop "q". The climb must raise ONLY
        // q; p must stay at its FIXED pinned bound (3), never bumped by the climb.
        val seen = mutableListOf<Map<String, Int>>()
        val out = SmartUnwind.climb(seedBase = 2, cap = 16, pinned = mapOf("p" to 3)) { _, us ->
            seen.add(us.toSortedMap())
            // q under-bounds until its own bound reaches 4; p fires every round but must never be raised.
            if ((us["q"] ?: 2) >= 4) verified() else tooSmallAt("p", "q")
        }
        assertTrue(out.result.isVerified)
        assertTrue(out.discovered)
        // p stays pinned at 3 throughout; only q was discovered/raised (to 4).
        assertEquals(3, out.unwindSet["p"], "a pinned loop's bound is FIXED — the climb never raises it")
        assertEquals(4, out.unwindSet["q"], "the unpinned firing loop is the only one raised")
        // The pin rode every round verbatim (round 1 already had p=3), q climbed 2 -> 4.
        assertEquals(listOf(mapOf("p" to 3), mapOf("p" to 3, "q" to 4)), seen)
    }

    @Test
    fun a_pin_set_too_low_fails_closed_to_unknown_not_a_false_verified() {
        // The ONLY firing loop is the pinned one (set too low). Since a pin is never raised and no other
        // loop or the global base can grow, the climb must STOP on the UNKNOWN — never a false VERIFIED.
        var calls = 0
        val out = SmartUnwind.climb(seedBase = 4, cap = 16, pinned = mapOf("p" to 1)) { _, us ->
            calls++
            // p (pinned at 1) fires forever; if the climb wrongly raised it the result would flip.
            if ((us["p"] ?: 1) >= 2) verified() else tooSmallAt("p")
        }
        assertTrue(out.result.isUnknown, "a pin too low must yield UNKNOWN (unwinding assertion), not VERIFIED")
        assertFalse(out.discovered)
        assertEquals(UnknownKind.UNWINDING_ASSERTION, out.result.undecidedKind)
        assertEquals(1, out.unwindSet["p"], "the pin stayed FIXED at its too-low bound")
        assertEquals(1, calls, "no progress is possible, so the climb stops after the first round")
    }

    @Test
    fun pins_ride_every_round_even_when_other_loops_climb() {
        // A pin must be present from round ONE and on every subsequent round, alongside whatever the
        // climb discovers, so the engine always runs the pinned loop at the user's bound.
        val seen = mutableListOf<Map<String, Int>>()
        SmartUnwind.climb(seedBase = 1, cap = 8, pinned = mapOf("p" to 5)) { _, us ->
            seen.add(us.toSortedMap())
            val q = us["q"] ?: 1
            if (q >= 2) verified() else tooSmallAt("q")
        }
        assertTrue(seen.all { it["p"] == 5 }, "the pin rides every round at its fixed bound: $seen")
    }
}
