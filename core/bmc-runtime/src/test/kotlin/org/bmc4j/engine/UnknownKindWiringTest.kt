package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Proves the soundness invariant at the [JbmcResult] boundary: NO bare, kindless, or
 * reasonless UNKNOWN can be constructed, and each engine-layer UNKNOWN construction site is wired to
 * the correct [UnknownKind]. The enumeration here is the "every UNKNOWN carries a kind" guard for the
 * engine layer — the construction boundary rejects a violation rather than relying on review.
 */
internal class UnknownKindWiringTest {

    // --- the construction boundary refuses a bare UNKNOWN ------------------------------------------

    @Test
    fun a_kindless_UNKNOWN_cannot_be_constructed() {
        // The factory signature requires a non-null kind, but the all-args path is exercised by the
        // public verified/refuted ctor (kind=null there is fine because the verdict isn't UNKNOWN);
        // a direct UNKNOWN with a null kind must be rejected by the init guard.
        val ex = assertThrows(IllegalArgumentException::class.java) {
            // reflection-free: route through the named factory but blank the reason to trip the guard
            JbmcResult.unknown(UnknownKind.PARSE_FAILURE, "  ", "raw")
        }
        assertTrue(ex.message!!.contains("non-empty"), ex.message)
    }

    @Test
    fun a_reasonless_UNKNOWN_cannot_be_constructed() {
        assertThrows(IllegalArgumentException::class.java) {
            JbmcResult.unknown(UnknownKind.SOLVER_GAVE_UP, null, "raw")
        }
    }

    @Test
    fun a_verified_or_refuted_result_carries_no_kind_or_reason() {
        val verified = JbmcResult(true, listOf(), "{}")
        assertNull(verified.undecidedKind)
        assertNull(verified.undecidedReason)
        val refuted = JbmcResult(false, listOf(), "{}")
        assertNull(refuted.undecidedKind)
    }

    // --- each engine-layer site is wired to its kind, with a non-empty reason ----------------------

    @Test
    fun every_engine_layer_unknown_site_carries_a_kind_and_nonEmpty_reason() {
        val sites = listOf(
                // factory                                                 expected kind
                JbmcResult.unknownTimeout("timed out after 1s", "{}") to UnknownKind.TIMEOUT,
                JbmcResult.unknownEngineCrash("engine exited 6", "{}") to UnknownKind.ENGINE_CRASH,
                JbmcResult.unknownParse(
                        JbmcOutputParser.parseFailureReason("garbage"), "garbage") to UnknownKind.PARSE_FAILURE,
                // the parser's own UNKNOWN sites, driven through parse():
                JbmcOutputParser.parse("not-json-at-all", "pkg.T.p") to UnknownKind.PARSE_FAILURE,
                JbmcOutputParser.parse(unwindingJson(), "pkg.T.p") to UnknownKind.UNWINDING_ASSERTION,
                JbmcOutputParser.parse(markerlessJson(), "pkg.T.p") to UnknownKind.SOLVER_GAVE_UP)
        for ((result, expectedKind) in sites) {
            assertTrue(result.isUnknown, "site should be UNKNOWN")
            assertNotNull(result.undecidedKind, "no kindless UNKNOWN")
            assertEquals(expectedKind, result.undecidedKind, "site wired to wrong kind")
            assertFalse(result.undecidedReason.isNullOrBlank(), "no reasonless UNKNOWN")
        }
    }

    // --- PARSE_FAILURE self-diagnosis --------------------------------------------------------------

    @Test
    fun parse_failure_reason_classifies_empty_truncated_and_garbage_with_length_and_tail() {
        val empty = JbmcOutputParser.parseFailureReason("")
        assertTrue(empty.contains("could not parse"))
        assertTrue(empty.contains("empty"), empty)
        assertTrue(empty.contains("total length: 0"), empty)

        val truncated = JbmcOutputParser.parseFailureReason("[{\"result\":[{\"status\":\"SUC")
        assertTrue(truncated.contains("truncated JSON"), truncated)
        assertTrue(truncated.contains("total length:"), truncated)
        assertTrue(truncated.contains("last "), truncated)

        val garbage = JbmcOutputParser.parseFailureReason("Segmentation fault (core dumped)\njbmc: abort")
        assertTrue(garbage.contains("non-JSON garbage"), garbage)
        assertTrue(garbage.contains("core dumped"), "the tail carries the actual output: $garbage")
    }

    @Test
    fun parse_failure_tail_is_bounded_to_about_500_chars() {
        val big = "x".repeat(5000)
        val reason = JbmcOutputParser.parseFailureReason(big)
        assertTrue(reason.contains("total length: 5000"), reason)
        // The tail is bounded: the whole 5000-char body is NOT inlined.
        assertTrue(reason.length < 1200, "reason should fold only a bounded tail, was ${reason.length}")
        assertTrue(reason.contains("last 500 chars"), reason)
    }

    // --- helpers: minimal --json-ui JSON for the parser's UNKNOWN branches -------------------------

    /** A result array with one unwinding-assertion FAILURE and no reachability markers -> UNKNOWN
     *  (UNWINDING_ASSERTION) via the marker-less unwinding branch. */
    private fun unwindingJson(): String = """
        [ { "result": [
            { "property": "pkg.T.p.unwind.0", "description": "unwinding assertion loop 0",
              "status": "FAILURE" }
        ] } ]
    """.trimIndent()

    /** A result array with neither markers nor violations -> UNKNOWN (SOLVER_GAVE_UP: markers missing). */
    private fun markerlessJson(): String = """
        [ { "result": [
            { "property": "pkg.T.p.assertion.1", "description": "some assertion", "status": "SUCCESS" }
        ] } ]
    """.trimIndent()
}
