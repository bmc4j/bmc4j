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
     * FAILS: same property, but the unwind bound (4) is too small to cover n up to 10.
     * --unwinding-assertions reports this instead of silently "proving" an under-explored loop.
     */
    // Expected verdict: REFUTED - the unwinding assertion reports the insufficient bound.
    @BmcProof(unwind = 4, expect = Verdict.REFUTED)
    fun too_small_a_bound_is_reported_not_trusted() {
        val n = Bmc.anyInt(0, 10)
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2)
    }
}
