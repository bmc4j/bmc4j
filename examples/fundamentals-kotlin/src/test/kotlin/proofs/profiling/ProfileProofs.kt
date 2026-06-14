package proofs.profiling

import example.loopsunwinding.Sums
import example.timeout.Heavy
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.BmcProfile
import org.bmc4j.Verdict

/**
 * The [BmcProfile] capability: annotate a proof to get a per-stage **performance breakdown** printed
 * alongside its verdict, reading as *(bmc4j prep) + (engine)*:
 * - a **bmc4j pipeline** group — the classpath mirroring + bytecode rewrites bmc4j runs BEFORE jbmc
 *   launches (mirror, desugar, reachability, model-slice, …), each *harness-measured* (tagged
 *   `[bmc4j]`); and
 * - the **engine phases** — jbmc's own `Runtime <Phase>:` timings (tagged `[engine]`), plus the engine
 *   subprocess wall-clock the harness measures (tagged `[harness]`).
 *
 * So a slow proof reveals at a glance whether the cost is OUR bytecode/classpath work or jbmc's
 * symex/solver. It is purely additive: the verdict is unchanged, only extra diagnostic output is
 * emitted, parsed from the verbose engine stream the harness already captures (no second engine run).
 *
 * Run these and read the `bmc4j[profile]:` lines in the test output. The two proofs show the two ends
 * of the diagnostic: a tractable loop that reaches the solver (full pipeline + engine phases), and a
 * heavy proof that times out **before SAT**: its symbolic execution finishes but the engine is then
 * killed bit-blasting the equation in Convert SSA, where the harness attributes the unaccounted
 * wall-clock (a derived `Convert SSA (incomplete)` entry). That phase attribution is the single most
 * useful signal when a proof hangs.
 */
class ProfileProofs {

    /**
     * VERIFIED, with a full breakdown. A bounded loop the engine unwinds and then solves: the profile
     * shows bmc4j's pipeline passes (`[bmc4j]` rows) followed by the engine phases (`[engine]` Symex /
     * Convert SSA / Solver), the loop being unwound by method, the formula size, and
     * `reached SAT/SMT solver: YES`. `@BmcProfile` never changes the verdict — this still verifies
     * exactly as it would without the annotation.
     */
    @BmcProof(unwind = 12)
    @BmcProfile
    fun `tractable loop profile reaches the solver`() {
        val n = Bmc.anyInt(0, 10)
        Bmc.check(Sums.sumTo(n) == n * (n + 1) / 2)
    }

    /**
     * TIMEOUT, profiled — the showcase case. A quadratic double-loop over two wide symbolic inputs at a
     * high unwind builds a formula too large to solve in a small budget. The proof is force-killed and
     * reported as the TIMEOUT flavour of UNKNOWN (the verdict is unchanged by profiling), and the
     * breakdown — parsed from what the engine streamed up to the kill — pinpoints WHERE it was stuck.
     * Symbolic execution COMPLETES here (jbmc reports a `Runtime Symex` line); the engine is then killed
     * inside Convert SSA, lowering the program equation to a bit-vector formula, so the harness shows the
     * real `[engine] Symex` time plus a derived `[harness] Convert SSA (incomplete)` entry for the
     * unaccounted remainder, and `reached SAT/SMT solver: NO` confirms it never reached the solver. Under
     * `@BmcProfile` the live `bmc4j[engine]:` lines also print the phase transitions as they happen.
     */
    @BmcProof(unwind = 64, timeoutSeconds = 4, expect = Verdict.TIMEOUT)
    @BmcProfile
    fun `heavy proof profile shows it never reached sat`() {
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        Bmc.assume(a in 0..60)
        Bmc.assume(b in 0..60)
        Bmc.check(Heavy.quadraticMix(a, b) >= Long.MIN_VALUE) // trivially true; the cost is the formula
    }
}
