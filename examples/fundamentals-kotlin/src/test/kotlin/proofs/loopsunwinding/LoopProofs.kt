package proofs.loopsunwinding

import example.loopsunwinding.Sums
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

class LoopProofs {

    /** PASSES: the loop sum equals the closed form for all n in 0..10; the bound (12) covers it. */
    @BmcProof(unwind = 12)
    fun sum_to_n_matches_closed_form() {
        val n = Bmc.anyInt(0, 10)
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2)
    }

    /**
     * FAILS as UNDECIDED: same property, but the unwind bound (4) is too small to cover n up
     * to 10. --unwinding-assertions reports the truncation and the verdict is UNKNOWN -
     * incompleteness, not a refutation (it used to be mislabeled REFUTED).
     */
    // Expected verdict: UNKNOWN - the insufficient bound is reported as undecided.
    @BmcProof(unwind = 4, expect = Verdict.UNKNOWN)
    fun too_small_a_bound_is_reported_not_trusted() {
        val n = Bmc.anyInt(0, 10)
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2)
    }
}
