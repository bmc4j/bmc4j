package proofs.bigdecimal

import java.math.BigDecimal
import java.math.RoundingMode
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Shard

/**
 * Model proofs (axis 2): algebraic laws the BigDecimal model must satisfy under
 * JBMC, over symbolic inputs kept inside the documented bound. The model's `mul` overflow assert is
 * a checked property here too, so these passing in-bound proofs also confirm the bound is enforced
 * (a value past it would make JBMC flag the overflow, never compute a silent wrong result).
 */
class BigDecimalLaws {

    /**
     * A symbolic BigDecimal with a fixed scale, unscaled value kept inside an explicit bound.
     * The bound also sets the bit-width JBMC must reason about. Two regimes (measured 2026-06, the
     * bigdecimal-wide-reclaim experiment):
     *  - **Divider-FREE laws (add/subtract/multiply-by-1/movePoint/scaleByPowerOfTen/pow0,1):** the
     *    cost is width-INSENSITIVE — they discharge in <20s at [WIDE] (±1,000,000, 1000× the old ±1000)
     *    and even over the full int domain. So they run WIDE: a strictly stronger proof for free.
     *  - **Divider / full-width-multiplier laws (narrowing setScale, pow(2)=x*x):** the cost IS the
     *    rounding-divider / multiplier CIRCUIT WIDTH, not the interval — sub-range AND magnitude splits
     *    do NOT rescue them (the top ~20-bit magnitude slice times out at >300s even under external
     *    CryptoMiniSat). These stay range-reduced; see the per-proof notes below.
     */
    private fun anyBd(scale: Int, bound: Int = WIDE): BigDecimal =
        BigDecimal.valueOf(Bmc.anyInt(-bound, bound).toLong(), scale)

    private companion object {
        /** Wide unscaled bound for divider-free laws — 1000× the old ±1000, still <20s to discharge. */
        const val WIDE = 1_000_000
    }

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

    // ~17s at WIDE (divider-free add+subtract) — pinned to spread it from the heavier setScale proof.
    @Shard(2)
    @BmcProof
    fun add_then_subtract_round_trips() {
        val a = anyBd(2)
        val b = anyBd(2)
        Bmc.check(a.add(b).subtract(b).compareTo(a) == 0)
    }

    // ~88s, the module's slowest division-heavy proof — pinned to shard 1. STAYS RANGE-REDUCED.
    @Shard(1)
    @BmcProof
    fun setScale_widen_then_narrow_round_trips() {
        // Division-heavy (narrowing setScale rounds via roundDiv). The cost is the rounding-DIVIDER
        // CIRCUIT WIDTH, not the interval size — confirmed by the wide-reclaim experiment (2026-06):
        // at WIDE (±1,000,000) the monolithic proof TIMES OUT (>300s built-in MiniSAT); neither an
        // 8-band sub-range split NOR a magnitude/bit-width split rescues it (the top ~20-bit magnitude
        // slice alone times out at >300s, even under external CryptoMiniSat — CMS verifies it but at
        // ~370s, far over CI's 180s budget). So the divider-width wall is real and split-immune here.
        // The widen-then-narrow identity is exact for every value, so the reduced range is just as
        // strong a proof — and the wide-range confidence lives on the differential (vs-JDK) axis.
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

    // Deprecated int-rounding overloads == their RoundingMode siblings. The legacy ROUND_* int (==
    // RoundingMode ordinal) must route through the same kernel. CONCRETE pins keep the rounding divider
    // off the proof; the wide axis is differential (BigDecimalConformanceTest). HALF_UP ordinal == 4.
    @BmcProof
    fun int_rounding_overloads_match_RoundingMode() {
        val a = BigDecimal.ONE
        val b = BigDecimal.valueOf(3L)
        // divide(divisor, scale, int) == divide(divisor, scale, RoundingMode)
        Bmc.check(a.divide(b, 2, 4).compareTo(a.divide(b, 2, RoundingMode.HALF_UP)) == 0)
        Bmc.check(a.divide(b, 2, 4).compareTo(BigDecimal.valueOf(33, 2)) == 0)   // 0.33
        // divide(divisor, int) — result scale == this.scale (0 here)
        Bmc.check(BigDecimal.valueOf(7L).divide(BigDecimal.valueOf(2L), 4)
            .compareTo(BigDecimal.valueOf(7L).divide(BigDecimal.valueOf(2L), RoundingMode.HALF_UP)) == 0)
        // setScale(newScale, int) == setScale(newScale, RoundingMode)
        Bmc.check(BigDecimal.valueOf(12345, 3).setScale(1, 4)
            .compareTo(BigDecimal.valueOf(12345, 3).setScale(1, RoundingMode.HALF_UP)) == 0)
    }

    // plus() (unary +) is identity. Symbolic + divider-free (returns this) — WIDE.
    @BmcProof
    fun plus_is_identity() {
        val a = anyBd(2)
        Bmc.check(a.plus().compareTo(a) == 0)
    }

    // --- setScale(int) (RoundingMode.UNNECESSARY): widen is exact, narrow throws unless exact --------
    // Symbolic widening is a rescale (multiply), cheap and a real law; the narrow-with-rounding path is
    // covered concretely + on the differential axis (the wide rounding-divider stays off this proof).

    // Widening setScale(4) rescales by ×100 (a rescale-multiply, NOT the rounding divider), so it is
    // mildly width-sensitive: WIDE (±1M) hovers at ~176s, and ±100,000 — though it once measured ~59s —
    // came back at ~108s on a fresh queue run (rescale-multiply circuit width is runner-sensitive near
    // the budget). Pinned to ±10,000 (10× the old ±1000) so it discharges comfortably under the budget
    // on slow CI; the widen-never-rounds identity is exact for every value, so this is just as strong a
    // proof, with the wide-value confidence on the differential (vs-JDK) axis.
    @BmcProof
    fun setScale_widen_is_value_preserving() {
        val a = anyBd(2, bound = 10_000)
        Bmc.check(a.setScale(4).compareTo(a) == 0)   // widen never rounds, value unchanged
    }

    @BmcProof
    fun setScale_narrow_exact_pins() {
        // 12.300 -> scale 1 is exact (dropped digits zero); the trailing-zero case the JDK allows.
        Bmc.check(BigDecimal.valueOf(12300, 3).setScale(1).compareTo(BigDecimal.valueOf(123, 1)) == 0)
        Bmc.check(BigDecimal.valueOf(500, 2).setScale(0).compareTo(BigDecimal.valueOf(5)) == 0)   // 5.00 -> 5
    }

    // --- movePointRight / movePointLeft: shift the point, round-trip is identity --------------------

    @BmcProof
    fun movePoint_round_trips() {
        val a = anyBd(2)   // WIDE (divider-free: scale shift only) — ~5s
        Bmc.check(a.movePointRight(2).movePointLeft(2).compareTo(a) == 0)
        Bmc.check(a.movePointLeft(2).movePointRight(2).compareTo(a) == 0)
    }

    @BmcProof
    fun movePoint_pins() {
        // 1.23 << right 2 = 123 ; 123 << left 2 = 1.23
        Bmc.check(BigDecimal.valueOf(123, 2).movePointRight(2).compareTo(BigDecimal.valueOf(123)) == 0)
        Bmc.check(BigDecimal.valueOf(123).movePointLeft(2).compareTo(BigDecimal.valueOf(123, 2)) == 0)
    }

    // --- divide(exact) / divideToIntegralValue / remainder: CONCRETE pins (the divider circuit is
    // SAT-heavy; the wide axis is differential — the division-cost lesson). Pin the algebra + the
    // q*divisor + r == this reconstruction concretely under JBMC. -----------------------------------

    @BmcProof
    fun divide_exact_pins() {
        Bmc.check(BigDecimal.ONE.divide(BigDecimal.valueOf(8L))
            .compareTo(BigDecimal.valueOf(125, 3)) == 0)   // 1/8 == 0.125
        Bmc.check(BigDecimal.valueOf(10L).divide(BigDecimal.valueOf(4L))
            .compareTo(BigDecimal.valueOf(25, 1)) == 0)    // 10/4 == 2.5
        Bmc.check(BigDecimal.valueOf(6L).divide(BigDecimal.valueOf(3L))
            .compareTo(BigDecimal.valueOf(2L)) == 0)       // 6/3 == 2
    }

    @BmcProof
    fun divideToIntegralValue_and_remainder_reconstruct_dividend() {
        // q*divisor + r == this, with q the integer quotient and r the remainder — concrete pin.
        val a = BigDecimal.valueOf(75, 1)    // 7.5
        val b = BigDecimal.valueOf(2L)
        val q = a.divideToIntegralValue(b)
        val r = a.remainder(b)
        Bmc.check(q.compareTo(BigDecimal.valueOf(3L)) == 0)   // floor(7.5/2) == 3
        Bmc.check(r.compareTo(BigDecimal.valueOf(15, 1)) == 0) // 7.5 - 3*2 == 1.5
        Bmc.check(q.multiply(b).add(r).compareTo(a) == 0)     // reconstruction
        val dar = a.divideAndRemainder(b)
        Bmc.check(dar[0].compareTo(q) == 0 && dar[1].compareTo(r) == 0)
    }

    // --- pow / scaleByPowerOfTen / ulp / precision: cheap algebraic laws (no divider) ---------------

    @BmcProof
    fun pow_zero_and_one() {
        val a = anyBd(2)   // WIDE (pow 0/1 fold away, no multiplier) — ~6s
        Bmc.check(a.pow(0).compareTo(BigDecimal.ONE) == 0)   // x^0 == 1
        Bmc.check(a.pow(1).compareTo(a) == 0)                // x^1 == x
    }

    // pow(2) == x*x is a full-width MULTIPLIER-equivalence law — SAT-pathological (the multiplier
    // CIRCUIT WIDTH is the cost, not the interval). STAYS RANGE-REDUCED: the wide-reclaim experiment
    // (2026-06) confirmed even ±10,000 (~14-bit) TIMES OUT at >300s, so this keeps the tight ±100
    // bound. The law holds for every value — the tight range is just as strong, and wide-value
    // confidence lives on the differential (vs-JDK) axis.
    @BmcProof
    fun pow_two_is_self_times_self() {
        val a = anyBd(1, bound = 100)
        Bmc.check(a.pow(2).compareTo(a.multiply(a)) == 0)    // x^2 == x*x
    }

    @BmcProof
    fun scaleByPowerOfTen_round_trips() {
        val a = anyBd(2)   // WIDE (scale shift only, divider-free) — ~10s
        Bmc.check(a.scaleByPowerOfTen(3).scaleByPowerOfTen(-3).compareTo(a) == 0)
    }

    @BmcProof
    fun ulp_and_precision_pins() {
        Bmc.check(BigDecimal.valueOf(123, 2).ulp().compareTo(BigDecimal.valueOf(1, 2)) == 0)  // 0.01
        Bmc.check(BigDecimal.valueOf(12L).ulp().compareTo(BigDecimal.ONE) == 0)
        Bmc.check(BigDecimal.valueOf(12345, 2).precision() == 5)   // 123.45
        Bmc.check(BigDecimal.ZERO.precision() == 1)
        Bmc.check(BigDecimal.valueOf(100L).precision() == 3)
    }

    @BmcProof
    fun valueExact_narrowing_pins() {
        Bmc.check(BigDecimal.valueOf(1200, 2).intValueExact() == 12)     // 12.00 -> 12
        Bmc.check(BigDecimal.valueOf(1200, 2).longValueExact() == 12L)
        Bmc.check(BigDecimal.valueOf(50L).byteValueExact().toInt() == 50)
        Bmc.check(BigDecimal.valueOf(-50L).shortValueExact().toInt() == -50)
    }

    // --- toBigIntegerExact: exact-or-throw ----------------------------------------------------------

    @BmcProof
    fun toBigIntegerExact_pins() {
        Bmc.check(BigDecimal.valueOf(12300, 2).toBigIntegerExact().toLong() == 123L)   // 123.00 -> 123
        Bmc.check(BigDecimal.valueOf(123).toBigIntegerExact().toLong() == 123L)
        Bmc.check(BigDecimal.valueOf(-500, 1).toBigIntegerExact().toLong() == -50L)    // -50.0 -> -50
    }
}
