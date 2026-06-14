package proofs.profiling;

import example.loopsunwinding.Sums;
import example.timeout.Heavy;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.BmcProfile;
import org.bmc4j.Verdict;

/**
 * The {@link BmcProfile} capability: annotate a proof to get a per-stage <b>performance breakdown</b>
 * printed alongside its verdict. The breakdown reads as <em>(bmc4j prep) + (engine)</em>:
 * <ul>
 *   <li>a <b>bmc4j pipeline</b> group — the classpath mirroring + bytecode rewrites bmc4j runs BEFORE
 *       jbmc launches (mirror, desugar, reachability, model-slice, …), each <em>harness-measured</em>
 *       (tagged {@code [bmc4j]}); and</li>
 *   <li>the <b>engine phases</b> — jbmc's own {@code Runtime <Phase>:} timings (tagged {@code [engine]}),
 *       plus the engine subprocess wall-clock the harness measures (tagged {@code [harness]}).</li>
 * </ul>
 * So a slow proof reveals at a glance whether the cost is OUR bytecode/classpath work or jbmc's
 * symex/solver. It is purely additive: the verdict is unchanged, only extra diagnostic output is
 * emitted, parsed from the verbose engine stream the harness already captures (no second engine run).
 *
 * <p>Run these and read the {@code bmc4j[profile]:} lines in the test output. The two proofs show the
 * two ends of the diagnostic: a tractable loop that reaches the solver (full pipeline + engine phases),
 * and a heavy proof that times out <em>before SAT</em>: its symbolic execution finishes but the engine
 * is then killed bit-blasting the equation in Convert SSA, where the harness attributes the unaccounted
 * wall-clock (a derived {@code Convert SSA (incomplete)} entry). That phase attribution is the single
 * most useful signal when a proof hangs.
 */
class ProfileProofTests {

    /**
     * VERIFIED, with a full breakdown. A bounded loop the engine unwinds and then solves: the profile
     * shows bmc4j's pipeline passes ({@code [bmc4j]} rows) followed by the engine phases ({@code [engine]}
     * Symex / Convert SSA / Solver), the loop being unwound by method, the formula size, and
     * {@code reached SAT/SMT solver: YES}. {@code @BmcProfile} never changes the verdict — this still
     * verifies exactly as it would without the annotation.
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
     * breakdown — parsed from what the engine streamed up to the kill — pinpoints WHERE it was stuck.
     * Symbolic execution COMPLETES here (jbmc reports a {@code Runtime Symex} line); the engine is then
     * killed inside Convert SSA, lowering the program equation to a bit-vector formula, so the harness
     * shows the real {@code [engine] Symex} time plus a derived {@code [harness] Convert SSA (incomplete)}
     * entry for the unaccounted remainder, and {@code reached SAT/SMT solver: NO} confirms it never
     * reached the solver. That is exactly the information you need to make a timing-out proof tractable
     * (shrink the range, lower the bound, or contract the heavy callee). Under {@code @BmcProfile} the
     * live {@code bmc4j[engine]:} lines also print the phase transitions as they happen.
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
