package org.bmc4j.kotlin

import org.bmc4j.Bmc

/**
 * Kotlin sugar for the `domainSplit` DSL — partition a slow proof's claimed input domain into N
 * parallel slices plus one soundness cover check (see [Bmc.domainSplit] for the full semantics).
 *
 * ```
 * @BmcProof fun slowProof() {
 *     val x = Bmc.anyInt()
 *     domainSplit(x in -1_000_000..1_000_000) {   // the claimed domain
 *         slice(x < 0)
 *         slice(x == 0)
 *         slice(x > 0)
 *     }
 *     Bmc.check(property(x))                       // body runs once per slice
 * }
 * ```
 *
 * The `{ }` is purely visual grouping: this lowers to the identical flat marker sequence the Java form
 * uses (`Bmc.domainSplit(cond)` followed by one `Bmc.slice(c)` per child), which is exactly what the
 * rewriter extracts from the proof bytecode — the booleans are analysed, never executed.
 *
 * `inline`, so [register] is inlined at the call site (no lambda object, no `invokedynamic`): the
 * `slice(...)` calls land directly in the proof method's bytecode where the rewriter sees them, just
 * like `Bmc.check`/`Bmc.assume`. At most one `domainSplit` per proof — a second one, or a [slice] with
 * no enclosing `domainSplit`, is a processing-time error.
 */
inline fun domainSplit(overallCondition: Boolean, register: () -> Unit) {
    Bmc.domainSplit(overallCondition)
    register()
}

/**
 * Register one sub-domain of the enclosing [domainSplit]. The proof body is re-verified once under
 * `assume(condition)` for this slice. A marker — the boolean is analysed as bytecode, never executed.
 */
inline fun slice(condition: Boolean) {
    Bmc.slice(condition)
}
