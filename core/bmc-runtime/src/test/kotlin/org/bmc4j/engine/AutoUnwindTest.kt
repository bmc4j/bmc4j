package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [AutoUnwind] climb — the pure low→high search behind auto-unwind discovery,
 * driven by a stub `runAt` so it needs no engine. Pins the climb's contract: stop at the first
 * conclusive verdict (the minimal covering bound), escalate on the unwinding-too-small signal, never
 * accept a vacuous pass as "the bound works", never mask a higher-bound REFUTED with a lower VERIFIED,
 * and cap with a clear UNKNOWN instead of climbing forever.
 */
internal class AutoUnwindTest {

    private fun verified() = JbmcResult(true, emptyList(), null)
    private fun refuted() = JbmcResult(false, emptyList(), null)
    private fun vacuous() = JbmcResult(false, emptyList(), null, true)
    private fun tooSmall() = JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, "bound too small", null)
    private fun timedOut() = JbmcResult.unknownTimeout("out of time", null)

    @Test
    fun discovers_the_minimal_bound_and_stops() {
        // VERIFIED first appears at bound 4; the climb must land there and run no higher.
        val tried = mutableListOf<Int>()
        val out = AutoUnwind.climb(seed = 1, cap = 16) { b ->
            tried.add(b)
            if (b >= 4) verified() else tooSmall()
        }
        assertTrue(out.result.isVerified)
        assertEquals(4, out.bound, "lands on the minimal covering bound")
        assertTrue(out.discovered)
        assertEquals(listOf(1, 2, 4), tried, "doubles 1->2->4 and stops at the first VERIFIED")
    }

    @Test
    fun under_seed_escalates_and_still_lands_correct() {
        // Seed below the true bound: the climb escalates rather than reporting a false UNKNOWN.
        val tried = mutableListOf<Int>()
        val out = AutoUnwind.climb(seed = 1, cap = 16) { b ->
            tried.add(b)
            if (b >= 8) verified() else tooSmall()
        }
        assertTrue(out.result.isVerified)
        assertEquals(8, out.bound)
        assertEquals(listOf(1, 2, 4, 8), tried)
    }

    @Test
    fun unbounded_loop_caps_with_a_clear_unknown_not_forever() {
        // Every rung hits the unwinding bound: cap at 16 with a clear, non-retryable UNKNOWN.
        val tried = mutableListOf<Int>()
        val out = AutoUnwind.climb(seed = 1, cap = 16) { b -> tried.add(b); tooSmall() }
        assertTrue(out.result.isUnknown)
        assertFalse(out.discovered, "a capped climb is not a discovered bound")
        assertEquals(16, out.bound)
        assertEquals(UnknownKind.UNWINDING_ASSERTION, out.result.undecidedKind)
        assertTrue(out.result.undecidedReason!!.contains("up to unwind=16"),
                "the message names the cap: ${out.result.undecidedReason}")
        assertTrue(out.result.undecidedReason!!.contains("explicit"),
                "the message tells the user to set an explicit bound")
        // Doubling lands exactly on the cap (never overshoots) and stops: 1,2,4,8,16.
        assertEquals(listOf(1, 2, 4, 8, 16), tried)
    }

    @Test
    fun vacuous_pass_is_surfaced_not_accepted_as_the_bound() {
        // A bound-1 pass on unsat assumptions VERIFIES trivially; the climb must surface it as VACUOUS
        // (its own verdict via the reachability check), never accept it as "the bound works".
        val tried = mutableListOf<Int>()
        val out = AutoUnwind.climb(seed = 1, cap = 16) { b -> tried.add(b); vacuous() }
        assertTrue(out.result.isVacuous)
        assertFalse(out.result.isVerified)
        assertEquals(1, out.bound, "vacuity is conclusive at the seed; no climb")
        assertEquals(listOf(1), tried, "stops immediately — never climbs past a vacuous result")
    }

    @Test
    fun higher_bound_refuted_is_not_masked_by_a_lower_verified() {
        // The signal is: a lower bound that is TOO SMALL (unwinding) climbs; it never returns a VERIFIED
        // that could mask a real refutation at a higher bound. Here bound 1-2 are too small, bound 4
        // REFUTES — the climb surfaces the refutation, not a spurious lower pass.
        val out = AutoUnwind.climb(seed = 1, cap = 16) { b ->
            when {
                b >= 4 -> refuted()
                else -> tooSmall()
            }
        }
        assertFalse(out.result.isVerified)
        assertFalse(out.result.isUnknown)
        assertTrue(out.discovered)
        assertEquals(4, out.bound, "the real counterexample at bound 4 wins")
    }

    @Test
    fun a_rung_that_falls_over_stops_with_that_unknown_not_a_climb() {
        // A non-unwinding UNKNOWN (timeout / OOM / parse) at a rung will not be fixed by a higher bound:
        // stop and surface it rather than multiplying the cost up the cap.
        val tried = mutableListOf<Int>()
        val out = AutoUnwind.climb(seed = 1, cap = 16) { b ->
            tried.add(b)
            if (b >= 2) timedOut() else tooSmall()
        }
        assertTrue(out.result.isUnknown)
        assertEquals(UnknownKind.TIMEOUT, out.result.undecidedKind)
        assertFalse(out.discovered)
        assertEquals(listOf(1, 2), tried, "stops at the rung that fell over — no further climb")
    }

    @Test
    fun seed_is_clamped_into_range() {
        // A seed above the cap is clamped to the cap; a seed below 1 is clamped to 1.
        val triedHigh = mutableListOf<Int>()
        AutoUnwind.climb(seed = 99, cap = 8) { b -> triedHigh.add(b); verified() }
        assertEquals(listOf(8), triedHigh, "seed clamped down to the cap")

        val triedLow = mutableListOf<Int>()
        AutoUnwind.climb(seed = 0, cap = 8) { b -> triedLow.add(b); verified() }
        assertEquals(listOf(1), triedLow, "seed clamped up to 1")
    }

    @Test
    fun seed_above_true_bound_is_a_single_rung() {
        // The seed optimization's point: starting at-or-above the answer makes the common case one solve.
        val tried = mutableListOf<Int>()
        val out = AutoUnwind.climb(seed = 8, cap = 16) { b -> tried.add(b); verified() }
        assertEquals(8, out.bound)
        assertEquals(listOf(8), tried, "a good seed is a single rung")
    }
}
