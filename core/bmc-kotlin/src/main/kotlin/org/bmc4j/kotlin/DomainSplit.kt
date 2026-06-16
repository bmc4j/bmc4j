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

/**
 * Register one sub-domain from N separate defining constraints (`c1 && ... && cN`). Semantically
 * EXACTLY the single-arg `slice(c1 && ... && cN)` — same partition, same cover — but the rewriter emits
 * each `c_k` as its OWN atomic `assume` on the slice run, never one compound `assume(c1 && ... && cN)`.
 *
 * That matters because CBMC's pre-SAT simplifier propagates an atomic assumed bound (`v >= lo`) to
 * prune downstream branches, but does NOT crack open a conjoined `&&` to recover the individual bounds
 * — so a range slice given as one `slice(lo <= v && v <= hi)` never has its bounds propagate, while
 * `slice(lo <= v, v <= hi)` does. Prefer this multi-arg form for range/bound slices.
 *
 * Fixed arity (2..8), not varargs: a `boolean[]` would add array reasoning to the formula — the
 * opposite of the goal. A marker, like [slice]: the booleans are analysed as bytecode, never executed.
 */
inline fun slice(c1: Boolean, c2: Boolean) {
    Bmc.slice(c1, c2)
}

/** Register one sub-domain from 3 conjoined constraints, each emitted as its own atomic assume. See [slice]. */
inline fun slice(c1: Boolean, c2: Boolean, c3: Boolean) {
    Bmc.slice(c1, c2, c3)
}

/** Register one sub-domain from 4 conjoined constraints, each emitted as its own atomic assume. See [slice]. */
inline fun slice(c1: Boolean, c2: Boolean, c3: Boolean, c4: Boolean) {
    Bmc.slice(c1, c2, c3, c4)
}

/** Register one sub-domain from 5 conjoined constraints, each emitted as its own atomic assume. See [slice]. */
inline fun slice(c1: Boolean, c2: Boolean, c3: Boolean, c4: Boolean, c5: Boolean) {
    Bmc.slice(c1, c2, c3, c4, c5)
}

/** Register one sub-domain from 6 conjoined constraints, each emitted as its own atomic assume. See [slice]. */
inline fun slice(c1: Boolean, c2: Boolean, c3: Boolean, c4: Boolean, c5: Boolean, c6: Boolean) {
    Bmc.slice(c1, c2, c3, c4, c5, c6)
}

/** Register one sub-domain from 7 conjoined constraints, each emitted as its own atomic assume. See [slice]. */
inline fun slice(c1: Boolean, c2: Boolean, c3: Boolean, c4: Boolean, c5: Boolean, c6: Boolean,
                 c7: Boolean) {
    Bmc.slice(c1, c2, c3, c4, c5, c6, c7)
}

/** Register one sub-domain from 8 conjoined constraints, each emitted as its own atomic assume. See [slice]. */
inline fun slice(c1: Boolean, c2: Boolean, c3: Boolean, c4: Boolean, c5: Boolean, c6: Boolean,
                 c7: Boolean, c8: Boolean) {
    Bmc.slice(c1, c2, c3, c4, c5, c6, c7, c8)
}
