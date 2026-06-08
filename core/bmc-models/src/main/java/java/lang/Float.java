package java.lang;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Bounded BMC model of {@link java.lang.Float}, present ONLY to supply a SOUND {@code compare} (the
 * JDK IEEE-754 total order) that jbmc's bundled {@code Float.compare} gets wrong.
 *
 * <h2>Why this model exists</h2>
 * The 2026-06 FP probe (measured, jbmc default {@code floatbv}, no flag) established:
 * <ul>
 *   <li>{@code float} arithmetic and the primitive {@code == < >} comparisons are SOUND and
 *       bit-precise.</li>
 *   <li>jbmc's native {@code Float.compare} is UNSOUND: the sign is correct for strictly-ordered
 *       finite values, but the EQUAL / {@code -0.0}-vs-{@code +0.0} / NaN cases are left
 *       UNCONSTRAINED. So a model leaning on the native {@code compare} for the total order proves
 *       false things.</li>
 *   <li><b>{@code floatToIntBits}/{@code intBitsToFloat} are ALSO unsound under jbmc</b> — a nondet
 *       stub (even {@code floatToIntBits(1.0f)} does not equal {@code 0x3F800000}; a symbolic finite
 *       float reads back {@code 0x7F800000}, +Inf's pattern). So the JDK's own bit-flip algorithm
 *       CANNOT be reproduced here.</li>
 * </ul>
 *
 * <h2>The bit-free total order</h2>
 * The JDK total order is modeled WITHOUT {@code floatToIntBits}, using only the operations the probe
 * proved sound: primitive {@code < > ==}, NaN detection via {@code a != a}, and {@code -0.0}-vs-{@code
 * +0.0} discrimination via the reciprocal sign ({@code 1.0f/-0.0f == -Infinity},
 * {@code 1.0f/+0.0f == +Infinity}). This reproduces the JDK total order exactly: {@code -0.0 < +0.0},
 * NaN is the largest value (sorts above {@code +Infinity}), and {@code NaN compareTo NaN == 0}.
 *
 * <p>{@code floatToIntBits}/{@code intBitsToFloat}/{@code parseFloat}/{@code toString} and the rest of
 * {@code Float}'s surface are NOT modeled — they fall to the {@link BmcModelTail} (loud), so a proof
 * reaching one fails NAMED AND LOUD rather than silently using the unsound jbmc intrinsic.
 */
@BmcModelTail(reason = "Float exists only to supply a sound bit-free compare/compareTo total order; floatToIntBits/intBitsToFloat are UNSOUND under jbmc (nondet stub) so they and the parse/format/box surface stay loud in the tail — reaching one is a member-named UNKNOWN, never a false green")
public final class Float {

    private Float() {
    }

    /**
     * The JDK IEEE-754 total order, modeled bit-free. Equivalent to the JDK's
     * {@code (f1<f2)?-1 : (f1>f2)?1 : bitsCompare}, with the bits step replaced by the sound
     * reciprocal-sign / NaN-detection resolution (see the class doc). Result: {@code -0.0 < +0.0},
     * NaN largest, {@code compare(NaN,NaN)==0}.
     */
    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleCompareLaws): the IEEE total order, bit-free (floatToIntBits is unsound under jbmc)")
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
}
