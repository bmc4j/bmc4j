package proofs.arraybounds

import example.arraybounds.Grades
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

class ArrayBoundsProofs {

    /** FAILS: score 100 is in range but breaks the lookup (index 5). */
    // Expected verdict: REFUTED - the seeded off-by-one throws at score = 100.
    @BmcProof(expect = Verdict.REFUTED)
    fun label_never_throws_for_valid_scores() {
        Grades.label(Bmc.anyInt(1, 100))
    }

    /** PASSES: the clamped version is safe for every valid score. */
    @BmcProof
    fun labelSafe_never_throws_for_valid_scores() {
        Grades.labelSafe(Bmc.anyInt(1, 100))
    }
}
