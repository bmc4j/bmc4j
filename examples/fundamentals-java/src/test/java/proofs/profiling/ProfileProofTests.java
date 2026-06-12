package proofs.profiling;

import example.loopsunwinding.Sums;
import example.timeout.Heavy;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.BmcProfile;
import org.bmc4j.Verdict;

/**
 * The {@link BmcProfile} capability: annotate a proof to get a per-stage <b>performance breakdown</b>
 * printed alongside its verdict — phase timings, whether the SAT/SMT solver was ever reached, the
 * top loop-unwinding offenders, and formula-size stats. It is purely additive: the verdict is
 * unchanged, only extra diagnostic output is emitted, parsed from the verbose engine stream the
 * harness already captures (no second engine run).
 *
 * <p>Run these and read the {@code bmc4j[profile]:} lines in the test output. The two proofs show the
 * two ends of the diagnostic: a tractable loop that reaches the solver, and a heavy proof that times
 * out in symbolic execution and so <em>never reaches SAT</em> — the single most useful signal when a
 * proof is slow or hangs.
 */
class ProfileProofTests {

    /**
     * VERIFIED, with a full breakdown. A bounded loop the engine unwinds and then solves: the profile
     * shows the loop being unwound by method, the formula size, and {@code reached SAT/SMT solver: YES}
     * with the solver phase timing. {@code @BmcProfile} never changes the verdict — this still verifies
     * exactly as it would without the annotation.
     */
    @BmcProof(unwind = 12)
    @BmcProfile
    void tractable_loop_profile_reaches_the_solver() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2);
    }

    /**
     * TIMEOUT, profiled — the showcase case. A quadratic double-loop over two wide symbolic inputs at a
     * high unwind builds a formula too large to solve in a 2-second budget. The proof is force-killed
     * and reported as the TIMEOUT flavour of UNKNOWN (the verdict is unchanged by profiling), and the
     * breakdown — parsed from what the engine streamed up to the kill — pinpoints WHERE it was stuck:
     * the heavy method's loop unwinding dominates, and {@code reached SAT/SMT solver: NO} shows the time
     * went into symbolic execution / formula construction, not the solver. That is exactly the
     * information you need to make a timing-out proof tractable (shrink the range, lower the bound, or
     * contract the heavy callee).
     */
    @BmcProof(unwind = 64, timeoutSeconds = 4, expect = Verdict.TIMEOUT)
    @BmcProfile
    void heavy_proof_profile_shows_it_never_reached_sat() {
        int a = Bmc.anyInt();
        int b = Bmc.anyInt();
        Bmc.assume(a >= 0 && a <= 60);
        Bmc.assume(b >= 0 && b <= 60);
        Bmc.check(Heavy.quadraticMix(a, b) >= Long.MIN_VALUE); // trivially true; the cost is the formula
    }
}
