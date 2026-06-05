package example.integeroverflow

/** Two tiny routines: one that intuition gets wrong, one it gets right. */
object Numbers {

    /** Absolute value. BUG: `abs(Int.MIN_VALUE)` overflows back to a negative. */
    fun abs(x: Int): Int = if (x < 0) -x else x

    /** The larger of two values. Correct for all inputs. */
    fun max(a: Int, b: Int): Int = if (a >= b) a else b
}
