package example.conformkt

/**
 * Analysis-only **model** of [example.conformkt.RunningTotal], written in Kotlin with the same
 * fully-qualified name so it shadows the real object on JBMC's analysis classpath (never on the test
 * runtime classpath).
 *
 * The real [example.conformkt.RunningTotal.sum] computes `1 + 2 + ... + n` with a **loop**; this
 * model computes the identical result with the loop-free **closed form** `n * (n + 1) / 2`. That is
 * the reason a model exists here: a downstream proof can call this cheap arithmetic instead of
 * unrolling the real loop on every use.
 *
 * Because the model and the real class are genuinely *different* implementations of the same
 * function, conformance is no longer trivial: `@ConformProofsAgainstModel(RunningTotal::class)`
 * asserts `sum(n) == n * (n + 1) / 2` against BOTH, so the real leg actually unrolls the loop and
 * checks it agrees with this closed form. Get the formula wrong here (e.g. `n * n / 2`) and the
 * model leg refutes; get the real loop wrong and the real leg refutes.
 */
object RunningTotal {

    const val MAX_N: Int = 8

    /** Closed form for the triangular number — no loop to unroll. */
    fun sum(n: Int): Int = n * (n + 1) / 2
}
