package example.valueclasses

/**
 * A value class whose invariant is enforced by the constructor with an exception — the common
 * Kotlin pattern. JBMC runs the `init {}` during analysis, so the invariant is real, not assumed.
 */
@JvmInline
value class Port(val number: Int) {
    init {
        require(number in 1..65535) { "port out of range: $number" }
    }
}

/** BUG: at the maximum port, `number + 1` is 65536, which the Port constructor rejects. */
fun next(p: Port): Port = Port(p.number + 1)

/** Fixed: the maximum port is its own successor (saturating), so construction never fails. */
fun safeNext(p: Port): Port = if (p.number == 65535) p else Port(p.number + 1)
