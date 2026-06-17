package example.conformkt

/**
 * Analysis-only **model** of [example.conformkt.Volume], written in Kotlin with the same
 * fully-qualified name so it shadows the real object on JBMC's analysis classpath (never on the test
 * runtime classpath).
 *
 * This is a `conformant` model: it claims to be a **faithful** stand-in for the real implementation,
 * not an intentional divergence. The conformance proof (`@ConformProofsAgainstModel(Volume::class)`)
 * holds us to that claim — it runs the proof once against this model and once against the real
 * [example.conformkt.Volume], and both must reach the expected verdict. If this model dropped the
 * overflow-safe `Long` arithmetic (e.g. computed `current + delta` in `Int`), it could still VERIFY
 * the clamp bound here while the real class — which the proof would then *not* be testing — diverges:
 * conforming it keeps the two honest about each other.
 */
object Volume {

    const val MAX: Int = 100

    fun adjust(current: Int, delta: Int): Int {
        val raw = current.toLong() + delta.toLong()
        return when {
            raw < 0L -> 0
            raw > MAX.toLong() -> MAX
            else -> raw.toInt()
        }
    }
}
