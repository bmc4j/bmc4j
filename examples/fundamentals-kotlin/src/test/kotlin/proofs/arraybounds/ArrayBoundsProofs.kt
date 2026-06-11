package proofs.arraybounds

import example.arraybounds.Grades
import example.arraybounds.Score
import example.arraybounds.gradeBand
import org.bmc4j.Bmc.*
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.assumeValid

// Kotlin-idiomatic proof shape: backtick-named proof methods (they survive as
// spaces in the bytecode, all the way through the engine) and the static facade
// star-imported so the symbolic-input calls read bare.
class ArrayBoundsProofs {

    /** FAILS: score 100 is in range but breaks the lookup (index 5). */
    // Expected verdict: REFUTED - the seeded off-by-one throws at score = 100.
    @BmcProof(expect = Verdict.REFUTED)
    fun `label never throws for valid scores`() {
        Grades.label(anyInt(1, 100))
    }

    /** PASSES: the clamped version is safe for every valid score. */
    @BmcProof
    fun `labelSafe never throws for valid scores`() {
        Grades.labelSafe(anyInt(1, 100))
    }

    /** PASSES: every Score that exists is in 1..100 — the value class's `init { require(...) }`
     *  prunes every other input, so the invariant is verified, not assumed. */
    @BmcProof
    fun `Score invariant holds`() {
        val s = assumeValid { Score(anyInt()) }
        check(s.value in 1..100)
    }

    /** PASSES: gradeBand never throws for a valid Score. `assumeValid` folds the value class's
     *  1..100 range into the proof domain — no separate `assume` restating it. */
    @BmcProof(unwind = 1)
    fun `gradeBand never throws for any Score`() {
        val score = assumeValid { Score(anyInt()) }
        gradeBand(score.value)
    }

    /** FAILS: not every Score is <= 50; the value class admits the full 1..100 range. */
    // Expected verdict: REFUTED - the invariant does not bound scores to 1..50.
    @BmcProof(expect = Verdict.REFUTED)
    fun `Score is not bounded to 50`() {
        val s = assumeValid { Score(anyInt()) }
        check(s.value in 1..50)
    }
}
