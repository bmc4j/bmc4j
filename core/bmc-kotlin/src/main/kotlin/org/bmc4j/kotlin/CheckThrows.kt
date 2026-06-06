package org.bmc4j.kotlin

import org.bmc4j.Bmc

/**
 * `true` iff [block] throws a [T]. Any other `Throwable` propagates — under BMC an uncaught
 * exception is a violation, so a wrong-typed throw fails the proof with its own trace instead of
 * being silently folded into `false`.
 *
 * This is the composable form, for *iff*-shaped laws relating the throw to the input domain:
 *
 * ```
 * val raw = Bmc.anyInt()
 * Bmc.check(throws<IllegalArgumentException> { Port(raw) } == (raw !in 1..65535))
 * ```
 *
 * For the plain "this must throw" assertion, use [checkThrows]. Like [assumeValid], this is
 * `inline`, so [block] is inlined at the call site — no lambda object and no `invokedynamic` —
 * which keeps the whole thing analysable by JBMC.
 */
inline fun <reified T : Throwable> throws(block: () -> Unit): Boolean {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) {
            return true
        }
        throw e
    }
    return false
}

/**
 * Check that [block] throws a [T] for every allowed input — the BMC analogue of kotlin.test's
 * `assertFailsWith`, replacing the hand-rolled `try { … } catch { threw = true }; check(threw)`
 * idiom:
 *
 * ```
 * Bmc.assume(lo > hi)                                          // an empty range...
 * checkThrows<IllegalArgumentException> { 0.coerceIn(lo, hi) } // ...must be rejected
 * ```
 *
 * A throw of any *other* type propagates and fails the proof with its own trace (see [throws]).
 */
inline fun <reified T : Throwable> checkThrows(block: () -> Unit) {
    Bmc.check(throws<T>(block), "expected exception was not thrown")
}
