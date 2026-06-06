package proofs.arraybounds

import example.arraybounds.Grades
import org.bmc4j.Bmc.*
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

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
}
