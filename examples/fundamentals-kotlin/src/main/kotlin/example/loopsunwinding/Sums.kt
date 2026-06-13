package example.loopsunwinding

/** A loop whose result we prove against a closed form. */
object Sums {

    /** Sum 1..n. Correct, and equal to `n*(n+1)/2`. A plain counted loop (no range object). */
    fun sumTo(n: Int): Int {
        var total = 0
        var i = 1
        while (i <= n) {
            total += i
            i++
        }
        return total
    }

    /**
     * Count down to zero from a SYMBOLIC start: the trip count is exactly `start`, which is a
     * symbolic input here — so no FIXED unwind bound can ever cover it. The auto-unwind climb keeps
     * firing the unwinding assertion at this loop right up to the cap, which is precisely the
     * data-dependent-bound signal the diagnostic exists to name.
     */
    fun countDown(start: Int): Int {
        var n = start
        var steps = 0
        while (n > 0) {
            n--
            steps++
        }
        return steps
    }
}
