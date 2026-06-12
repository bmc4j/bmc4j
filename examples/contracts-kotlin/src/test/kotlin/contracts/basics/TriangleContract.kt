package contracts.basics

import example.basics.Triangles
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.Requires

/**
 * The contract for [Triangles.triangle], declared test-side from Kotlin as a plain Kotlin **`object`**
 * whose predicates are **ordinary member `fun`s** — the standard Kotlin contract shape (no
 * `companion object`, no `@JvmStatic` per predicate). The processor invokes the predicates on the
 * singleton (`TriangleContract.INSTANCE.bounded(n)` in the generated Java); a pure boolean over its
 * arguments reading no object state is analyzed by JBMC identically to a static one, and the purity
 * audit certifies the singleton read via the object's `static final INSTANCE` field.
 *
 * The mirror method carries a throwaway `error("mirror")` body — never used; only its signature (which
 * binds to `Triangles.triangle`) and the `@Requires`/`@Ensures` names matter, exactly like the abstract
 * interface mirror. Parameter names survive into bytecode because the bmc4j plugin sets
 * `javaParameters = true` on the Kotlin compile.
 *
 * Here the target is an `object` (`Triangles`); `classtarget` shows the SAME object-hosted contract
 * shape binding a normal-`class` static target, confirming host-kind and target-kind are independent.
 */
@BmcContractsFor(Triangles::class)
object TriangleContract {

    @Requires("bounded")
    @Ensures("nonNeg")
    fun triangle(n: Int): Int = error("mirror")

    // Plain member predicates — no companion, no @JvmStatic. Pure booleans over their arguments.
    fun bounded(n: Int): Boolean = n in 0..8
    fun nonNeg(result: Int, n: Int): Boolean = result >= 0
}
