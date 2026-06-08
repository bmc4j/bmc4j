package proofs.primitives

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.Arrays

/**
 * Model proofs for the {@code float[]}/{@code double[]} {@link java.util.Arrays} overloads reclaimed
 * from the loud tail: {@code equals}/{@code sort}/{@code binarySearch}/{@code compare}/{@code mismatch}
 * (and ranged + parallelSort). These all route through the IEEE TOTAL ORDER (the {@link java.lang.Float}/
 * {@link java.lang.Double} {@code compare} models), so the high-value pins are the edge cases jbmc's
 * native {@code Float.compare} gets wrong: {@code -0.0 < +0.0}, NaN sorts last, NaN==NaN under
 * {@code equals}/{@code compare}. Arrays are kept tiny (length 2-3) so the insertion-sort unwind and
 * the symbolic FP stay tractable.
 */
class FloatDoubleArraysLaws {

    // --- sort: total order (NaN last, -0 before +0) ----------------------------------------------

    @BmcProof
    fun sort_float_orders_two_finite() {
        val x = Bmc.anyFloat(-1.0e20f, 1.0e20f)
        val y = Bmc.anyFloat(-1.0e20f, 1.0e20f)
        val a = floatArrayOf(x, y)
        Arrays.sort(a)
        Bmc.check(a[0].compareTo(a[1]) <= 0)
    }

    @BmcProof
    fun sort_float_puts_nan_last() {
        // NaN is the largest under the total order, so it sorts to the end regardless of start order.
        val a = floatArrayOf(Float.NaN, 1.0f, -1.0f)
        Arrays.sort(a)
        Bmc.check(a[0] == -1.0f && a[1] == 1.0f)
        Bmc.check(a[2] != a[2]) // a[2] is NaN (NaN != NaN)
    }

    @BmcProof
    fun sort_float_neg_zero_before_pos_zero() {
        // -0.0 sorts before +0.0 under the total order even though they're primitive-==.
        val a = floatArrayOf(0.0f, -0.0f)
        Arrays.sort(a)
        // distinguish by reciprocal sign: a[0] must be -0.0 (1/-0 = -Inf), a[1] = +0.0 (1/+0 = +Inf).
        Bmc.check(1.0f / a[0] < 0.0f)
        Bmc.check(1.0f / a[1] > 0.0f)
    }

    @BmcProof
    fun sort_double_orders_two_finite() {
        val x = Bmc.anyDouble(-1.0e20, 1.0e20)
        val y = Bmc.anyDouble(-1.0e20, 1.0e20)
        val a = doubleArrayOf(x, y)
        Arrays.sort(a)
        Bmc.check(a[0].compareTo(a[1]) <= 0)
    }

    @BmcProof
    fun sort_double_puts_nan_last() {
        val a = doubleArrayOf(Double.NaN, 1.0, -1.0)
        Arrays.sort(a)
        Bmc.check(a[0] == -1.0 && a[1] == 1.0)
        Bmc.check(a[2] != a[2])
    }

    // --- equals: -0 != +0, NaN == NaN (total order, per the JDK spec) ----------------------------

    @BmcProof
    fun equals_float_reflexive() {
        val a = floatArrayOf(Bmc.anyFloat(-1.0e20f, 1.0e20f), Bmc.anyFloat(-1.0e20f, 1.0e20f))
        Bmc.check(Arrays.equals(a, a))
    }

    @BmcProof
    fun equals_float_nan_equals_nan() {
        // Arrays.equals(float[]) uses the total order: NaN == NaN (unlike primitive ==).
        Bmc.check(Arrays.equals(floatArrayOf(Float.NaN), floatArrayOf(Float.NaN)))
    }

    @BmcProof
    fun equals_float_neg_zero_not_equal_pos_zero() {
        // Arrays.equals(float[]) uses the total order: -0.0 != +0.0 (unlike primitive ==).
        Bmc.check(!Arrays.equals(floatArrayOf(-0.0f), floatArrayOf(0.0f)))
    }

    @BmcProof
    fun equals_double_nan_equals_nan() {
        Bmc.check(Arrays.equals(doubleArrayOf(Double.NaN), doubleArrayOf(Double.NaN)))
    }

    @BmcProof
    fun equals_double_neg_zero_not_equal_pos_zero() {
        Bmc.check(!Arrays.equals(doubleArrayOf(-0.0), doubleArrayOf(0.0)))
    }

    // --- binarySearch: total order, sorted-assume ------------------------------------------------

    @BmcProof
    fun binarySearch_float_finds_present_key() {
        val x = Bmc.anyFloat(-1.0e6f, 1.0e6f)
        val a = floatArrayOf(x, x + 1.0f, x + 2.0f)
        Bmc.assume(x < x + 1.0f && x + 1.0f < x + 2.0f) // strictly increasing
        Bmc.check(Arrays.binarySearch(a, x + 1.0f) == 1)
    }

    @BmcProof
    fun binarySearch_double_finds_present_key() {
        val x = Bmc.anyDouble(-1.0e6, 1.0e6)
        val a = doubleArrayOf(x, x + 1.0, x + 2.0)
        Bmc.assume(x < x + 1.0 && x + 1.0 < x + 2.0)
        Bmc.check(Arrays.binarySearch(a, x + 1.0) == 1)
    }

    // NOTE: Arrays.compare(float[])/mismatch(float[]) laws are covered on the DIFFERENTIAL axis
    // (conformance.ArraysUtilConformanceTest) rather than here. Their JBMC shape — a loop returning the
    // Float.compare-derived signed result with an `a.length - b.length` fallthrough — reproducibly
    // ENGINE-CRASHES jbmc's solver (exit code 6), and the SAME crash hits the pre-existing INTEGRAL
    // Arrays.compare/mismatch proofs, so it is a jbmc fragility with that array-compare shape, not a
    // model issue. Per the perf memo, when a shape defeats the solver the differential (real-JVM) axis
    // is the designated home — and it is the definitive arbiter for these -0/+0/NaN edge cases anyway.

    // --- ranged + parallelSort -------------------------------------------------------------------

    @BmcProof
    fun sort_float_range_orders_only_the_window() {
        val p = Bmc.anyFloat(-1.0e6f, 1.0e6f)
        val x = Bmc.anyFloat(-1.0e6f, 1.0e6f)
        val y = Bmc.anyFloat(-1.0e6f, 1.0e6f)
        val q = Bmc.anyFloat(-1.0e6f, 1.0e6f)
        val a = floatArrayOf(p, x, y, q)
        Arrays.sort(a, 1, 3)
        Bmc.check(a[0] == p && a[3] == q)
        Bmc.check(a[1].compareTo(a[2]) <= 0)
    }

    @BmcProof
    fun equals_float_range_compares_only_sub_regions() {
        val x = Bmc.anyFloat(-1.0e20f, 1.0e20f)
        val y = Bmc.anyFloat(-1.0e20f, 1.0e20f)
        val a = floatArrayOf(99.0f, x, y)
        val b = floatArrayOf(x, y, 99.0f)
        Bmc.check(Arrays.equals(a, 1, 3, b, 0, 2))
    }

    @BmcProof
    fun parallelSort_float_orders_like_sort() {
        val x = Bmc.anyFloat(-1.0e20f, 1.0e20f)
        val y = Bmc.anyFloat(-1.0e20f, 1.0e20f)
        val a = floatArrayOf(x, y)
        Arrays.parallelSort(a)
        Bmc.check(a[0].compareTo(a[1]) <= 0)
    }
}
