package proofs.kotlinmath

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * Model proofs (axis 2) for the `kotlin.math.MathKt` non-inline residue. The common math functions
 * (abs/min/max/sqrt/pow/ceil/floor) are @InlineOnly — they inline to java.lang.Math directly and need
 * no model — so these laws pin the genuinely non-inline JVM members the model supplies: the Int/Long
 * `sign` property and `roundToInt`/`roundToLong`. Concrete inputs (no symbolic doubles, per policy).
 */
class KotlinMathLaws {

    /** Int.sign over a symbolic value: -1 / 0 / +1 by sign. */
    @BmcProof
    fun int_sign_trichotomy() {
        val n = Bmc.anyInt(-100, 100)
        val s = n.sign
        Bmc.check(
            (n > 0 && s == 1) ||
                (n == 0 && s == 0) ||
                (n < 0 && s == -1),
        )
    }

    /** Long.sign of a concrete negative value. */
    @BmcProof
    fun long_sign_negative() {
        Bmc.check((-42L).sign == -1)
    }

    /** roundToInt rounds half-up to the nearest int. */
    @BmcProof
    fun roundToInt_half_up() {
        Bmc.check(2.5.roundToInt() == 3 && 2.4.roundToInt() == 2)
    }

    /** roundToLong rounds half-up to the nearest long. */
    @BmcProof
    fun roundToLong_half_up() {
        Bmc.check(3.5.roundToLong() == 4L && 3.49.roundToLong() == 3L)
    }
}
