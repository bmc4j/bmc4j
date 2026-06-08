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
@BmcModelTail(reason = "MathContext-rounded arithmetic overloads (add/subtract/multiply/divide/pow/round/plus with MathContext, sqrt(MathContext)), the deprecated int-rounding-mode overloads (divide(BigDecimal,int[,int]), setScale(int,int)), and toEngineeringString/toPlainString are out of scope for the bounded long-backed model; all loud under JBMC")
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

    /**
     * Exact division {@code this / divisor}, like the JDK's no-rounding {@code divide(BigDecimal)}: the
     * preferred scale is {@code this.scale - divisor.scale}, and the result extends the scale only as far
     * as needed to represent the quotient exactly. A non-terminating decimal expansion (e.g. {@code 1/3})
     * throws {@link ArithmeticException}, exactly the JDK contract; a zero divisor throws too. Loud,
     * never silent at the bound: the scale-extension multiplies route through the checked {@link #mul}.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal divide(BigDecimal divisor) {
        if (divisor.unscaled == 0L) {
            throw new ArithmeticException("Division by zero");
        }
        int preferredScale = scale - divisor.scale;
        // Work on the reduced fraction n/d (magnitudes; carry the sign separately).
        boolean neg = (unscaled < 0) ^ (divisor.unscaled < 0);
        long n = unscaled < 0 ? -unscaled : unscaled;
        long d = divisor.unscaled < 0 ? -divisor.unscaled : divisor.unscaled;
        long g = gcdLong(n, d);
        n /= g;
        d /= g;
        // this/divisor has a finite decimal expansion iff the reduced denominator is 2^a·5^b. Strip the
        // 2s and 5s; anything left means a non-terminating quotient (e.g. 1/3) — the JDK throws there.
        long dd = d;
        while (dd % 2L == 0L) {
            dd /= 2L;
        }
        while (dd % 5L == 0L) {
            dd /= 5L;
        }
        if (dd != 1L) {
            throw new ArithmeticException(
                "Non-terminating decimal expansion; no exact representable decimal result.");
        }
        // Bump the scale (multiply the numerator by 10 each step) until d divides it exactly; that is the
        // minimal scale at which the quotient is exact. Pad up to the preferred scale if it's larger.
        int newScale = preferredScale;
        long acc = n;
        while (acc % d != 0L) {
            acc = mul(acc, 10L);
            newScale++;
        }
        long q = acc / d;
        if (newScale < preferredScale) {
            q = rescale(q, newScale, preferredScale);
            newScale = preferredScale;
        }
        return new BigDecimal(neg ? -q : q, newScale);
    }

    /**
     * The integer part of {@code this / divisor} (truncated toward zero), like the JDK's
     * {@code divideToIntegralValue}. The integer quotient is then expressed toward the preferred scale
     * {@code this.scale - divisor.scale}: a POSITIVE preferred scale pads it with trailing zeros; a
     * NEGATIVE preferred scale strips trailing tens (down to the preferred scale, but only while the
     * quotient stays divisible by ten — e.g. {@code 5 / 0.1} is {@code 5E+1}, {@code 100 / 0.7} stays
     * {@code 142}). A zero divisor throws {@link ArithmeticException}. Loud at the bound (the padding
     * routes through the checked {@link #mul}).
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal divideToIntegralValue(BigDecimal divisor) {
        long q = integralQuotient(divisor);   // integer quotient, conceptual scale 0
        int preferredScale = scale - divisor.scale;
        int s = 0;
        if (preferredScale > 0) {
            return new BigDecimal(mul(q, pow10(preferredScale)), preferredScale);
        }
        // preferredScale <= 0: drop trailing tens toward it, stopping when no longer exactly divisible.
        while (s > preferredScale && q % 10L == 0L) {
            q /= 10L;
            s--;
        }
        return new BigDecimal(q, s);
    }

    /**
     * The remainder {@code this - this.divideToIntegralValue(divisor) * divisor}, exactly as the JDK
     * defines it (same sign as the dividend; the scale falls out of that subtraction). A zero divisor
     * throws {@link ArithmeticException}.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal remainder(BigDecimal divisor) {
        return subtract(divideToIntegralValue(divisor).multiply(divisor));
    }

    /**
     * {@code {divideToIntegralValue(divisor), remainder(divisor)}} in one shot, like the JDK — the same
     * integer quotient as {@link #divideToIntegralValue} and remainder as {@link #remainder}. A zero
     * divisor throws {@link ArithmeticException}.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal[] divideAndRemainder(BigDecimal divisor) {
        BigDecimal q = divideToIntegralValue(divisor);
        return new BigDecimal[] {q, subtract(q.multiply(divisor))};
    }

    /** The truncated-toward-zero integer quotient of this/divisor (shared by divideToIntegralValue and
     *  remainder); throws ArithmeticException on a zero divisor. */
    private long integralQuotient(BigDecimal divisor) {
        if (divisor.unscaled == 0L) {
            throw new ArithmeticException("Division by zero");
        }
        int s = Math.max(scale, divisor.scale);
        long n = rescale(unscaled, scale, s);
        long d = rescale(divisor.unscaled, divisor.scale, s);
        return n / d;   // long division truncates toward zero
    }

    private static long gcdLong(long a, long b) {
        while (b != 0L) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a < 0 ? -a : a;
    }

    /**
     * {@code this} raised to {@code n} (0 &lt;= n &lt;= 999999999), like the JDK's {@code pow(int)}:
     * the result is exact with scale {@code this.scale * n}, and {@code pow(0)} is {@code ONE} (scale 0)
     * for any value. An exponent outside {@code [0, 999999999]} throws {@link ArithmeticException}.
     * Loud, never silent at the bound: the repeated multiply routes through the checked {@link #mul}.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal pow(int n) {
        if (n < 0 || n > 999999999) {
            throw new ArithmeticException("Invalid operation");
        }
        long u = 1L;
        int s = 0;
        for (int i = 0; i < n; i++) {
            u = mul(u, unscaled);
            s += scale;
        }
        return new BigDecimal(u, s);
    }

    /**
     * Move the decimal point so the value is multiplied by {@code 10^n}, by SUBTRACTING {@code n} from
     * the scale (the JDK's {@code scaleByPowerOfTen} — note the scale may go NEGATIVE, unlike
     * {@link #movePointRight}, which clamps it to 0). The unscaled value is unchanged; only the scale
     * shifts. Exact and cheap; no rounding.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal scaleByPowerOfTen(int n) {
        return new BigDecimal(unscaled, scale - n);
    }

    /**
     * The unit in the last place: {@code 1 × 10^-scale}, i.e. unscaled {@code 1} at this value's scale,
     * exactly like the JDK ({@code ulp} depends only on the scale, not the value). Scale 2 → {@code 0.01},
     * scale 0 → {@code 1}, a negative scale → a power of ten ≥ 10.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public BigDecimal ulp() {
        return new BigDecimal(1L, scale);
    }

    /**
     * The precision: the number of digits in the unscaled value, like the JDK ({@code ZERO} has
     * precision 1). Counts the decimal digits of {@code |unscaled|}.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int precision() {
        long u = unscaled < 0 ? -unscaled : unscaled;
        int p = 1;
        while (u >= 10L) {
            u /= 10L;
            p++;
        }
        return p;
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
    public byte byteValue() {
        return (byte) truncatedToLong();
    }

    @Override
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public short shortValue() {
        return (short) truncatedToLong();
    }

    /**
     * The exact integer value as a {@code long}, throwing {@link ArithmeticException} ("Rounding
     * necessary") when this has a nonzero fractional part — the JDK contract. (A magnitude past the
     * {@code long} range, which the arbitrary-precision JDK would also reject, can't arise on the
     * {@code long} backing.) Shared by the byte/short/int variants below.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public long longValueExact() {
        if (scale > 0 && unscaled % pow10(scale) != 0L) {
            throw new ArithmeticException("Rounding necessary");
        }
        return truncatedToLong();
    }

    /**
     * The exact {@code int} value: exact integer (else "Rounding necessary") that fits an {@code int}
     * (else "Overflow") — the JDK contract.
     */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public int intValueExact() {
        long v = longValueExact();
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new ArithmeticException("Overflow");
        }
        return (int) v;
    }

    /** The exact {@code short} value: exact integer that fits a {@code short}, else loud per the JDK. */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public short shortValueExact() {
        long v = longValueExact();
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
            throw new ArithmeticException("Overflow");
        }
        return (short) v;
    }

    /** The exact {@code byte} value: exact integer that fits a {@code byte}, else loud per the JDK. */
    @BmcModelConforms("differential (BigDecimalConformanceTest) + @BmcProof (proofs.bigdecimal)")
    public byte byteValueExact() {
        long v = longValueExact();
        if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
            throw new ArithmeticException("Overflow");
        }
        return (byte) v;
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
