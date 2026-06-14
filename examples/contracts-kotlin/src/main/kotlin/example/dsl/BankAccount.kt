package example.dsl

/**
 * A MUTATING instance target for the contracts DSL. [deposit] mutates the receiver's `balance` (the
 * contracted method may mutate freely; the contract's `updatesOnly` captures what it changes, and its
 * postcondition relates the pre-state `before` to the post-state `after`). The DSL contract lives
 * test-side as a top-level `val` in `dsl.BankAccountContracts`; production code carries no bmc reference.
 */
class BankAccount(var balance: Int) {

    /** Add [amount] to the balance. Mutates `this.balance` and nothing else. */
    fun deposit(amount: Int) {
        balance += amount
    }

    /** Reject a non-positive amount; otherwise add it. The `thenThrows` case contracts the rejection. */
    fun depositChecked(amount: Int) {
        require(amount > 0) { "amount must be positive" }
        balance += amount
    }
}

/**
 * A second target whose body writes a field OUTSIDE what the (deliberately wrong) contract claims it
 * updates - the frame-violation demo. `bump` changes BOTH `value` and `touches`, but the contract
 * declares `updatesOnly { self.value }`, so the enforce frame check catches the unaccounted write to
 * `touches` and REFUTES.
 */
class Counter(var value: Int, var touches: Int) {

    fun bump(by: Int) {
        value += by
        touches += 1
    }
}
