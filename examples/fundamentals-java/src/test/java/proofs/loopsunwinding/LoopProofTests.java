package proofs.loopsunwinding;

import example.loopsunwinding.Sums;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

class LoopProofTests {

    /**
     * PASSES: the loop sum equals the closed form for all n in 0..10, and the
     * unwind bound (12) is large enough to cover the whole assumed range.
     */
    @BmcProof(unwind = 12)
    void sum_to_n_matches_closed_form() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2);
    }

    /**
     * FAILS as UNDECIDED: same property, but the unwind bound (4) is too small to cover
     * n up to 10. --unwinding-assertions (on by default) reports the truncation, and the
     * verdict is UNKNOWN - incompleteness, NOT a refutation: nothing was proven wrong,
     * the bound just cut exploration short. (It used to be mislabeled REFUTED, which let
     * this very demo pass for the wrong reason.)
     */
    // Expected verdict: UNKNOWN - the insufficient bound is reported as undecided.
    @BmcProof(unwind = 4, expect = Verdict.UNKNOWN)
    void too_small_a_bound_is_reported_not_trusted() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2);
    }

    /**
     * PASSES with AUTO unwind (the default): the same size-bounded loop as above, but with NO explicit
     * bound. bmc4j auto-discovers the smallest unwind that covers n up to 10 and VERIFIES there — the
     * beginner never has to know the loop runs ~11 times. The discovered bound is reported in the log
     * and the structured summary so it can be pinned.
     */
    @BmcProof
    void auto_unwind_discovers_the_bound_and_verifies() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2);
    }

    /**
     * REFUTED only at a higher bound (AUTO must not be masked by a lower VERIFIED). The wrong claim
     * {@code sumTo(n) != 15} holds for every n whose loop runs fewer than 5 times, so a too-small bound
     * would VERIFY it falsely — but {@code --unwinding-assertions} stays on at every rung, so an
     * under-covering bound is a fail-closed UNKNOWN (it climbs), never a spurious pass. At the covering
     * bound the real counterexample (n = 5, sumTo = 15) surfaces. {@code expect = REFUTED} pins that the
     * climb reaches the genuine refutation rather than stopping early on a truncated green.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void auto_unwind_does_not_mask_a_higher_bound_refutation() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(Sums.sumTo(n) != 15); // false at n == 5 (1+2+3+4+5), reachable only once the bound covers it
    }

    /**
     * VACUOUS with AUTO unwind (composition with vacuity): contradictory assumptions admit no input, so
     * the proof would "pass" trivially at the seed bound. Auto-discovery must NOT accept that bound-1
     * pass as the discovered bound — the existing reachability/vacuity check fires and the verdict is
     * VACUOUS, exactly as a pinned proof would report. {@code expect = VACUOUS} makes it a regression
     * test that auto-unwind never launders an empty domain into a green.
     */
    @BmcProof(expect = Verdict.VACUOUS)
    void auto_unwind_surfaces_vacuity_not_a_trivial_bound() {
        int n = Bmc.anyInt(0, 10);
        Bmc.assume(n > 5);
        Bmc.assume(n < 2);              // empty domain: no n is both
        Bmc.check(Sums.sumTo(n) == 0);
    }

    /**
     * UNDECIDED with AUTO unwind because the bound is DATA-DEPENDENT, not just too small: the loop runs
     * {@code start} times, and {@code start} is a symbolic input over a range no fixed unwind can cover.
     * Auto-unwind climbs to the cap and the unwinding assertion fires at the countDown loop at EVERY
     * bound, so bmc4j reports it as a data-dependent bound, NAMES the loop, and says raising unwind won't
     * help — distinct from {@link #auto_unwind_discovers_the_bound_and_verifies()}, whose constant-bounded
     * loop converges. (The exact diagnostic text is pinned by the engine-layer unit tests.)
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    void a_data_dependent_loop_is_undecided_under_auto_unwind() {
        int start = Bmc.anyInt(1, 1_000_000);
        Bmc.check(Sums.countDown(start) >= 0);
    }
}
