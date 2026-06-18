package java.lang;

import org.bmc4j.BmcCondition;
import org.bmc4j.ConditionalOn;
import org.bmc4j.models.audit.BmcModelConforms;

/**
 * BMC model of {@link java.lang.Integer}, centered on {@code int -> String} decimal formatting with a
 * MODE-CONDITIONAL no-refine override carried beside the default via {@link ConditionalOn}.
 *
 * <h2>Why this model exists</h2>
 * {@code Integer.toString(int)} (and {@code String.valueOf(int)} / {@code StringBuilder.append(int)},
 * which funnel through it) lowers in JBMC's frontend to the refinement primitive
 * {@code org.cprover.CProverString.toString(int)}. Under string REFINEMENT that primitive is a sound,
 * fast intrinsic. Under {@code StringMode.CHAR_ARRAY_MODEL} ({@code --no-refine-strings}) it instead
 * returns an UNCONSTRAINED String, which the char-array String model backs with a NONDET-LENGTH array -
 * so {@code int -> String} becomes unbounded (an {@code int} is really at most 11 chars incl. sign).
 * That poisons proofs: an exception message {@code "...: " + index} blows a {@code String.<init>} unwind
 * up to thousands of iterations.
 *
 * <h2>The two toString bodies</h2>
 * <ul>
 *   <li><b>Default {@link #toString(int)}</b>: delegates to {@code CProverString.toString(int)} - the
 *       fast refinement intrinsic, UNCHANGED under refinement.</li>
 *   <li><b>{@link #toStringCharArray(int)}</b> (the {@code @ConditionalOn(STRING_REFINEMENT_OFF)}
 *       override): under no-refine, does a BOUNDED digit build into a fixed 11-char buffer (10 digits +
 *       a possible sign) and constructs the String via the sound char-array path
 *       ({@code new String(char[], offset, count)}, which the bundled char-array String model backs with
 *       a real array). The prep-time {@code ConditionalOnBytecode} pass redirects every
 *       {@code Integer.toString(int)} call site to it when refinement is off.</li>
 * </ul>
 *
 * <h2>{@link Integer#MIN_VALUE} and the no-bare-literal rule</h2>
 * {@code -MIN_VALUE} overflows back to itself (the two's-complement trap), so the override never negates:
 * it peels digits off the value while keeping it NEGATIVE. And it never returns a bare String LITERAL
 * ({@code "0"}, {@code "-"}): under no-refine a model-class literal's backing is itself nondet-length
 * (the literal-pinning pass excludes {@code java/*}), so EVERY case - including {@code 0} and
 * {@code MIN_VALUE} - is built through the char[] path.
 *
 * <h2>The rest of the surface</h2>
 * Once this class shadows {@code java.lang.Integer} it must remain a faithful boxed wrapper and supply the
 * static members sibling models reach: the boxing/unboxing pair, {@link #compare}, the {@code MIN_VALUE}/
 * {@code MAX_VALUE} constants, and {@link #rotateLeft}. All are small total pure functions (exact).
 */
public final class Integer implements java.io.Serializable, Comparable<Integer> {

    /** {@code 2^31 - 1}. */
    public static final int MAX_VALUE = 0x7fffffff;

    /** {@code -2^31}. */
    public static final int MIN_VALUE = 0x80000000;

    /** Boxed value. */
    private final int value;

    /** Box an {@code int} (also the autoboxing entry point alongside {@link #valueOf(int)}). */
    public Integer(int value) {
        this.value = value;
    }

    /** Box an {@code int} (the autoboxing entry point). */
    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); boxing identity")
    public static Integer valueOf(int i) {
        return new Integer(i);
    }

    /** Unbox to the carried {@code int}. */
    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); unboxing identity")
    public int intValue() {
        return value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); value equality")
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Integer) && ((Integer) obj).value == value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); hash == int value")
    @Override
    public int hashCode() {
        return value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); signed int order (delegates to compare)")
    @Override
    public int compareTo(Integer other) {
        return compare(this.value, other.value);
    }

    /** Signed comparison: {@code (x < y) ? -1 : ((x == y) ? 0 : 1)}. Exact over the whole range. */
    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); signed int compare, exact")
    public static int compare(int x, int y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    /** Rotate the bits of {@code i} left by {@code distance} (the JDK's {@code Integer.rotateLeft}). */
    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); bit rotate, exact")
    public static int rotateLeft(int i, int distance) {
        return (i << distance) | (i >>> -distance);
    }

    /**
     * Default body: the fast refinement intrinsic. Under string REFINEMENT JBMC lowers this (and the
     * {@code CProverString.toString} it calls) to its sound {@code of_int} conversion; this body is
     * irrelevant there. Under {@code CHAR_ARRAY_MODEL} the {@link #toStringCharArray(int)} override
     * REPLACES every call site of this method, so the unconstrained-intrinsic result never reaches a
     * proof.
     */
    @BmcModelConforms("@BmcProof (proofs.strings.IntToStringLaws); refinement intrinsic (unchanged); "
        + "no-refine override is bounded (<= 11 chars), differentially exact")
    public static String toString(int i) {
        return org.cprover.CProverString.toString(i);
    }

    /**
     * No-refine override (swapped in for {@link #toString(int)} under {@code STRING_REFINEMENT_OFF}):
     * bounded decimal formatting into a fixed 11-char buffer, then a sound char-array String
     * construction. Differentially exact vs the JDK for every {@code int} (including
     * {@code Integer.MIN_VALUE}); length is at most 11.
     *
     * <p>Not a real {@code Integer} member name, so it is a model-internal helper (not audited as a JDK
     * member). TODO: instance-method body-swap is a future {@code @ConditionalOn} expansion; this MVP
     * is static-only.
     */
    @ConditionalOn(condition = BmcCondition.STRING_REFINEMENT_OFF, target = "toString")
    static String toStringCharArray(int i) {
        char[] buf = new char[11];          // 10 digits max + a possible '-' sign
        int pos = buf.length;
        // Work on the NEGATIVE side so Integer.MIN_VALUE is representable (its positive twin is not).
        boolean negative = i < 0;
        int n = negative ? i : -i;
        // Always runs at least once (peels the last digit), so 0 is built as "0" through the char[] path
        // - never a bare literal.
        do {
            int digit = -(n % 10);          // n <= 0, so n % 10 is in [-9, 0]; negate to a 0..9 digit
            buf[--pos] = (char) ('0' + digit);
            n = n / 10;
        } while (n < 0);
        if (negative) {
            buf[--pos] = '-';
        }
        // The bundled char-array String model backs this with a real array (sound, bounded length).
        return new String(buf, pos, buf.length - pos);
    }
}
