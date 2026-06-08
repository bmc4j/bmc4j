package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the [UnknownKind] table: each kind's [UnknownKind.retryable] flag is the soundness-relevant
 * contract the engine retry keys off, so it must match the design exactly. A regression here would
 * either re-run a deterministic UNKNOWN forever-once (waste) or fail to self-heal a transient flake.
 */
internal class UnknownKindTest {

    @Test
    fun retryable_flags_match_the_design_table() {
        // Retryable: the three nondeterministic flake shapes that a re-run can clear.
        assertTrue(UnknownKind.ENGINE_CRASH.retryable, "ENGINE_CRASH is retryable")
        assertTrue(UnknownKind.PARSE_FAILURE.retryable, "PARSE_FAILURE is retryable")
        assertTrue(UnknownKind.LINK_FAILURE_STUB.retryable, "LINK_FAILURE_STUB is retryable")
        // Not retryable: deterministic causes where a re-run pays the same cost for the same answer.
        assertFalse(UnknownKind.TIMEOUT.retryable, "TIMEOUT is not retryable")
        assertFalse(UnknownKind.UNMODELLED_MEMBER.retryable, "UNMODELLED_MEMBER is not retryable")
        assertFalse(UnknownKind.OUT_OF_SCOPE.retryable,
                "OUT_OF_SCOPE is not retryable (a declared decline is deterministic, not a flake)")
        assertFalse(UnknownKind.UNWINDING_ASSERTION.retryable, "UNWINDING_ASSERTION is not retryable")
        assertFalse(UnknownKind.SOLVER_GAVE_UP.retryable, "SOLVER_GAVE_UP is not retryable")
        assertFalse(UnknownKind.MIRROR_FAILURE.retryable, "MIRROR_FAILURE is not retryable")
        assertFalse(UnknownKind.PURITY_AUDIT.retryable, "PURITY_AUDIT is not retryable")
    }

    @Test
    fun exactly_ten_kinds_three_retryable() {
        assertEquals(10, UnknownKind.entries.size, "the design table has exactly ten kinds")
        assertEquals(3, UnknownKind.entries.count { it.retryable },
                "exactly three kinds are retryable")
    }

    @Test
    fun fromNameOrNull_round_trips_and_is_lenient() {
        for (k in UnknownKind.entries) {
            assertEquals(k, UnknownKind.fromNameOrNull(k.name))
        }
        assertEquals(UnknownKind.PARSE_FAILURE, UnknownKind.fromNameOrNull("  PARSE_FAILURE "))
        assertNull(UnknownKind.fromNameOrNull(null))
        assertNull(UnknownKind.fromNameOrNull(""))
        assertNull(UnknownKind.fromNameOrNull("NOT_A_KIND"))
    }
}
