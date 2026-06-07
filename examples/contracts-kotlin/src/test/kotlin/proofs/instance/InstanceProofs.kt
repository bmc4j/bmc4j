package proofs.instance

import example.instance.Account
import example.instance.AccountNaive
import org.bmc4j.Bmc
import org.bmc4j.Bmc.anyInt
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Pure instance-method contracts in Kotlin, the caller side. Alongside these the processor
 * auto-generates `AccountContract__BmcEnforce.enforce__project`, which discharges `@Ensures` against
 * the REAL loop body with a symbolic receiver (`self` nondet). The redirect rewrites the Kotlin
 * `a.project(amount)` (invokevirtual) to the static stub with the receiver prepended.
 */
class InstanceProofs {

    /**
     * PASSES at a tiny bound — only because the instance call is redirected to the contract summary,
     * so the caller relies on `@Ensures result >= self.balance` instead of re-analyzing the loop.
     */
    @BmcProof(unwind = 2)
    fun `caller reuses the instance contract at a tiny bound`() {
        val balance = anyInt(0, 1000)
        val amount = anyInt(0, 8)
        val a = Account(balance)
        val projected = a.project(amount)
        Bmc.check(projected >= a.balance)
    }

    /** UNDECIDED at the same bound: `AccountNaive` has no contract, so the real loop is inlined and
     *  overruns `unwind = 2` — incompleteness (UNKNOWN), not a counterexample. */
    @BmcProof(unwind = 2, expect = Verdict.UNKNOWN)
    fun `without a contract the same bound is too small`() {
        val balance = anyInt(0, 1000)
        val amount = anyInt(0, 8)
        val a = AccountNaive(balance)
        Bmc.check(a.project(amount) >= a.balance)
    }
}
