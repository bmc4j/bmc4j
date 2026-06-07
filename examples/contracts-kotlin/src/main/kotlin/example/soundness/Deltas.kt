package example.soundness

/**
 * The soundness guard, Kotlin edition: annotating a method is not the same as proving it. `delta`
 * carries a FALSE contract (`@Ensures result >= 0`, a lie when `a < b`); `absDelta` is honest. The
 * auto-generated enforce-proof — not a hand-written one — is what catches the false claim and turns
 * the build red, so "annotate ≠ proven" is structural.
 */
object Deltas {

    @JvmStatic
    fun delta(a: Int, b: Int): Int = a - b

    @JvmStatic
    fun absDelta(a: Int, b: Int): Int = if (a >= b) a - b else b - a
}
