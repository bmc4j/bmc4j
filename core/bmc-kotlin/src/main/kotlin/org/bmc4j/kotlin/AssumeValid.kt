package org.bmc4j.kotlin

import org.bmc4j.Bmc

/**
 * Produce a symbolic instance that is **valid by construction**: run [construct], and if it throws a
 * [RuntimeException] (e.g. a value class / data class `init { require(...) }`, or any constructor
 * that validates with exceptions), prune that path so the proof only ever sees inputs the
 * constructor accepts.
 *
 * The idea: validation expressed as exceptions *is* the spec, so we reuse it instead of restating
 * an invariant. "Assume valid" == "assume construction did not throw".
 *
 * ```
 * @JvmInline value class Port(val n: Int) { init { require(n in 1..65535) } }
 *
 * @BmcProof fun routing_is_in_range() {
 *     val p = assumeValid { Port(Bmc.anyInt()) }   // only 1..65535 survive
 *     Bmc.check(p.n in 1..65535)                    // ...so this holds
 * }
 * ```
 *
 * This is `inline`, so [construct] is inlined at the call site — no lambda object and no
 * `invokedynamic` — which keeps the whole thing analysable by JBMC.
 *
 * Soundness note: like [Bmc.assume], this *assumes* the property it names. You are trusting the
 * constructor's checks to fully characterise validity; whatever it does not reject, the proof will
 * still consider valid.
 */
inline fun <T> assumeValid(construct: () -> T): T {
    try {
        return construct()
    } catch (e: RuntimeException) {
        Bmc.assumeUnreachable()
        throw e // unreachable once the path above is pruned; satisfies the type checker
    }
}
