package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class JbmcResultTest {

    @Test
    fun verified_result_carries_no_violations() {
        val r = JbmcResult(true, listOf(), "raw")
        assertTrue(r.isVerified)
        assertTrue(r.violations.isEmpty())
        assertEquals("raw", r.rawOutput)
        assertFalse(r.isVacuous)
    }

    @Test
    fun vacuous_result_is_unverified_and_flagged() {
        val v = JbmcResult.Violation(
                BmcReachability.VACUOUS_MESSAGE, null, 0, listOf(), listOf())
        val r = JbmcResult(false, listOf(v), "raw", true)
        assertFalse(r.isVerified)
        assertTrue(r.isVacuous)
        assertEquals(BmcReachability.VACUOUS_MESSAGE, r.violations[0].description)
    }

    @Test
    fun unknown_result_is_undecided_not_verified_and_carries_reason_no_violations() {
        // UNKNOWN is a distinct verdict (undecided within budget) — not verified, not a
        // refutation (no counterexample), but still a failure to the caller.
        val r = JbmcResult.unknown("timed out after 1s", "raw")
        assertFalse(r.isVerified)
        assertTrue(r.isUnknown)
        assertFalse(r.isVacuous)
        assertTrue(r.violations.isEmpty(), "UNKNOWN has no counterexample")
        assertEquals("timed out after 1s", r.undecidedReason)
        assertEquals(JbmcResult.Verdict.UNKNOWN, r.verdict)
        assertEquals("raw", r.rawOutput)
    }

    @Test
    fun verified_and_refuted_map_to_their_verdicts() {
        assertEquals(JbmcResult.Verdict.VERIFIED, JbmcResult(true, listOf(), "r").verdict)
        assertEquals(JbmcResult.Verdict.REFUTED, JbmcResult(false, listOf(), "r").verdict)
        assertFalse(JbmcResult(false, listOf(), "r").isUnknown)
    }

    @Test
    fun link_failure_stubs_is_a_parallel_fact_attached_without_changing_the_verdict() {
        // withLinkFailureStubs attaches the harvested members and leaves the verdict/violations alone
        // (the demote-to-UNKNOWN policy is BmcProofExtension's job); empty/null is a no-op.
        val base = JbmcResult(false, listOf(), "raw")
        assertTrue(base.linkFailureStubs.isEmpty())
        assertEquals(base, base.withLinkFailureStubs(null))
        assertEquals(base, base.withLinkFailureStubs(listOf()))

        val withStubs = base.withLinkFailureStubs(listOf("kotlin.ranges.RangesKt.coerceAtMost(long, long)"))
        assertEquals(JbmcResult.Verdict.REFUTED, withStubs.verdict, "the verdict is unchanged")
        assertEquals(listOf("kotlin.ranges.RangesKt.coerceAtMost(long, long)"), withStubs.linkFailureStubs)
    }

    @Test
    fun violation_exposes_all_fields() {
        val frame = StackTraceElement("pkg.C", "m", "C.java", 7)
        val v = JbmcResult.Violation(
                "boom", "C.java", 7, listOf(frame), listOf("x = 1"))
        val r = JbmcResult(false, listOf(v), "raw")

        assertFalse(r.isVerified)
        assertEquals(1, r.violations.size)
        assertEquals("boom", v.description)
        assertEquals("C.java", v.file)
        assertEquals(7, v.line)
        assertEquals(listOf(frame), v.stack)
        assertEquals(listOf("x = 1"), v.counterexample)
    }
}
