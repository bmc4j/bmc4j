package proofs.arraybounds;

import example.arraybounds.GradeBand;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

class GradeBandProofTests {

    /** FAILS: JBMC finds score == 100 breaks the buggy version. */
    // Expected verdict: REFUTED - the seeded off-by-one throws at score = 100.
    @BmcProof(expect = Verdict.REFUTED)
    void label_never_throws_for_valid_scores() {
        int score = Bmc.anyInt(1, 100);
        GradeBand.label(score);
    }

    /** PASSES: the clamped version is safe for every valid score. */
    @BmcProof
    void labelSafe_never_throws_for_valid_scores() {
        int score = Bmc.anyInt(1, 100);
        GradeBand.labelSafe(score);
    }
}
