package example.conformkt

/**
 * The **real** implementation of a triangular-number / running-total helper: `sum(n)` adds up
 * `1 + 2 + ... + n` with an honest, bounded **loop**.
 *
 * That loop is the whole point of the [example.conformkt] concept. It is fully analyzable, but a
 * downstream proof that calls `sum` has to *unroll* the loop every time it touches it (up to `n`
 * iterations) — exactly the cost you write a model to avoid. The `src/bmcModel` model
 * ([example.conformkt.RunningTotal] there) replaces this loop with the closed form
 * `n * (n + 1) / 2`, so callers analyse the cheap arithmetic instead of unrolling.
 *
 * `@ConformProofsAgainstModel(RunningTotal::class)` is what keeps that substitution honest: the
 * conformance proof asserts `sum(n) == n * (n + 1) / 2`, runs once against this real loop (which
 * must be unrolled and is genuinely checked) and once against the model (the closed form), and both
 * legs must verify. If this loop were wrong — say it added `i + 1` — the real leg would REFUTE
 * against the model's closed form.
 */
object RunningTotal {

    /** Inputs are kept in `0..[MAX_N]` so the real loop fully unrolls within the proof's bound. */
    const val MAX_N: Int = 8

    /** Sum `1 + 2 + ... + n` the long way, with a bounded loop. */
    fun sum(n: Int): Int {
        var s = 0
        for (i in 1..n) s += i
        return s
    }
}
