package contracts.suspendcontracts

import example.suspendcontracts.Calcs
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * Contracts on the `suspend` functions of [Calcs]. The mirror is declared `suspend` so it binds the
 * suspend production target, whose lowered ABI is `(args, Continuation)Object`; the KSP processor reads
 * the declared `suspend` shape, synthesizes that lowered descriptor for the call-site redirect, hides
 * the trailing `Continuation` from the predicates, and uses the declared result type (`Int`), so the
 * predicates bind the plain Kotlin shape — `bounded(n)` and `isN(result, n)` — with no coroutine types
 * leaking in.
 *
 * The generated `enforce__stepTo` drives the real suspend body to completion (immediate dispatch) and
 * checks `@Ensures` on the completed result. `stepBuggy` is a deliberately-false demo pinned
 * [Verdict.REFUTED]: its post-suspension off-by-one breaks `stepBuggy(n) == n`, so the enforce-proof
 * refutes it and it publishes no reusable redirect.
 */
@BmcContractsFor(Calcs::class)
interface CalcContract {

    @Requires("bounded")
    @Ensures("isN")
    suspend fun stepTo(n: Int): Int

    @ExpectEnforce(Verdict.REFUTED)
    @Requires("bounded")
    @Ensures("isN")
    suspend fun stepBuggy(n: Int): Int

    companion object {
        @JvmStatic fun bounded(n: Int): Boolean = n in 0..5
        @JvmStatic fun isN(result: Int, n: Int): Boolean = result == n
    }
}
