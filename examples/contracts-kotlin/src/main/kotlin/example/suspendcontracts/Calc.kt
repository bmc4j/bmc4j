package example.suspendcontracts

/**
 * `suspend` functions carrying contracts. "Most Kotlin is written as `suspend`", so a contract story
 * that excluded suspend would exclude most Kotlin — bmc4j contracts therefore cover suspend targets
 * under the same **immediate-dispatch** idealization the coroutine proofs use (see
 * [`examples/concurrency-kotlin`]): a suspend call completes linearly in one call (every nested
 * suspension point resolves synchronously), so `@Requires` is checked at entry and `@Ensures` at
 * completion, exactly as for an ordinary value-returning method.
 *
 * The target lives on an `object` with `@JvmStatic` (the Kotlin-idiomatic nameable static, like the
 * `basics` concept), so `@BmcContractsFor(Calcs::class)` is expressible.
 */
object Calcs {

    /** A suspend helper with a real suspension point in [stepTo]'s loop body: `inner(acc)` is itself a
     *  suspend call, so the compiler lowers [stepTo] into a state machine that re-enters per iteration. */
    @JvmStatic
    suspend fun inner(x: Int): Int = x + 1

    /**
     * Steps a running accumulator up by one `n` times via the suspend helper, so `stepTo(n) == n`. The
     * loop (and its per-iteration suspension point) makes the body costly to inline: a caller at a tiny
     * `unwind` only gets through by reusing the contract's `@Ensures` instead of unrolling.
     */
    @JvmStatic
    suspend fun stepTo(n: Int): Int {
        var acc = 0
        var i = 0
        while (i < n) {
            acc = inner(acc)
            i++
        }
        return acc
    }

    /** BUG: the value computed after the suspension point is off by one. A contract asserting
     *  `stepBuggy(n) == n` is REFUTED — the enforce-proof catches the post-suspension logic bug. */
    @JvmStatic
    suspend fun stepBuggy(n: Int): Int {
        var acc = 0
        var i = 0
        while (i < n) {
            acc = inner(acc)
            i++
        }
        return acc + 1
    }
}

/** No-contract twin, to show the same tiny bound is too small without the summary. */
object CalcsNaive {

    @JvmStatic
    suspend fun inner(x: Int): Int = x + 1

    @JvmStatic
    suspend fun stepTo(n: Int): Int {
        var acc = 0
        var i = 0
        while (i < n) {
            acc = inner(acc)
            i++
        }
        return acc
    }
}
