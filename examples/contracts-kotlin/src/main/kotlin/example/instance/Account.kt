package example.instance

/**
 * A pure instance method in Kotlin: [project] reads the receiver's `balance` but never mutates it
 * (the account is immutable — `val`). Its contract lives test-side in `contracts.instance`;
 * production code carries no bmc references. The receiver is threaded into the predicates as `self`.
 */
class Account(val balance: Int) {

    /**
     * The balance after applying `amount` as a sequence of unit steps — a pure projection over `this`
     * and the argument (reads `this.balance`, mutates nothing). The loop is artificial but real: it
     * makes the method costly to inline, so a caller at a tiny `unwind` can only get through by reusing
     * the contract instead of unrolling it.
     */
    fun project(amount: Int): Int {
        var result = balance
        for (i in 0 until amount) result += 1
        return result
    }

    /** Identical projection — a second pure instance method carrying a deliberately-false demo
     *  contract (the contract's `@Ensures` is the lie, not this body). */
    fun projectAgain(amount: Int): Int = project(amount)
}

/** No-contract twin, to show the same bound is too small without the summary. */
class AccountNaive(val balance: Int) {
    fun project(amount: Int): Int {
        var result = balance
        for (i in 0 until amount) result += 1
        return result
    }
}
