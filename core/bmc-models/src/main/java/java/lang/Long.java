package java.lang;

import org.bmc4j.BmcCondition;
import org.bmc4j.ConditionalOn;
import org.bmc4j.models.audit.BmcModelConforms;

/**
 * BMC model of {@link java.lang.Long}, centered on {@code long -> String} decimal formatting - the
 * {@code long} twin of the {@link java.lang.Integer} model. See that model for the full rationale; the
 * differences are the wider type, the buffer size (a {@code long} is at most 20 chars incl. sign), and
 * the extra bit-count helpers sibling models reach.
 *
 * <p>{@code Long.toString(long)} (and {@code String.valueOf(long)} / {@code StringBuilder.append(long)},
 * which funnel through it) lowers to the refinement primitive {@code CProverString.toString(long)}: sound
 * + fast under refinement, but UNCONSTRAINED (nondet-length) under {@code CHAR_ARRAY_MODEL}. The
 * {@link #toStringCharArray(long)} override ({@code @ConditionalOn(STRING_REFINEMENT_OFF)}) replaces it
 * under no-refine with a bounded digit build constructed via the sound char-array String path. Handles
 * {@link Long#MIN_VALUE} by never negating, and never returns a bare String literal.
 *
 * <p>Once this class shadows {@code java.lang.Long} it must remain a faithful boxed wrapper and supply the
 * static members sibling models reach: the boxing/unboxing pair, {@link #compare}, the {@code MIN_VALUE}/
 * {@code MAX_VALUE} constants, and the {@code bitCount}/{@code numberOfLeadingZeros}/
 * {@code numberOfTrailingZeros} bit helpers (all small total pure functions, exact).
 */
public final class Long implements java.io.Serializable, Comparable<Long> {

    /** {@code 2^63 - 1}. */
    public static final long MAX_VALUE = 0x7fffffffffffffffL;

    /** {@code -2^63}. */
    public static final long MIN_VALUE = 0x8000000000000000L;

    /** Boxed value. */
    private final long value;

    /** Box a {@code long} (also the autoboxing entry point alongside {@link #valueOf(long)}). */
    public Long(long value) {
        this.value = value;
    }

    /** Box a {@code long} (the autoboxing entry point). */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); boxing identity")
    public static Long valueOf(long l) {
        return new Long(l);
    }

    /** Unbox to the carried {@code long}. */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); unboxing identity")
    public long longValue() {
        return value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); value equality")
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Long) && ((Long) obj).value == value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); hash == high^low fold")
    @Override
    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }

    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); signed long order (delegates to compare)")
    @Override
    public int compareTo(Long other) {
        return compare(this.value, other.value);
    }

    /** Signed comparison: {@code (x < y) ? -1 : ((x == y) ? 0 : 1)}. Exact over the whole range. */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); signed long compare, exact")
    public static int compare(long x, long y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    /** Population count (number of one-bits), the JDK's {@code Long.bitCount}. */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); bit population count, exact")
    public static int bitCount(long i) {
        int count = 0;
        // Bounded 64-iteration scan over the bits (no Hacker's-Delight folding, which JBMC explores fine
        // either way; the explicit loop keeps it transparently exact).
        for (int b = 0; b < 64; b++) {
            if ((i & (1L << b)) != 0L) {
                count++;
            }
        }
        return count;
    }

    /** Number of leading zero bits, the JDK's {@code Long.numberOfLeadingZeros} ({@code 64} for {@code 0}). */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); leading-zero count, exact")
    public static int numberOfLeadingZeros(long i) {
        if (i == 0L) {
            return 64;
        }
        int n = 0;
        // Scan from the most-significant bit down to the first set bit.
        for (int b = 63; b >= 0; b--) {
            if ((i & (1L << b)) != 0L) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Number of trailing zero bits, the JDK's {@code Long.numberOfTrailingZeros} ({@code 64} for {@code 0}). */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); trailing-zero count, exact")
    public static int numberOfTrailingZeros(long i) {
        if (i == 0L) {
            return 64;
        }
        int n = 0;
        for (int b = 0; b < 64; b++) {
            if ((i & (1L << b)) != 0L) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Default body: the fast refinement intrinsic (unchanged under refinement). */
    @BmcModelConforms("@BmcProof (proofs.strings.LongToStringLaws); refinement intrinsic (unchanged); "
        + "no-refine override is bounded (<= 20 chars), differentially exact")
    public static String toString(long i) {
        return org.cprover.CProverString.toString(i);
    }

    /**
     * No-refine override (swapped in for {@link #toString(long)} under {@code STRING_REFINEMENT_OFF}):
     * bounded decimal formatting into a fixed 20-char buffer, then a sound char-array String
     * construction. Differentially exact vs the JDK for every {@code long} (including
     * {@code Long.MIN_VALUE}); length is at most 20. Model-internal helper name (not a real JDK member).
     */
    @ConditionalOn(condition = BmcCondition.STRING_REFINEMENT_OFF, target = "toString")
    static String toStringCharArray(long i) {
        char[] buf = new char[20];          // 19 digits max + a possible '-' sign
        int pos = buf.length;
        boolean negative = i < 0;
        long n = negative ? i : -i;         // negative side holds the full magnitude (incl. MIN_VALUE)
        do {
            int digit = (int) -(n % 10);    // n <= 0, so n % 10 is in [-9, 0]; negate to a 0..9 digit
            buf[--pos] = (char) ('0' + digit);
            n = n / 10;
        } while (n < 0);
        if (negative) {
            buf[--pos] = '-';
        }
        return new String(buf, pos, buf.length - pos);
    }
}
