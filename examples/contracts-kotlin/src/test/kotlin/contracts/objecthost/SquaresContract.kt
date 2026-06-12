package contracts.objecthost

import example.objecthost.Squares
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * The contract for [Squares.pyramid], declared test-side as a plain Kotlin **`object`** whose
 * predicates are **ordinary member `fun`s** — no `companion object`, no `@JvmStatic` per predicate.
 *
 * Contrast with `contracts.basics.TriangleContract`, an `interface` whose predicates must be
 * `@JvmStatic` functions in a `companion object` to compile to the `static boolean` methods the
 * generated stub/enforce historically called. The processor now also accepts predicates hosted as
 * members of an `object`: it invokes them on the singleton (`SquaresContract.INSTANCE.bounded(n)` in
 * the generated Java) instead of statically. A pure boolean over its arguments reading no object
 * state is analyzed by JBMC identically to a static one, and the purity audit certifies the
 * singleton read via its `static final INSTANCE` field — so this is a pure call-shape change with no
 * soundness difference.
 *
 * The mirror methods carry a throwaway `error("mirror")` body: their bodies are never used — only the
 * signature (which binds to the production method) and the `@Requires`/`@Ensures` names matter, exactly
 * like the abstract interface mirror. Parameter names survive into bytecode because the bmc4j plugin
 * sets `javaParameters = true` on the Kotlin compile.
 *
 * This type also doubles as the object-form soundness guard:
 *
 * - `pyramid` is HONEST (`result >= 0` always holds), so `enforce__pyramid` VERIFIES (the default) and
 *   publishes a reusable redirect — discharging IDENTICALLY to the static/companion form.
 * - `bogus` is a LIE (`result <= 0` is false for any positive pyramid), pinned [Verdict.REFUTED]: its
 *   `enforce__bogus` passes BY refutation and publishes NO redirect. Annotating an object-hosted
 *   contract is no more "asserting" it than the static form — the obligation is discharged structurally.
 */
@BmcContractsFor(Squares::class)
object SquaresContract {

    @Requires("bounded")
    @Ensures("nonNeg")
    fun pyramid(n: Int): Int = error("mirror")

    @ExpectEnforce(Verdict.REFUTED)
    @Requires("bounded")
    @Ensures("nonPos")
    fun bogus(n: Int): Int = error("mirror")

    // Plain member predicates — no companion, no @JvmStatic. Pure booleans over their arguments.
    fun bounded(n: Int): Boolean = n in 0..8
    fun nonNeg(result: Int, n: Int): Boolean = result >= 0
    fun nonPos(result: Int, n: Int): Boolean = result <= 0
}
