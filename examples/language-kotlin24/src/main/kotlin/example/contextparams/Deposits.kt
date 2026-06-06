package example.contextparams

/**
 * A per-tenant limit carried as a context parameter (stable in Kotlin 2.4) instead of being
 * threaded through every signature. On the JVM a context parameter compiles to an extra leading
 * value parameter — a plain method JBMC analyses like any other.
 */
class Limits(val max: Int) {
    init {
        require(max in 0..65535) { "max out of range: $max" }
    }
}

/** BUG: clamps the top but trusts the caller on the bottom — negative deposits pass through. */
context(limits: Limits)
fun clampDeposit(amount: Int): Int = if (amount > limits.max) limits.max else amount

/** Fixed: both bounds enforced — the result is always a valid amount. */
context(limits: Limits)
fun safeClampDeposit(amount: Int): Int = amount.coerceIn(0, limits.max)
