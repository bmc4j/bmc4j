package proofs.primitives;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import java.util.Arrays;

/**
 * Model proofs for the bit-free IEEE total order supplied by the {@link java.lang.Float} / {@link
 * java.lang.Double} {@code compare} models. These are the HIGH-VALUE pins: the EQUAL / {@code -0.0}-vs-
 * {@code +0.0} / NaN cases are exactly where jbmc's NATIVE {@code Float.compare}/{@code Double.compare}
 * is unsound (leaves them UNCONSTRAINED), so verifying them here proves the model fixes that.
 *
 * <p>Background (2026-06 FP probe, measured): {@code floatToIntBits}/{@code doubleToLongBits} are ALSO
 * unsound under jbmc (a nondet stub — even {@code floatToIntBits(1.0f) != 0x3F800000}), so the JDK's
 * bit-flip {@code compare} algorithm cannot be reproduced. The model instead derives the total order
 * from the sound primitives ({@code < > ==}, NaN via {@code a != a}, signed-zero via the reciprocal
 * sign); these proofs are the conformance record that the derived order matches the JDK.
 */
class FloatDoubleCompareLaws {

    // ---- Float.compare: strict finite ordering --------------------------------------------------

    @BmcProof
    void float_compare_finite_strict() {
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        float b = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.assume(a < b);
        Bmc.check(Float.compare(a, b) == -1);
        Bmc.check(Float.compare(b, a) == 1);
    }

    @BmcProof
    void float_compare_reflexive_finite() {
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.check(Float.compare(a, a) == 0);
    }

    // ---- Float.compare: the unsound-native edge cases the model FIXES ---------------------------

    @BmcProof
    void float_compare_neg_zero_below_pos_zero() {
        // The JDK total order: -0.0 < +0.0 (distinct from primitive ==, which says they're equal).
        Bmc.check(Float.compare(-0.0f, 0.0f) == -1);
        Bmc.check(Float.compare(0.0f, -0.0f) == 1);
        Bmc.check(Float.compare(-0.0f, -0.0f) == 0);
        Bmc.check(Float.compare(0.0f, 0.0f) == 0);
        // sanity: primitive == still says the two zeros are equal (sound, unchanged).
        Bmc.check(-0.0f == 0.0f);
    }

    @BmcProof
    void float_compare_nan_is_largest_and_self_equal() {
        float nan = Float.NaN;
        Bmc.check(Float.compare(nan, nan) == 0);                    // NaN == NaN under the total order
        Bmc.check(Float.compare(nan, Float.POSITIVE_INFINITY) == 1); // NaN sorts above +Inf
        Bmc.check(Float.compare(Float.POSITIVE_INFINITY, nan) == -1);
        Bmc.check(Float.compare(nan, -1.0e30f) == 1);
        // sanity: primitive == still says NaN != NaN (sound, unchanged).
        Bmc.check(nan != nan);
    }

    @BmcProof
    void float_compare_sign_consistent_symbolic() {
        // For any two finite floats, a STRICT primitive order forces compare's sign to match. (The only
        // place compare and primitive == diverge is the -0.0/+0.0 tie, which is excluded here because
        // strict < / > is false there — that divergence is pinned separately by the -0/+0 proof above.)
        float a = Bmc.anyFloat(-1.0e20f, 1.0e20f);
        float b = Bmc.anyFloat(-1.0e20f, 1.0e20f);
        int c = Float.compare(a, b);
        if (a < b) {
            Bmc.check(c < 0);
        }
        if (a > b) {
            Bmc.check(c > 0);
        }
        // a strictly-equal nonzero pair (excludes the -0/+0 tie, where a-b underflows differently)
        Bmc.assume(a != 0.0f);
        if (a == b) {
            Bmc.check(c == 0);
        }
    }

    // ---- Double.compare: the same pins ----------------------------------------------------------

    @BmcProof
    void double_compare_finite_strict() {
        double a = Bmc.anyDouble(-1.0e30, 1.0e30);
        double b = Bmc.anyDouble(-1.0e30, 1.0e30);
        Bmc.assume(a < b);
        Bmc.check(Double.compare(a, b) == -1);
        Bmc.check(Double.compare(b, a) == 1);
    }

    @BmcProof
    void double_compare_neg_zero_below_pos_zero() {
        Bmc.check(Double.compare(-0.0, 0.0) == -1);
        Bmc.check(Double.compare(0.0, -0.0) == 1);
        Bmc.check(Double.compare(-0.0, -0.0) == 0);
        Bmc.check(Double.compare(0.0, 0.0) == 0);
        Bmc.check(-0.0 == 0.0);
    }

    @BmcProof
    void double_compare_nan_is_largest_and_self_equal() {
        double nan = Double.NaN;
        Bmc.check(Double.compare(nan, nan) == 0);
        Bmc.check(Double.compare(nan, Double.POSITIVE_INFINITY) == 1);
        Bmc.check(Double.compare(Double.POSITIVE_INFINITY, nan) == -1);
        Bmc.check(Double.compare(nan, -1.0e30) == 1);
        Bmc.check(nan != nan);
    }
}
