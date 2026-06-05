package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JbmcResultTest {

    @Test
    void verified_result_carries_no_violations() {
        JbmcResult r = new JbmcResult(true, List.of(), "raw");
        assertTrue(r.isVerified());
        assertTrue(r.violations().isEmpty());
        assertEquals("raw", r.rawOutput());
        assertFalse(r.isVacuous());
    }

    @Test
    void vacuous_result_is_unverified_and_flagged() {
        JbmcResult.Violation v = new JbmcResult.Violation(
                BmcReachability.VACUOUS_MESSAGE, null, 0, List.of(), List.of());
        JbmcResult r = new JbmcResult(false, List.of(v), "raw", true);
        assertFalse(r.isVerified());
        assertTrue(r.isVacuous());
        assertEquals(BmcReachability.VACUOUS_MESSAGE, r.violations().get(0).description());
    }

    @Test
    void unknown_result_is_undecided_not_verified_and_carries_reason_no_violations() {
        // UNKNOWN is a distinct verdict (undecided within budget) — not verified, not a
        // refutation (no counterexample), but still a failure to the caller.
        JbmcResult r = JbmcResult.unknown("timed out after 1s", "raw");
        assertFalse(r.isVerified());
        assertTrue(r.isUnknown());
        assertFalse(r.isVacuous());
        assertTrue(r.violations().isEmpty(), "UNKNOWN has no counterexample");
        assertEquals("timed out after 1s", r.undecidedReason());
        assertEquals(JbmcResult.Verdict.UNKNOWN, r.verdict());
        assertEquals("raw", r.rawOutput());
    }

    @Test
    void verified_and_refuted_map_to_their_verdicts() {
        assertEquals(JbmcResult.Verdict.VERIFIED, new JbmcResult(true, List.of(), "r").verdict());
        assertEquals(JbmcResult.Verdict.REFUTED, new JbmcResult(false, List.of(), "r").verdict());
        assertFalse(new JbmcResult(false, List.of(), "r").isUnknown());
    }

    @Test
    void violation_exposes_all_fields() {
        StackTraceElement frame = new StackTraceElement("pkg.C", "m", "C.java", 7);
        JbmcResult.Violation v = new JbmcResult.Violation(
                "boom", "C.java", 7, List.of(frame), List.of("x = 1"));
        JbmcResult r = new JbmcResult(false, List.of(v), "raw");

        assertFalse(r.isVerified());
        assertEquals(1, r.violations().size());
        assertEquals("boom", v.description());
        assertEquals("C.java", v.file());
        assertEquals(7, v.line());
        assertEquals(List.of(frame), v.stack());
        assertEquals(List.of("x = 1"), v.counterexample());
    }
}
