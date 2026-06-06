package example.exceptions

/**
 * A domain exception that CARRIES its diagnosis as typed fields. Under BMC the exception is an
 * ordinary object — its constructor runs symbolically — so those fields are provable like any
 * other result. That matters because error paths are the least-tested code there is: a wrong
 * shortfall in a refusal message survives every happy-path test.
 */
class InsufficientFunds(val balance: Int, val requested: Int) : RuntimeException() {
    val shortfall: Int = requested - balance
}

/** BUG: refuses correctly, but constructs the exception with the arguments SWAPPED. */
fun withdraw(balance: Int, amount: Int): Int {
    if (amount > balance) throw InsufficientFunds(amount, balance)
    return balance - amount
}

/** Fixed: the refusal reports the numbers it actually refused on. */
fun safeWithdraw(balance: Int, amount: Int): Int {
    if (amount > balance) throw InsufficientFunds(balance, amount)
    return balance - amount
}
