package example.constructreceiver

/**
 * Subjects for the receiver-construction proofs. The point of interest is that the PROOFS read these
 * through INSTANCE members whose values come from FIELD INITIALIZERS that run in the class constructor
 * `<init>` — exactly what jbmc skips when it synthesises a nondet `this`. With receiver construction on,
 * the proofs see the initialised values.
 */
object Receiver {
    /** Index into a caller-supplied table; in range for 0..size-1. */
    fun bandLength(table: IntArray, index: Int): Int = table[index]

    /** True only when the looked-up scalar equals its initializer; lets a proof pin a scalar's value. */
    fun isEight(n: Int): Boolean = n == 8
}
