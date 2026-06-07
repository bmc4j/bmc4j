package proofs.kotlinresult

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the `kotlin.Result<T>` value-class model. `Result.success`/`Result.failure`
 * and `getOrNull`/`isSuccess`/`isFailure`/`exceptionOrNull` are `@InlineOnly`, so they inline straight into
 * the caller and compile down to the modeled value-class ABI (`Result."constructor-impl"`,
 * `"isFailure-impl"`, `"isSuccess-impl"`, `"exceptionOrNull-impl"`). The real ABI chain reaches stdlib
 * internals JBMC stubs (Result is a value class — its members nondet-havoc unmodeled), so these laws pin the
 * model's success/failure discrimination under JBMC.
 *
 * Note: the value `T` is kept a reference (`String`) — Result boxes its carrier, and a success of a literal
 * boxes to the raw value while a failure boxes to the `Failure` wrapper; that is exactly the discriminator
 * the model keys on.
 */
class ResultLaws {

    @BmcProof
    fun success_is_success_not_failure() {
        val r = Result.success("ok")
        Bmc.check(r.isSuccess && !r.isFailure)
    }

    @BmcProof
    fun failure_is_failure_not_success() {
        val r = Result.failure<String>(RuntimeException())
        Bmc.check(r.isFailure && !r.isSuccess)
    }

    @BmcProof
    fun success_getOrNull_is_value_and_no_exception() {
        val r = Result.success("ok")
        Bmc.check(r.getOrNull() == "ok" && r.exceptionOrNull() == null)
    }

    @BmcProof
    fun failure_getOrNull_is_null_and_carries_exception() {
        val e = RuntimeException()
        val r = Result.failure<String>(e)
        Bmc.check(r.getOrNull() == null && r.exceptionOrNull() === e)
    }
}
