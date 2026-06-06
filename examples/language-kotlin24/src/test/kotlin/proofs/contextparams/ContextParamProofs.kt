package proofs.contextparams

import example.contextparams.Limits
import example.contextparams.clampDeposit
import example.contextparams.safeClampDeposit
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.assumeValid

/**
 * Context parameters lower to ordinary leading JVM parameters, and the stdlib
 * `context(value) { ... }` bridge is an `inline fun` — the lambda body is inlined at the call
 * site (no lambda object, no invokedynamic), so proofs over context-parameterized functions
 * work exactly like proofs over plain ones.
 */
class ContextParamProofs {

    // PASS: the upper clamp holds for every valid limit and every amount.
    @BmcProof
    fun clamp_never_exceeds_limit() {
        val limits = assumeValid { Limits(Bmc.anyInt()) }
        val amount = Bmc.anyInt()
        val deposited = context(limits) { clampDeposit(amount) }
        Bmc.check(deposited <= limits.max)
    }

    // FAIL (the bug): nothing clamps the bottom — a negative amount flows straight through.
    // Expected verdict: REFUTED with a negative `amount`.
    @BmcProof(expect = Verdict.REFUTED)
    fun clamp_yields_valid_amount() {
        val limits = assumeValid { Limits(Bmc.anyInt()) }
        val amount = Bmc.anyInt()
        val deposited = context(limits) { clampDeposit(amount) }
        Bmc.check(deposited in 0..limits.max)
    }

    // PASS: the fix enforces both bounds.
    @BmcProof
    fun safe_clamp_yields_valid_amount() {
        val limits = assumeValid { Limits(Bmc.anyInt()) }
        val amount = Bmc.anyInt()
        val deposited = context(limits) { safeClampDeposit(amount) }
        Bmc.check(deposited in 0..limits.max)
    }
}
