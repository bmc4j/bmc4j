package contracts.suspendcontracts

import example.suspendcontracts.Calcs
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * Contracts on the `suspend` functions of [Calcs]. The mirror is declared `suspend` so its kapt-lowered
 * signature matches the lowered production target (`(args, Continuation)Object`); the processor hides
 * the trailing `Continuation` from the predicates and recovers the declared result type (`Int`) from
 * it, so the predicates bind the plain Kotlin shape — `bounded(n)` and `isN(result, n)` — with no
 * coroutine types leaking in.
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
