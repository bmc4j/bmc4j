package org.bmc4j.engine

import org.bmc4j.contracts.ExpectEnforce
import org.bmc4j.contracts.contractFor

/** A mutating instance target for the [ContractDslBytecode] decode/lower test. */
class DslFixtureAccount(var balance: Int) {
    fun deposit(amount: Int) {
        balance += amount
    }
}

/** A static target (threads no receiver - the first arg is `self`). */
object DslFixtureMath {
    @JvmStatic
    fun capped(value: Int, cap: Int): Int = if (value > cap) cap else value
}

/**
 * Top-level `val` contracts in the issue's exact surface - executed by the test to populate the registry,
 * then decoded/lowered. A MUTATING instance contract with an explicit `updatesOnly` frame, and a static
 * contract over args + result.
 */
val fixtureDeposit = contractFor(DslFixtureAccount::deposit) {
    whenPrecondition("amount in range") { self, amount -> self.balance in 0..1000 && amount in 0..1000 }
            .thenPostCondition("balance grew by amount") { before, after, amount, ret ->
                after.balance == before.balance + amount
            }
            .updatesOnly { self, amount -> self.balance }
}

val fixtureCapped = contractFor(DslFixtureMath::capped) {
    whenPrecondition("cap non-negative") { value, cap -> cap in 0..1000 && value in 0..2000 }
            .thenPostCondition("result within cap") { before, after, cap, ret -> ret <= cap }
}

/** A deliberately-false contract pinned REFUTED in the DSL. */
val fixtureWrong = contractFor(DslFixtureAccount::deposit, expect = ExpectEnforce.REFUTED) {
    whenPrecondition("anything") { self, amount -> amount in 0..1000 }
            .thenPostCondition("balance unchanged (false)") { before, after, amount, ret ->
                after.balance == before.balance
            }
            .updatesOnly { self, amount -> self.balance }
}
