package example.timeout

/**
 * A deliberately solver-heavy routine, used by the `timeout` concept to demonstrate the **UNKNOWN**
 * verdict. Nested loops over wide symbolic inputs blow up the bit-vector formula JBMC hands the SAT
 * solver, so with a large unwind the proof can't be decided in a small time budget — which is exactly
 * when you want a timeout instead of a hung build.
 */
object Heavy {

    /**
     * A quadratic accumulation whose result depends on every product `i * j` of two wide symbolic
     * inputs. Unwound to a high bound this produces a large, hard-to-solve formula.
     */
    fun quadraticMix(a: Int, b: Int): Long {
        var acc = 0L
        for (i in 0 until a) {
            for (j in 0 until b) {
                acc += i.toLong() * j + (acc xor (i + j).toLong())
            }
        }
        return acc
    }
}
