package proofs.exceptions

import example.exceptions.InsufficientFunds
import example.exceptions.safeWithdraw
import example.exceptions.withdraw
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.checkThrows

/**
 * `checkThrows<E> { ... }` returns the caught exception, so the ERROR PATH itself is provable:
 * the exception's constructor ran symbolically, and its fields are checked like any other
 * result. The bug class this catches — an exception that fires correctly but reports the wrong
 * diagnosis — is invisible to happy-path tests and usually discovered in production logs.
 */
class ExceptionFieldProofs {

    // PASS: an overdraw always throws, whatever the (sane) balance/amount.
    @BmcProof
    fun overdraw_always_refused() {
        val balance = Bmc.anyInt(0, 1_000_000)
        val amount = Bmc.anyInt(0, 1_000_000)
        Bmc.assume(amount > balance)
        checkThrows<InsufficientFunds> { withdraw(balance, amount) }
    }

    // FAIL (the bug): the refusal fires, but with balance/requested SWAPPED — the reported
    // shortfall is negative. Expected verdict: REFUTED - the error path lies about its diagnosis.
    @BmcProof(expect = Verdict.REFUTED)
    fun refusal_reports_true_shortfall() {
        val balance = Bmc.anyInt(0, 1_000_000)
        val amount = Bmc.anyInt(0, 1_000_000)
        Bmc.assume(amount > balance)
        val e = checkThrows<InsufficientFunds> { withdraw(balance, amount) }
        Bmc.check(e.shortfall == amount - balance && e.shortfall > 0)
    }

    // PASS: the fix reports the numbers it refused on — for EVERY refusing input.
    @BmcProof
    fun safe_refusal_reports_true_shortfall() {
        val balance = Bmc.anyInt(0, 1_000_000)
        val amount = Bmc.anyInt(0, 1_000_000)
        Bmc.assume(amount > balance)
        val e = checkThrows<InsufficientFunds> { safeWithdraw(balance, amount) }
        Bmc.check(e.shortfall == amount - balance && e.balance == balance && e.requested == amount)
    }
}
