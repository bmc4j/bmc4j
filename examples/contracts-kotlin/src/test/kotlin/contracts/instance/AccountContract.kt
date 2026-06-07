package contracts.instance

import example.instance.Account
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.Verdict

/**
 * The contract for [Account]'s pure instance method `project(Int)`, declared test-side from Kotlin.
 * Because the resolved target is an instance method, the processor threads the receiver into the
 * predicates as a leading `self` parameter — `nonNegative(self, amount)` and
 * `atLeastBalance(result, self, amount)`. The receiver appears in the Kotlin predicate exactly as a
 * normal parameter; `self.balance` reads the receiver field.
 *
 * The postcondition depends on a field of the receiver, so the enforce-proof is only green because it
 * holds for ALL receiver states (JBMC makes `self` nondet). `projectAgain` is a deliberately-false
 * demo pinned [Verdict.REFUTED]: it claims a strictly-greater result, which is false at `amount == 0`
 * for any balance — so it publishes no reusable redirect and its enforce-proof passes by refutation.
 */
@BmcContractsFor(Account::class)
interface AccountContract {

    @Requires("nonNegative")
    @Ensures("atLeastBalance")
    fun project(amount: Int): Int

    @ExpectEnforce(Verdict.REFUTED)
    @Requires("nonNegative")
    @Ensures("exceedsBalance")
    fun projectAgain(amount: Int): Int

    companion object {
        // Bound BOTH the receiver state and the argument: a precondition over `self` is exactly what
        // an instance contract adds. Without bounding the balance, balance + amount could overflow for
        // a near-MAX balance, which would (correctly) refute the postcondition.
        @JvmStatic fun nonNegative(self: Account, amount: Int): Boolean =
            self.balance in 0..1000 && amount in 0..8

        @JvmStatic fun atLeastBalance(result: Int, self: Account, amount: Int): Boolean =
            result >= self.balance

        @JvmStatic fun exceedsBalance(result: Int, self: Account, amount: Int): Boolean =
            result > self.balance
    }
}
