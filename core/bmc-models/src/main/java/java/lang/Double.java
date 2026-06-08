package java.lang;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Bounded BMC model of {@link java.lang.Double}, present ONLY to supply a SOUND {@code compare} (the
 * JDK IEEE-754 total order). The {@code double} twin of the {@link Float} model — see that class for
 * the full rationale and the 2026-06 FP probe findings.
 *
 * <h2>The bit-free total order</h2>
 * jbmc's native {@code Double.compare} leaves the EQUAL / {@code -0.0}-vs-{@code +0.0} / NaN cases
 * UNCONSTRAINED, and {@code doubleToLongBits} is itself an unsound nondet stub, so the JDK's bit-flip
 * algorithm cannot be reproduced. The total order is modeled WITHOUT {@code doubleToLongBits}, using
 * only the operations the probe proved sound: primitive {@code < > ==}, NaN detection via
 * {@code a != a}, and {@code -0.0}-vs-{@code +0.0} discrimination via the reciprocal sign
 * ({@code 1.0/-0.0 == -Infinity}, {@code 1.0/+0.0 == +Infinity}). Result: {@code -0.0 < +0.0}, NaN is
 * the largest value, {@code compare(NaN,NaN) == 0}.
 *
 * <p>Note bmc4j discourages {@code double} (double stringification is unsound; symbolic FP is heavy) —
 * this model exists so the {@code double[]} {@code Arrays} overloads can reclaim the total order, not
 * to encourage {@code double} use. {@code doubleToLongBits}/{@code parseDouble}/{@code toString} stay
 * loud in the {@link BmcModelTail}.
 */
@BmcModelTail(reason = "Double exists only to supply a sound bit-free compare/compareTo total order; doubleToLongBits/longBitsToDouble are UNSOUND under jbmc (nondet stub) so they and the parse/format/box surface stay loud in the tail — reaching one is a member-named UNKNOWN, never a false green")
public final class Double {

    private Double() {
    }

    /**
     * The JDK IEEE-754 total order, modeled bit-free (the {@code double} twin of
     * {@link Float#compare(float, float)}). {@code -0.0 < +0.0}, NaN largest,
     * {@code compare(NaN,NaN) == 0}.
     */
    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleCompareLaws): the IEEE total order, bit-free (doubleToLongBits is unsound under jbmc)")
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
