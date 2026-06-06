package example.backingfields

/**
 * Explicit backing fields (stable in Kotlin 2.4): the public property exposes the read-only
 * view while the backing field keeps the mutable type — no second `_private` property. On the
 * JVM this is an ordinary field (of the backing type) plus a getter (of the public type), so
 * nothing new reaches JBMC.
 */
class SessionLog {
    val durations: List<Int>
        field = mutableListOf()

    /** BUG: records the raw duration — clock skew can hand us a negative one. */
    fun record(duration: Int) {
        durations.add(duration) // inside the class, `durations` resolves to the MutableList field
    }

    /** Fixed: clamps at the boundary, so the log's non-negativity invariant holds. */
    fun recordSafe(duration: Int) {
        durations.add(if (duration < 0) 0 else duration)
    }

    fun totalTime(): Int = durations.sum()
}
