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

    /**
     * VERIFIES under AUTO (no pin): the same closed-form property, but the loop's trip count is bounded
     * by a CONSTANT range (n in 0..10), so auto-unwind's climb CONVERGES at a finite bound within the
     * cap — this is the control. A loop that just needs a bigger fixed bound is NOT data-dependent and
     * must not be mislabelled so; it simply verifies.
     */
    @BmcProof
    fun a_constant_bounded_loop_converges_under_auto_unwind() {
        val n = Bmc.anyInt(0, 10)
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2)
    }

    /**
     * UNDECIDED under AUTO (no pin): the loop runs `start` times, and `start` is a SYMBOLIC input over a
     * range no fixed unwind can cover. Auto-unwind climbs to the cap and the unwinding assertion fires at
     * the countDown loop at EVERY bound — the data-dependent-bound signal. The verdict is UNKNOWN and the
     * diagnostic states the trip count is data-dependent, names the loop, and says raising unwind won't
     * help (the message text is pinned by the engine-layer unit tests).
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    fun a_data_dependent_loop_is_undecided_under_auto_unwind() {
        val start = Bmc.anyInt(1, 1_000_000)
        Bmc.check(Sums.countDown(start) >= 0)
    }
}
