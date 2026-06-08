package org.bmc4j.models.audit;

/**
 * The IEEE-754 <b>total order</b> on {@code float}/{@code double}, modeled bit-free for JBMC, exposed
 * as a plain {@code org.bmc4j} helper rather than as a {@code java.lang.Float}/{@code java.lang.Double}
 * model class.
 *
 * <h2>Why a helper, not a {@code java.lang.Float}/{@code Double} model</h2>
 * The {@code float[]}/{@code double[]} {@link java.util.Arrays} overloads (sort/equals/binarySearch)
 * only need a sound total-order {@code compare}. Supplying that via {@code java.lang.Float}/{@code
 * Double} model CLASSES put those models on EVERY proof's analysis classpath — and because
 * {@code Float}/{@code Double} are reached pervasively (autoboxing {@code Float.valueOf}/{@code
 * Double.valueOf}, the boxed-comparison path, {@code <clinit>}), JBMC then routed those pervasive
 * calls into the bounded models. That poisoned UNRELATED proofs: the models' FP-reciprocal {@code
 * compare} plus the synthesized loud-tail bodies over {@code Float}/{@code Double}'s full surface
 * dragged pathological FP circuits into the solver, which CRASHED (jbmc exit code 6) on proofs that
 * never touch floating point (e.g. {@code Arrays.sort(short[])}, {@code BigDecimal.setScale}). Keeping
 * the total order in a NON-pervasive helper that {@code java.util.Arrays} calls directly leaves the
 * real {@code java.lang.Float}/{@code Double} to the JDK on every other proof, exactly as before.
 *
 * <h2>The bit-free total order</h2>
 * The 2026-06 FP probe (measured, jbmc default {@code floatbv}) established that {@code
 * floatToIntBits}/{@code doubleToLongBits} are UNSOUND under jbmc (a nondet stub — even {@code
 * floatToIntBits(1.0f) != 0x3F800000}), so the JDK's bit-flip {@code compare} algorithm CANNOT be
 * reproduced. The order is instead derived from the operations the probe proved sound: primitive
 * {@code < > ==}, NaN detection via {@code a != a}, and {@code -0.0}-vs-{@code +0.0} discrimination via
 * the reciprocal sign ({@code 1.0/-0.0 == -Infinity}, {@code 1.0/+0.0 == +Infinity}). This reproduces
 * the JDK total order exactly: {@code -0.0 < +0.0}, NaN is the largest value (sorts above {@code
 * +Infinity}), and {@code compare(NaN, NaN) == 0}.
 *
 * <p>Soundness pinned by {@code @BmcProof} laws in {@code proofs.primitives.FloatDoubleArraysLaws}
 * (the {@code -0/+0}, NaN-largest, NaN==NaN edge cases, exercised through {@code Arrays.sort}/{@code
 * equals}, which route here) and by the differential {@code conformance.ArraysUtilConformanceTest}
 * (this helper's {@code compare} vs the real JDK {@code Float}/{@code Double.compare} on a real JVM).
 */
public final class FpTotalOrder {

    private FpTotalOrder() {
    }

    /**
     * The JDK {@link Float#compare(float, float)} IEEE-754 total order, modeled bit-free.
     * {@code -0.0 < +0.0}, NaN largest, {@code compare(NaN, NaN) == 0}.
     */
    public static int compare(float f1, float f2) {
        if (f1 < f2) {
            return -1;
        }
        if (f1 > f2) {
            return 1;
        }
        // f1 and f2 are equal-or-unordered under the primitive comparisons. Resolve the total order
        // using only sound ops (no floatToIntBits).
        boolean f1NaN = (f1 != f1);
        boolean f2NaN = (f2 != f2);
        if (f1NaN) {
            return f2NaN ? 0 : 1;   // NaN is the largest value; two NaNs compare equal
        }
        if (f2NaN) {
            return -1;
        }
        // Both are non-NaN and not strictly ordered -> numerically equal. The only remaining JDK
        // distinction is -0.0 < +0.0. Discriminate via the reciprocal sign: 1/-0 = -Inf, 1/+0 = +Inf;
        // for any equal nonzero pair the reciprocals are equal, so this yields 0 there.
        float r1 = (f1 == 0.0f) ? 1.0f / f1 : f1;
        float r2 = (f2 == 0.0f) ? 1.0f / f2 : f2;
        if (r1 < r2) {
            return -1;
        }
        if (r1 > r2) {
            return 1;
        }
        return 0;
    }

    /**
     * The JDK {@link Double#compare(double, double)} IEEE-754 total order, modeled bit-free (the
     * {@code double} twin of {@link #compare(float, float)}). {@code -0.0 < +0.0}, NaN largest,
     * {@code compare(NaN, NaN) == 0}.
     */
    public static int compare(double d1, double d2) {
        if (d1 < d2) {
            return -1;
        }
        if (d1 > d2) {
            return 1;
        }
        boolean d1NaN = (d1 != d1);
        boolean d2NaN = (d2 != d2);
        if (d1NaN) {
            return d2NaN ? 0 : 1;   // NaN is the largest value; two NaNs compare equal
        }
        if (d2NaN) {
            return -1;
        }
        double r1 = (d1 == 0.0) ? 1.0 / d1 : d1;
        double r2 = (d2 == 0.0) ? 1.0 / d2 : d2;
        if (r1 < r2) {
            return -1;
        }
        if (r1 > r2) {
            return 1;
        }
        return 0;
    }
}
