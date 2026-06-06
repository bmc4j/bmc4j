package proofs.whenexpr

import example.whenexpr.grade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The replay round-trip, end to end. When the refuted Kotlin proof
 * [WhenProofs.every_valid_score_is_graded] runs, bmc4j writes a Kotlin replay scratch file
 * `build/bmc4j/replays/WhenProofs_every_valid_score_is_gradedReplay.kt` whose reconstructed input is
 * `val score = 79` — the boundary the seeded gap in `grade()` leaves ungraded.
 *
 * This test IS that replay, pasted into the module's `src/test/kotlin` (as the validation step
 * prescribes) and given the assertion the proof made: it reconstructs the same `score` and shows the
 * violated condition (`grade(score) != 'F'`) actually trips. So a Kotlin counterexample really does
 * reproduce in plain Kotlin — no JBMC, just the literal the renderer emitted. A regression that
 * closed the gap (or changed the boundary) would break this deterministically.
 */
class GradeGapReplayReproductionTest {

    @Test
    fun replayed_counterexample_reproduces_the_refutation() {
        // Reconstructed from the counterexample, exactly as the generated .kt replay declares it.
        val score = 79

        // The proof asserted `grade(score) != 'F'`. The replay reproduces the violation:
        assertEquals('F', grade(score), "the gap at 79 grades to 'F'")
        assertFalse(grade(score) != 'F', "the proof's checked condition is violated for score = 79")
    }
}
