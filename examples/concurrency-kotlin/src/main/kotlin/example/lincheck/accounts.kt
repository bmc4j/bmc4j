package example.lincheck

/**
 * Three accounts that differ by exactly one protection each. An account has two
 * independent obligations:
 *
 *  - **Logic**: the overdraft *guard* — `withdraw` must refuse to drop the balance
 *    below zero, for every amount. A sequential property → caught by a `@BmcProof`.
 *  - **Concurrency**: the *lock* — `@Synchronized` so concurrent operations stay
 *    linearizable. An interleaving property → caught by Lincheck.
 *
 * Each buggy account drops one protection, and exactly the matching tool catches it —
 * while the other tool stays green and blind. `SafeAccount` keeps both.
 */

/** Sound LOGIC (has the guard), broken CONCURRENCY (no lock).
 *  `@BmcProof` passes; Lincheck finds the lost-update race. */
class RacyAccount {
    private var balance = 0

    fun deposit(amount: Int) {
        balance += amount
    }

    fun withdraw(amount: Int): Boolean =
        if (amount in 0..balance) {
            balance -= amount
            true
        } else {
            false
        }

    fun balance(): Int = balance
}

/** Sound CONCURRENCY (locked), broken LOGIC (no overdraft guard).
 *  Lincheck passes (it's linearizable — the negative balance is *consistent*);
 *  `@BmcProof` finds the overdraft. */
class OverdraftAccount {
    private var balance = 0

    @Synchronized
    fun deposit(amount: Int) {
        balance += amount
    }

    @Synchronized
    fun withdraw(amount: Int): Boolean {
        balance -= amount          // BUG: no `amount in 0..balance` check
        return true
    }

    @Synchronized
    fun balance(): Int = balance
}

/** Both protections present — green on both tools. */
class SafeAccount {
    private var balance = 0

    @Synchronized
    fun deposit(amount: Int) {
        balance += amount
    }

    @Synchronized
    fun withdraw(amount: Int): Boolean =
        if (amount in 0..balance) {
            balance -= amount
            true
        } else {
            false
        }

    @Synchronized
    fun balance(): Int = balance
}
