package java.math;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Bounded BMC model of {@link java.math.BigInteger}, backed by a {@code long}. Sound for values
 * within the {@code long} range (±9.2e18). This is a DELIBERATELY bounded model (real BigInteger is
 * arbitrary-precision); rather than silently wrap when a result leaves the {@code long} range, the
 * arithmetic routes through checked {@code Math.*Exact} operations so an out-of-bound result fails
 * LOUDLY (ArithmeticException, which {@code MathBytecode} surfaces as a property violation under
 * analysis) instead of silently returning a wrong value — honoring the "loud, never silent" model
 * contract. Within the bound, behavior matches the JDK; beyond it the model throws where the
 * (arbitrary-precision) JDK would not. Covers the common
 * valueOf/add/subtract/multiply/divide/mod/compareTo/intValue surface.
 */
@BmcModelConforms("long-backed bounded BigInteger — differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
@BmcModelTail(reason = "bitwise ops (and/or/xor/not/shift*/testBit/setBit/clearBit/flipBit/bitCount/bitLength/getLowestSetBit), the *Exact narrowing, number-theory (modInverse/modPow/gcd-variants/sqrt*/isProbablePrime/nextProbablePrime/probablePrime), and serialization (toByteArray/toString(int)/parallelMultiply) are out of scope for a long-backed bounded model; all loud under JBMC")
public class BigInteger extends Number implements Comparable<BigInteger> {

    public static final BigInteger ZERO = new BigInteger(0L);
    public static final BigInteger ONE = new BigInteger(1L);
    public static final BigInteger TWO = new BigInteger(2L);
    public static final BigInteger TEN = new BigInteger(10L);

    private final long value;

    private BigInteger(long value) {
        this.value = value;
    }

    /**
     * Decimal string constructor, parsed into the {@code long} backing. Accepts exactly what the JDK
     * accepts in radix 10: an optional leading {@code +}/{@code -} then one or more ASCII digits
     * (no whitespace, no decimal point, no other characters). Garbage (empty, {@code "-"}, letters,
     * embedded spaces, …) throws {@link NumberFormatException}, like the JDK.
     *
     * <p>Out-of-domain values are LOUD, never silent: a magnitude past the {@code long} range routes
     * through {@code Math.*Exact}, so the arbitrary-precision values the JDK would accept fail with an
     * ArithmeticException (surfaced as a property violation under analysis) rather than wrapping to a
     * wrong value — the same bounded-model contract as the rest of this class and BigDecimal(String).
     */
    public BigInteger(String val) {
        int n = val.length();
        int i = 0;
        boolean neg = false;
        if (n > 0 && (val.charAt(0) == '-' || val.charAt(0) == '+')) {
            neg = val.charAt(0) == '-';
            i = 1;
        }
        if (i >= n) {
            throw new NumberFormatException("Zero length BigInteger");   // "", "-", "+"
        }
        long acc = 0;
        for (; i < n; i++) {
            char c = val.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("For input string: \"" + val + "\"");
            }
            // acc = acc*10 + digit, with loud overflow past the long bound (never silent wrap).
            acc = Math.addExact(Math.multiplyExact(acc, 10L), (long) (c - '0'));
        }
        this.value = neg ? Math.negateExact(acc) : acc;
    }

    public static BigInteger valueOf(long value) {
        return new BigInteger(value);
    }

    public BigInteger add(BigInteger other) {
        return new BigInteger(Math.addExact(value, other.value));
    }

    public BigInteger subtract(BigInteger other) {
        return new BigInteger(Math.subtractExact(value, other.value));
    }

    public BigInteger multiply(BigInteger other) {
        return new BigInteger(Math.multiplyExact(value, other.value));
    }

    public BigInteger divide(BigInteger other) {
        return new BigInteger(value / other.value);
    }

    public BigInteger mod(BigInteger m) {
        if (m.value <= 0L) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        long r = value % m.value;
        return new BigInteger(r < 0 ? r + m.value : r);
    }

    public BigInteger remainder(BigInteger other) {
        return new BigInteger(value % other.value);
    }

    public BigInteger negate() {
        return new BigInteger(Math.negateExact(value));
    }

    public BigInteger abs() {
        return new BigInteger(Math.absExact(value));
    }

    public int signum() {
        return Long.compare(value, 0L);
    }

    @Override
    public int compareTo(BigInteger other) {
        return Long.compare(value, other.value);
    }

    public BigInteger min(BigInteger other) {
        return value <= other.value ? this : other;
    }

    public BigInteger max(BigInteger other) {
        return value >= other.value ? this : other;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BigInteger && ((BigInteger) o).value == value;
    }

    @Override
    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return (float) value;
    }

    @Override
    public double doubleValue() {
        return (double) value;
    }
}
