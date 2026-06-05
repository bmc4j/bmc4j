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
}
