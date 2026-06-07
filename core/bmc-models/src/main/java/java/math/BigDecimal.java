package java.math;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * Bounded BMC model of {@link java.math.BigDecimal}: an unscaled {@code long} value plus an
 * {@code int} scale (value = unscaled × 10⁻ˢᶜᵃˡᵉ). All arithmetic is exact integer arithmetic on the
 * unscaled values with scale alignment — so it captures the *decimal* exactness that is the whole
 * point of BigDecimal (e.g. {@code 0.10 + 0.20} compares equal to {@code 0.30}), unlike a
 * double-backed model. Sound while the unscaled value and intermediate rescalings stay within the
 * {@code long} range (~18 significant digits) — ample for money/business arithmetic.
 *
 * <p>Deliberately <b>not</b> provided: the {@code double} constructor / {@code valueOf(double)} —
 * those reintroduce binary floating-point error (and are discouraged in real code too). Use the
 * {@code String}/{@code long} constructors for exact values. {@code divide} requires an explicit
 * {@link RoundingMode}. String parsing handles an optional sign, digits and one decimal point (no
 * exponent notation); a numeral whose unscaled digits exceed the {@code long} range fails LOUDLY in
 * the digit-accumulation guard (never a silent wrap), like the rest of the arithmetic.
 */
@BmcModelTail(reason = "MathContext-rounded arithmetic overloads (add/subtract/multiply/divide/pow/round with MathContext), precision/scaleByPowerOfTen, the int/long/byte/short *Exact narrowing, toEngineeringString/toPlainString, and the broad formatting/precision surface are out of scope for the bounded long-backed model; all loud under JBMC")
public class BigDecimal extends Number implements Comparable<BigDecimal> {

    public static final BigDecimal ZERO = new BigDecimal(0L, 0);
    public static final BigDecimal ONE = new BigDecimal(1L, 0);
    public static final BigDecimal TEN = new BigDecimal(10L, 0);

    private final long unscaled;
    private final int scale;

    private BigDecimal(long unscaled, int scale) {
        this.unscaled = unscaled;
        this.scale = scale;
    }

    @BmcNotModelled(reason = "double entry reintroduces binary FP error (discouraged in real code too) — use the String/long constructors for exact values")
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public static BigDecimal valueOf(double val) {
        throw fail("bmc4j: unmodelled member java.math.BigDecimal.valueOf(double) — double entry reintroduces binary FP error (discouraged in real code too) — use the String/long constructors for exact values");
    }

    public BigDecimal(long val) {
        this(val, 0);
    }

    public BigDecimal(int val) {
        this((long) val, 0);
    }

    public BigDecimal(String val) {
        long u = 0;
        int sc = 0;
        boolean neg = false;
        boolean afterDot = false;
        boolean sawDigit = false;
        int n = val.length();
        int i = 0;
        if (n > 0 && val.charAt(0) == '-') {
            neg = true;
            i = 1;
        } else if (n > 0 && val.charAt(0) == '+') {
            i = 1;
        }
        for (; i < n; i++) {
            char c = val.charAt(i);
            if (c == '.') {
                if (afterDot) {
                    throw new NumberFormatException(val);   // more than one decimal point
                }
                afterDot = true;
                continue;
            }
            if (c < '0' || c > '9') {
                throw new NumberFormatException(val);        // non-digit (incl. whitespace, letters)
            }
            sawDigit = true;
            u = addExact(mul(u, 10), c - '0');   // loud on past-~18-digit overflow, never silent wrap
            if (afterDot) {
                sc++;
            }
        }
        if (!sawDigit) {
            throw new NumberFormatException(val);            // "", "-", "+", ".", "+." — no digits
        }
        this.unscaled = neg ? -u : u;
        this.scale = sc;
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public static BigDecimal valueOf(long val) {
        return new BigDecimal(val, 0);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public static BigDecimal valueOf(long unscaledVal, int scale) {
        return new BigDecimal(unscaledVal, scale);
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Multiply with a loud overflow check: past the {@code long} range the model must fail, never
     * wrap to a silently-wrong value. JBMC checks the {@code assert} as a property; on a
     * real JVM (assertions off by default) it's a plain multiply, so behavior in-bounds is unchanged.
     */
    private static long mul(long a, long b) {
        long r = a * b;
        assert b == 0 || r / b == a : "BigDecimal model overflow: value exceeds the ~18-digit bound";
        return r;
    }

    /**
     * Add with a loud overflow check, mirroring {@link #mul}. Used by the {@code String} constructor's
     * digit accumulation so an over-long numeral fails loudly rather than silently wrapping the
     * unscaled {@code long} — same "loud, never silent" contract as the arithmetic ops.
     */
    private static long addExact(long a, long b) {
        long r = a + b;
        assert ((a ^ r) & (b ^ r)) >= 0 : "BigDecimal model overflow: value exceeds the ~18-digit bound";
        return r;
    }

    private static long pow10(int n) {
        long r = 1;
        for (int i = 0; i < n; i++) {
            r = mul(r, 10);
        }
        return r;
    }

    /** Rescale an unscaled value from one scale up to a larger one (multiplying by 10^Δ). */
    private static long rescale(long u, int from, int to) {
        return mul(u, pow10(to - from));
    }

    /** Sound rounding division: round(num/den) under {@code mode}. {@code den != 0}. */
    private static long roundDiv(long num, long den, RoundingMode mode) {
        boolean neg = (num < 0) ^ (den < 0);
        long n = num < 0 ? -num : num;
        long d = den < 0 ? -den : den;
        long q = n / d;
        long r = n % d;
        if (r != 0) {
            boolean inc;
            switch (mode) {
                case UP:        inc = true; break;
                case DOWN:      inc = false; break;
                case CEILING:   inc = !neg; break;
                case FLOOR:     inc = neg; break;
                case HALF_UP:   inc = 2 * r >= d; break;
                case HALF_DOWN: inc = 2 * r > d; break;
                case HALF_EVEN: inc = 2 * r > d || (2 * r == d && (q & 1L) == 1L); break;
                default:        throw new ArithmeticException("Rounding necessary");
            }
            if (inc) {
                q++;
            }
        }
        return neg ? -q : q;
    }

    private long truncatedToLong() {
        return scale <= 0 ? unscaled * pow10(-scale) : unscaled / pow10(scale);
    }

    // --- arithmetic ----------------------------------------------------------

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal add(BigDecimal o) {
        int s = Math.max(scale, o.scale);
        return new BigDecimal(rescale(unscaled, scale, s) + rescale(o.unscaled, o.scale, s), s);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal subtract(BigDecimal o) {
        int s = Math.max(scale, o.scale);
        return new BigDecimal(rescale(unscaled, scale, s) - rescale(o.unscaled, o.scale, s), s);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal multiply(BigDecimal o) {
        return new BigDecimal(mul(unscaled, o.unscaled), scale + o.scale);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal divide(BigDecimal divisor, int newScale, RoundingMode mode) {
        int e = divisor.scale + newScale - scale;
        long num = e >= 0 ? mul(unscaled, pow10(e)) : unscaled;
        long den = e >= 0 ? divisor.unscaled : mul(divisor.unscaled, pow10(-e));
        return new BigDecimal(roundDiv(num, den, mode), newScale);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal divide(BigDecimal divisor, RoundingMode mode) {
        return divide(divisor, scale, mode);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal setScale(int newScale, RoundingMode mode) {
        if (newScale >= scale) {
            return new BigDecimal(rescale(unscaled, scale, newScale), newScale);
        }
        return new BigDecimal(roundDiv(unscaled, pow10(scale - newScale), mode), newScale);
    }

    /**
     * Rescale with {@link RoundingMode#UNNECESSARY}: scaling UP (newScale &gt;= scale) is always exact;
     * scaling DOWN throws {@link ArithmeticException} ("Rounding necessary") unless the dropped digits
     * are all zero — exactly the JDK contract for the no-rounding overload.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal setScale(int newScale) {
        if (newScale >= scale) {
            return new BigDecimal(rescale(unscaled, scale, newScale), newScale);
        }
        long div = pow10(scale - newScale);
        if (unscaled % div != 0L) {
            throw new ArithmeticException("Rounding necessary");
        }
        return new BigDecimal(unscaled / div, newScale);
    }

    /**
     * Move the decimal point right by {@code n} (multiply by 10ⁿ), like the JDK: the scale drops by
     * {@code n}, and once it would go negative the unscaled value absorbs the surplus power of ten
     * (the result then has scale 0). Exact integer arithmetic; loud past the {@code long} bound.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal movePointRight(int n) {
        int newScale = scale - n;
        if (newScale >= 0) {
            return new BigDecimal(unscaled, newScale);
        }
        return new BigDecimal(mul(unscaled, pow10(-newScale)), 0);
    }

    /**
     * Move the decimal point left by {@code n} (divide by 10ⁿ), like the JDK: the scale rises by
     * {@code n}; if {@code n} is negative far enough to drive the scale below zero, the unscaled value
     * absorbs the surplus power of ten (the result then has scale 0). Exact; loud past the bound.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal movePointLeft(int n) {
        int newScale = scale + n;
        if (newScale >= 0) {
            return new BigDecimal(unscaled, newScale);
        }
        return new BigDecimal(mul(unscaled, pow10(-newScale)), 0);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal negate() {
        return new BigDecimal(-unscaled, scale);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal abs() {
        return unscaled < 0 ? negate() : this;
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int signum() {
        return unscaled < 0 ? -1 : unscaled > 0 ? 1 : 0;
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal min(BigDecimal o) {
        return compareTo(o) <= 0 ? this : o;
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal max(BigDecimal o) {
        return compareTo(o) >= 0 ? this : o;
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal stripTrailingZeros() {
        if (unscaled == 0) {
            return new BigDecimal(0L, 0);
        }
        long u = unscaled;
        int s = scale;
        while (s > 0 && u % 10 == 0) {
            u /= 10;
            s--;
        }
        return new BigDecimal(u, s);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int scale() {
        return scale;
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigInteger unscaledValue() {
        return BigInteger.valueOf(unscaled);
    }

    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigInteger toBigInteger() {
        return BigInteger.valueOf(truncatedToLong());
    }

    /**
     * The exact integer value, throwing {@link ArithmeticException} if this has a nonzero fractional
     * part — exactly the JDK contract. A negative scale (a value that is an integer with trailing
     * implied zeros) is always exact; a positive scale is exact only when the fractional digits vanish.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigInteger toBigIntegerExact() {
        if (scale > 0 && unscaled % pow10(scale) != 0L) {
            throw new ArithmeticException("Rounding necessary");
        }
        return BigInteger.valueOf(truncatedToLong());
    }

    // --- comparison ----------------------------------------------------------

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int compareTo(BigDecimal o) {
        int s = Math.max(scale, o.scale);
        return Long.compare(rescale(unscaled, scale, s), rescale(o.unscaled, o.scale, s));
    }

    /** Scale-sensitive, like the real BigDecimal: 2.0 and 2.00 are NOT equal (use compareTo). */
    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public boolean equals(Object x) {
        if (!(x instanceof BigDecimal)) {
            return false;
        }
        BigDecimal o = (BigDecimal) x;
        return o.unscaled == unscaled && o.scale == scale;
    }

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int hashCode() {
        return 31 * (int) (unscaled ^ (unscaled >>> 32)) + scale;
    }

    // --- Number --------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int intValue() {
        return (int) truncatedToLong();
    }

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public long longValue() {
        return truncatedToLong();
    }

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public float floatValue() {
        return (float) doubleValue();
    }

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public double doubleValue() {
        return scale <= 0 ? (double) (unscaled * pow10(-scale)) : (double) unscaled / (double) pow10(scale);
    }
}
