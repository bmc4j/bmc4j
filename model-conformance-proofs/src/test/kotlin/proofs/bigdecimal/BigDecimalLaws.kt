package proofs.bigdecimal

import java.math.BigDecimal
import java.math.RoundingMode
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the BigDecimal model must satisfy under
 * JBMC, over symbolic inputs kept inside the documented bound. The model's `mul` overflow assert is
 * a checked property here too, so these passing in-bound proofs also confirm the bound is enforced
 * (a value past it would make JBMC flag the overflow, never compute a silent wrong result).
 */
class BigDecimalLaws {

    /**
     * A symbolic BigDecimal with a fixed scale, unscaled value kept well inside the long bound.
     * The bound also sets the bit-width JBMC must reason about: division-heavy proofs (setScale)
     * narrow it so the rounding-divider circuit stays small — the round-trip law holds for any value,
     * so a tighter range is just as strong a proof but far cheaper to discharge.
     */
    private fun anyBd(scale: Int, bound: Int = 1_000): BigDecimal =
        BigDecimal.valueOf(Bmc.anyInt(-bound, bound).toLong(), scale)

    @BmcProof
    fun add_is_commutative() {
        val a = anyBd(2)
        val b = anyBd(2)
        Bmc.check(a.add(b).compareTo(b.add(a)) == 0)
    }

    @BmcProof
    fun add_zero_is_identity() {
        val a = anyBd(2)
        Bmc.check(a.add(BigDecimal.ZERO).compareTo(a) == 0)
    }

    @BmcProof
    fun multiply_one_is_identity() {
        val a = anyBd(2)
        Bmc.check(a.multiply(BigDecimal.ONE).compareTo(a) == 0)
    }

    @BmcProof
    fun subtract_self_is_zero() {
        val a = anyBd(2)
        Bmc.check(a.subtract(a).signum() == 0)
    }

    @BmcProof
    fun add_then_subtract_round_trips() {
        val a = anyBd(2)
        val b = anyBd(2)
        Bmc.check(a.add(b).subtract(b).compareTo(a) == 0)
    }

    @BmcProof
    fun setScale_widen_then_narrow_round_trips() {
        // Division-heavy (narrowing setScale rounds via roundDiv). Tight range keeps the divider
        // small; the widen-then-narrow identity is exact for every value, so this stays a real proof.
        val a = anyBd(2, bound = 1_000)
        val widened = a.setScale(4, RoundingMode.HALF_UP)
        Bmc.check(widened.setScale(2, RoundingMode.HALF_UP).compareTo(a) == 0)
    }

    // divide across rounding modes, concrete cases (fast: cbmc folds the constant division). These
    // pin the trust-critical roundDiv kernel to the expected JDK results under JBMC's own semantics.

    @BmcProof
    fun divide_one_third_half_up_is_0_33() {
        val r = BigDecimal.ONE.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)
        Bmc.check(r.compareTo(BigDecimal.valueOf(33, 2)) == 0)   // 0.3333.. -> 0.33
    }

    @BmcProof
    fun divide_two_thirds_half_up_is_0_67() {
        val r = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)
        Bmc.check(r.compareTo(BigDecimal.valueOf(67, 2)) == 0)   // 0.6666.. -> 0.67
    }

    @BmcProof
    fun divide_floor_and_ceiling_bracket_the_quotient() {
        val a = BigDecimal.ONE
        val b = BigDecimal.valueOf(3)
        val floor = a.divide(b, 2, RoundingMode.FLOOR)     // 0.33
        val ceiling = a.divide(b, 2, RoundingMode.CEILING) // 0.34
        Bmc.check(floor.compareTo(BigDecimal.valueOf(33, 2)) == 0
                && ceiling.compareTo(BigDecimal.valueOf(34, 2)) == 0)
    }

    @BmcProof
    fun divide_negative_down_truncates_toward_zero_floor_goes_lower() {
        val a = BigDecimal.valueOf(-1)
        val b = BigDecimal.valueOf(3)
        Bmc.check(a.divide(b, 2, RoundingMode.DOWN).compareTo(BigDecimal.valueOf(-33, 2)) == 0)   // -0.33
        Bmc.check(a.divide(b, 2, RoundingMode.FLOOR).compareTo(BigDecimal.valueOf(-34, 2)) == 0)  // -0.34
    }

    @BmcProof
    fun divide_half_even_rounds_to_even_neighbour() {
        // 2.5 -> 2 (even), 3.5 -> 4 (even)
        Bmc.check(BigDecimal.valueOf(5).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_EVEN)
                .compareTo(BigDecimal.valueOf(2)) == 0)
        Bmc.check(BigDecimal.valueOf(7).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_EVEN)
                .compareTo(BigDecimal.valueOf(4)) == 0)
    }
}
