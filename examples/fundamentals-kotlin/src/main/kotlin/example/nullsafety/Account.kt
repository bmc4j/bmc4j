package example.nullsafety

data class Account(val parent: Account?, val balance: Int)

object Accounts {
    fun parentBalance(a: Account): Int = a.parent!!.balance
    fun parentBalanceOrZero(a: Account): Int = a.parent?.balance ?: 0
}
