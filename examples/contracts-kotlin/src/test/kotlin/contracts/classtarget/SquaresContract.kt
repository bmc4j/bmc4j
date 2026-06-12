package contracts.classtarget

import example.classtarget.Squares
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * Object-hosted contract → **normal-`class` static target**. This is the orthogonality confirmation:
 * the host is a plain Kotlin **`object`** with ordinary member `fun` predicates (the standard Kotlin
 * shape, same as `contracts.basics.TriangleContract`), while the TARGET [Squares] is a normal `class`
 * whose `pyramid`/`bogus` are `companion object` `@JvmStatic fun`s (real static methods on `Squares`),
 * NOT an `object`. Host-kind (`object`) and target-kind (`class` static) are independent, and the
 * processor binds the mirror to the companion `@JvmStatic` static target by signature.
 *
 * The mirror methods carry a throwaway `error("mirror")` body — never used; only the signature (which
 * binds to `Squares.pyramid` / `Squares.bogus`) and the `@Requires`/`@Ensures` names matter.
 *
 * - `pyramid` is HONEST (`result >= 0` always holds), so `enforce__pyramid` VERIFIES — the gap closed:
 *   an object-hosted contract discharges its enforce-proof against a normal-`class` static target.
 * - `bogus` is a LIE (`result <= 0` is false for any positive pyramid), pinned [Verdict.REFUTED]: its
 *   `enforce__bogus` passes BY refutation and publishes NO redirect, so the object-form "annotating ≠
 *   asserting" soundness guard holds against a class-static target too.
 *
 * (Only the enforce direction is exercised here. A *caller-reuse* redirect for a Kotlin caller of a
 * `companion @JvmStatic` method is a separate capability — kotlinc lowers such a call to an
 * `invokevirtual` on the synthetic `Squares$Companion` singleton, a different call shape than the
 * `invokestatic` an `object` target's caller emits — so the caller-side redirect is demonstrated in
 * `basics`'s object-target form, not here.)
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
