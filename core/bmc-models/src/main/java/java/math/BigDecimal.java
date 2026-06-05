package java.math;

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
 * exponent notation).
 */
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
            u = u * 10 + (c - '0');
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

    public static BigDecimal valueOf(long val) {
        return new BigDecimal(val, 0);
    }

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

    public BigDecimal add(BigDecimal o) {
        int s = Math.max(scale, o.scale);
        return new BigDecimal(rescale(unscaled, scale, s) + rescale(o.unscaled, o.scale, s), s);
    }

    public BigDecimal subtract(BigDecimal o) {
        int s = Math.max(scale, o.scale);
        return new BigDecimal(rescale(unscaled, scale, s) - rescale(o.unscaled, o.scale, s), s);
    }

    public BigDecimal multiply(BigDecimal o) {
        return new BigDecimal(mul(unscaled, o.unscaled), scale + o.scale);
    }

    public BigDecimal divide(BigDecimal divisor, int newScale, RoundingMode mode) {
        int e = divisor.scale + newScale - scale;
        long num = e >= 0 ? mul(unscaled, pow10(e)) : unscaled;
        long den = e >= 0 ? divisor.unscaled : mul(divisor.unscaled, pow10(-e));
        return new BigDecimal(roundDiv(num, den, mode), newScale);
    }

    public BigDecimal divide(BigDecimal divisor, RoundingMode mode) {
        return divide(divisor, scale, mode);
    }

    public BigDecimal setScale(int newScale, RoundingMode mode) {
        if (newScale >= scale) {
            return new BigDecimal(rescale(unscaled, scale, newScale), newScale);
        }
        return new BigDecimal(roundDiv(unscaled, pow10(scale - newScale), mode), newScale);
    }

    public BigDecimal negate() {
        return new BigDecimal(-unscaled, scale);
    }

    public BigDecimal abs() {
        return unscaled < 0 ? negate() : this;
    }

    public int signum() {
        return unscaled < 0 ? -1 : unscaled > 0 ? 1 : 0;
    }

    public BigDecimal min(BigDecimal o) {
        return compareTo(o) <= 0 ? this : o;
    }

    public BigDecimal max(BigDecimal o) {
        return compareTo(o) >= 0 ? this : o;
    }

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

    public int scale() {
        return scale;
    }

    public BigInteger unscaledValue() {
        return BigInteger.valueOf(unscaled);
    }

    public BigInteger toBigInteger() {
        return BigInteger.valueOf(truncatedToLong());
    }

    // --- comparison ----------------------------------------------------------

    @Override
    public int compareTo(BigDecimal o) {
        int s = Math.max(scale, o.scale);
        return Long.compare(rescale(unscaled, scale, s), rescale(o.unscaled, o.scale, s));
    }

    /** Scale-sensitive, like the real BigDecimal: 2.0 and 2.00 are NOT equal (use compareTo). */
    @Override
    public boolean equals(Object x) {
        if (!(x instanceof BigDecimal)) {
            return false;
        }
        BigDecimal o = (BigDecimal) x;
        return o.unscaled == unscaled && o.scale == scale;
    }

    @Override
    public int hashCode() {
        return 31 * (int) (unscaled ^ (unscaled >>> 32)) + scale;
    }

    // --- Number --------------------------------------------------------------

    @Override
    public int intValue() {
        return (int) truncatedToLong();
    }

    @Override
    public long longValue() {
        return truncatedToLong();
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    @Override
    public double doubleValue() {
        return scale <= 0 ? (double) (unscaled * pow10(-scale)) : (double) unscaled / (double) pow10(scale);
    }
}
