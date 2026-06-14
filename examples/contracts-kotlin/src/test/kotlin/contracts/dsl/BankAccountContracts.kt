package contracts.dsl

import example.dsl.BankAccount
import example.dsl.Counter
import example.dsl.Transfers
import org.bmc4j.contracts.ExpectEnforce
import org.bmc4j.contracts.contractFor

/**
 * The contracts DSL in its intended shape: top-level `val` contracts in a contracts source file, nothing
 * wrapping them. Each `contractFor(Type::member) { ... }` executes at build time and self-registers; the
 * plugin drains the registry and lowers each onto the enforce-proof backend, generating one `@BmcProof`
 * per case that the test task discovers and runs.
 *
 * Note the surface: the unbound method reference threads the receiver in as `self`; the precondition
 * lambda binds `(self, args...)`, the postcondition binds `(before, after, args..., ret)` so it relates
 * pre- and post-state directly (no `old(...)`); and `updatesOnly { ... }` names the frame.
 */

/**
 * A MUTATING instance contract that VERIFIES. `deposit` adds `amount` to the balance; the postcondition
 * relates the post-state to the pre-state (`after.balance == before.balance + amount`) and `updatesOnly`
 * declares the frame (only `balance` changes). The precondition keeps the values small enough that the
 * addition does not overflow.
 */
val deposit = contractFor(BankAccount::deposit) {
    whenPrecondition("amount in range") { self, amount -> self.balance in 0..1000 && amount in 0..1000 }
        .thenPostCondition("balance grew by amount") { before, after, amount, ret ->
            after.balance == before.balance + amount
        }
        .updatesOnly { self, amount -> self.balance }
}

/**
 * A `thenThrows` case: under a non-positive amount, `depositChecked` MUST throw. The case proves the
 * exceptional exit; there is no `ret`.
 */
val depositChecked = contractFor(BankAccount::depositChecked) {
    whenPrecondition("amount not positive") { self, amount -> amount <= 0 }
        .thenThrows<IllegalArgumentException>("rejects a non-positive amount")
}

/**
 * A STATIC contract that VERIFIES (threads no receiver - the first method argument is `self`). `clamp`
 * caps `value` at `cap`; the postcondition relates the result to the inputs.
 */
val clamp = contractFor(Transfers::clamp) {
    whenPrecondition("cap in range") { value, cap -> cap in 0..1000 && value in 0..2000 }
        .thenPostCondition("result is within the cap") { before, after, cap, ret ->
            ret <= cap && ret >= 0
        }
}

/**
 * A deliberately-FALSE contract, pinned `expect = REFUTED` IN the DSL so its self-asserting demo turns
 * green by refutation (no annotation wrapper). It claims `bump` only updates `value`, but the body also
 * increments `touches` - so the enforce frame check catches the unaccounted write and REFUTES.
 */
val frameViolation = contractFor(Counter::bump, expect = ExpectEnforce.REFUTED) {
    whenPrecondition("by in range") { self, by -> self.value in 0..1000 && by in 0..1000 }
        .thenPostCondition("value grew by `by`") { before, after, by, ret ->
            after.value == before.value + by
        }
        .updatesOnly { self, by -> self.value }
}
