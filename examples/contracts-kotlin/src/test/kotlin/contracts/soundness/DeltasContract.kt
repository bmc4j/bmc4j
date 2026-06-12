package contracts.soundness

import example.soundness.Deltas
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * One contract type mixing an HONEST mirror and a deliberately FALSE one — the soundness guard, in the
 * standard Kotlin **object-host** shape (a plain `object` with ordinary member `fun` predicates; no
 * `companion object`, no `@JvmStatic` per predicate). There is no hand-written proof: the auto-generated
 * enforce-proofs *are* the tests.
 *
 * - `absDelta` is honest (`result >= 0` always holds), so `enforce__absDelta` VERIFIES (the default)
 *   and publishes a reusable redirect.
 * - `delta` is a lie (`result >= 0` is false when `a < b`), pinned per-method [Verdict.REFUTED]: its
 *   `enforce__delta` passes BY refutation (counterexample `a = 0, b = 1`) and publishes NO redirect,
 *   so no caller ever reuses the false summary. Annotating an object-hosted contract is no more
 *   "asserting" it than the static form — the obligation is discharged structurally.
 *
 * The mirror methods carry a throwaway `error("mirror")` body — never used; only the signature (which
 * binds to `Deltas.delta` / `Deltas.absDelta`) and the `@Requires`/`@Ensures` names matter. The
 * predicates are invoked on the singleton (`DeltasContract.INSTANCE.bounded(...)` in the generated Java);
 * a pure boolean over its arguments is analyzed identically to a static one.
 */
@BmcContractsFor(Deltas::class)
object DeltasContract {

    @ExpectEnforce(Verdict.REFUTED)
    @Requires("bounded")
    @Ensures("nonNeg")
    fun delta(a: Int, b: Int): Int = error("mirror")

    @Requires("bounded")
    @Ensures("nonNeg")
    fun absDelta(a: Int, b: Int): Int = error("mirror")

    // Plain member predicates — no companion, no @JvmStatic. Pure booleans over their arguments.
    fun bounded(a: Int, b: Int): Boolean = a in -100..100 && b in -100..100
    fun nonNeg(result: Int, a: Int, b: Int): Boolean = result >= 0
}
