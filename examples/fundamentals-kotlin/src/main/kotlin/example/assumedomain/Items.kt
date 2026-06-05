package example.assumedomain

/** Array access — the function itself is correct; the proof's `assume` is what matters. */
object Items {

    /** Return the element at [i]. Safe exactly when `0 <= i < a.size`. */
    fun elementAt(a: IntArray, i: Int): Int = a[i]
}
