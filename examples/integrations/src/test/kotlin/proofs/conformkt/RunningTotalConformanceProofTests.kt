package proofs.conformkt

import example.conformkt.RunningTotal
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.ConformProofsAgainstModel

/**
 * Conforming a `src/bmcModel` model against the **real implementation** with
 * [ConformProofsAgainstModel].
 *
 * The `custommodels*` proofs lean on models of un-analyzable services ([example.custommodelskt.ExchangeRates]
 * throws in `src/main`), so their real leg is intractable on purpose and they are *not* conformed —
 * that is the model's whole job. [RunningTotal] is the opposite: it is fully analyzable, but the real
 * [RunningTotal.sum] computes its result with a **bounded loop**, while its `src/bmcModel` model
 * computes the SAME result loop-free via the closed form `n * (n + 1) / 2`. The model is the cheap
 * stand-in a downstream proof would use to avoid unrolling that loop.
 *
 * `@ConformProofsAgainstModel(RunningTotal::class)` runs each proof below **twice**:
 *  - the **model leg** — the normal run, with the loop-free Kotlin `src/bmcModel` [RunningTotal]
 *    substituted; and
 *  - the **real leg** — the same proof with that model *excluded*, so the real looping [RunningTotal]
 *    is analysed (the loop is fully unrolled at this `unwind`).
 *
 * Both legs must reach the proof's expected verdict (default VERIFIED) or the proof fails, naming the
 * leg. The asserted property `sum(n) == n * (n + 1) / 2` ties the real loop result to the model's
 * closed form, so the real leg is a genuine check: a wrong loop body would REFUTE it against the model.
 */
@ConformProofsAgainstModel(RunningTotal::class)
class RunningTotalConformanceProofTests {

    /**
     * The loop and the closed form agree for every n in range. `unwind = 16` is large enough to fully
     * unroll the real loop (n up to [RunningTotal.MAX_N] = 8); the model leg has no loop to unroll.
     * This is the property that makes the real leg bite: it checks the loop sum equals `n*(n+1)/2`.
     */
    @BmcProof(unwind = 16)
    fun sum_matches_closed_form() {
        val n = Bmc.anyInt(0, RunningTotal.MAX_N)
        Bmc.check(RunningTotal.sum(n) == n * (n + 1) / 2)
    }
}
