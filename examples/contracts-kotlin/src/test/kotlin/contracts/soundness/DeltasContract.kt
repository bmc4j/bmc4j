package contracts.soundness

import example.soundness.Deltas
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * One contract type mixing an HONEST mirror and a deliberately FALSE one — the soundness guard.
 * There is no hand-written proof: the auto-generated enforce-proofs *are* the tests.
 *
 * - `absDelta` is honest (`result >= 0` always holds), so `enforce__absDelta` VERIFIES (the default)
 *   and publishes a reusable redirect.
 * - `delta` is a lie (`result >= 0` is false when `a < b`), pinned per-method [Verdict.REFUTED]: its
 *   `enforce__delta` passes BY refutation (counterexample `a = 0, b = 1`) and publishes NO redirect,
 *   so no caller ever reuses the false summary. Annotating a method is not asserting it — the
 *   obligation is discharged structurally.
 */
@BmcContractsFor(Deltas::class)
interface DeltasContract {

    @ExpectEnforce(Verdict.REFUTED)
    @Requires("bounded")
    @Ensures("nonNeg")
    fun delta(a: Int, b: Int): Int

    @Requires("bounded")
    @Ensures("nonNeg")
    fun absDelta(a: Int, b: Int): Int

    companion object {
        @JvmStatic fun bounded(a: Int, b: Int): Boolean = a in -100..100 && b in -100..100
        @JvmStatic fun nonNeg(result: Int, a: Int, b: Int): Boolean = result >= 0
    }
}
