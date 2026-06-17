package example.conformkt

/**
 * A tiny, fully analyzable audio-volume helper. Unlike [example.custommodelskt.ExchangeRates] (a
 * live service that throws and *must* be modeled), this class carries its **real, tractable
 * implementation** — there is nothing un-analyzable about clamping an integer into `0..[MAX]`.
 *
 * That is exactly the point: it exists so the `src/bmcModel` model of it can be **conformed against
 * the real class** with [org.bmc4j.ConformProofsAgainstModel]. The real leg of that conformance run
 * analyses *this* code, so it has to reach the same verdict the model leg does.
 */
object Volume {

    /** The loudest volume the mixer accepts. */
    const val MAX: Int = 100

    /**
     * Apply a signed [delta] to the [current] volume and clamp the result into `0..[MAX]`.
     *
     * The arithmetic is done in [Long] so a hostile `delta` near [Int.MAX_VALUE] can't overflow
     * before it is clamped — a faithful model must reproduce that, or the real leg refutes where the
     * model verified.
     */
    fun adjust(current: Int, delta: Int): Int {
        val raw = current.toLong() + delta.toLong()
        return when {
            raw < 0L -> 0
            raw > MAX.toLong() -> MAX
            else -> raw.toInt()
        }
    }
}
