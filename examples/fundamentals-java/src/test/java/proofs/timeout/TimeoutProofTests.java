package proofs.timeout;

import example.timeout.Heavy;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Per-proof timeout and the <b>UNKNOWN</b> verdict. A proof is verified, refuted, or —
 * when the engine can't reach a verdict within its budget — <em>UNKNOWN</em> (undecided). UNKNOWN
 * still <b>fails</b> the proof (the absence of a verdict is not a proof), but distinctly from a
 * refutation: there is no counterexample, and the message tells you how to make it decidable (raise
 * the unwind/timeout, shrink the symbolic range with {@code assume}, split the proof, or contract the
 * heavy callee). This is the "visible over silent" discipline applied to resource exhaustion: a
 * SAT-pathological proof times out cleanly instead of hanging the build, and a timeout in CI is
 * never mistaken for "your code is wrong".
 */
class TimeoutProofTests {

    /**
     * Expected verdict: TIMEOUT. A quadratic double-loop over two wide symbolic inputs, unwound to
     * a high bound, produces a formula too large to solve in a 1-second budget (minutes-scale even
     * on fast hardware). bmc4j force-kills the engine tree on expiry and reports the structured
     * TIMEOUT flavour of UNKNOWN — <em>not</em> a refutation (no counterexample) and not a silent
     * pass. {@code expect = TIMEOUT} asserts the budget itself fired: a solver crash or unparseable
     * output would NOT satisfy it, so this demo guards the timeout machinery specifically.
     */
    @BmcProof(unwind = 64, timeoutSeconds = 1, expect = Verdict.TIMEOUT)
    void heavy_proof_times_out_as_unknown_not_refuted() {
        int a = Bmc.anyInt();
        int b = Bmc.anyInt();
        Bmc.assume(a >= 0 && a <= 60);
        Bmc.assume(b >= 0 && b <= 60);
        Bmc.check(Heavy.quadraticMix(a, b) >= Long.MIN_VALUE); // trivially true; the cost is in the formula
    }

    /**
     * PASSES: the same routine kept tractable — a tight range and a small bound make the formula
     * small enough to decide well within budget. A generous timeout never bites a proof that solves
     * quickly; the budget only catches the pathological ones.
     */
    @BmcProof(unwind = 4, timeoutSeconds = 60)
    void tractable_proof_finishes_well_within_budget() {
        int a = Bmc.anyInt(0, 2);
        int b = Bmc.anyInt(0, 2);
        Bmc.check(Heavy.quadraticMix(a, b) >= 0); // small range: easy to solve, comfortably in time
    }
}
