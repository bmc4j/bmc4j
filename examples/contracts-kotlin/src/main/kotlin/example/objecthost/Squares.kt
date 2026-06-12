package example.objecthost

/**
 * Production target for the **object-hosted predicate** example. `pyramid(n)` is a loop (the sum of
 * the first `n` squares) — costly to inline — so a caller at a tiny `unwind` only gets through by
 * reusing the contract's `@Ensures` instead of unrolling, exactly like `Triangles`.
 *
 * Nothing here is special to the object-hosted form: the *predicate hosting* is a property of the
 * test-side contract type (see `contracts.objecthost.SquaresContract`), not of the production code.
 * The point of this example is purely that the contract can be a plain Kotlin `object` whose
 * predicates are ordinary `fun`s — no `companion object`, no `@JvmStatic` per predicate.
 */
object Squares {

    @JvmStatic
    fun pyramid(n: Int): Int {
        var s = 0
        for (i in 1..n) s += i * i
        return s
    }

    /**
     * The soundness-guard target: `pyramid` plus one — strictly positive for any `n >= 0`. The
     * object-hosted contract claims `result <= 0` for it (a lie), so its enforce-proof passes BY
     * refutation and publishes no redirect. Annotating ≠ asserting holds in the object form too.
     */
    @JvmStatic
    fun bogus(n: Int): Int = pyramid(n) + 1
}

/** Identical body with NO contract, to show the same bound is too small without the summary. */
object SquaresNaive {

    @JvmStatic
    fun pyramid(n: Int): Int {
        var s = 0
        for (i in 1..n) s += i * i
        return s
    }
}
