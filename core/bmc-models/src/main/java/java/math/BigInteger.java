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
@BmcModelTail(reason = "bitwise ops (and/or/xor/not/shift*/testBit/setBit/clearBit/flipBit/bitCount/bitLength/getLowestSetBit), the *Exact narrowing, the remaining number-theory (modInverse/modPow/sqrt*/isProbablePrime/nextProbablePrime/probablePrime), and serialization (toByteArray/toString(int)/parallelMultiply) are out of scope for a long-backed bounded model; all loud under JBMC")
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

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public static BigInteger valueOf(long value) {
        return new BigInteger(value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger add(BigInteger other) {
        return new BigInteger(Math.addExact(value, other.value));
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger subtract(BigInteger other) {
        return new BigInteger(Math.subtractExact(value, other.value));
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger multiply(BigInteger other) {
        return new BigInteger(Math.multiplyExact(value, other.value));
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger divide(BigInteger other) {
        return new BigInteger(value / other.value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger mod(BigInteger m) {
        if (m.value <= 0L) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        long r = value % m.value;
        return new BigInteger(r < 0 ? r + m.value : r);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger remainder(BigInteger other) {
        return new BigInteger(value % other.value);
    }

    /**
     * Euclidean GCD on the {@code long} backing. The JDK's {@code gcd} is the non-negative greatest
     * common divisor, with {@code gcd(0, 0) == 0} and {@code gcd(x, 0) == abs(x)}; sign of the
     * operands is irrelevant. Euclid's {@code %} loop preserves that over signed longs, so we run it
     * on the raw values and take the absolute value of the result.
     *
     * <p>Loud, never silent at the bound: the only value whose absolute value leaves the {@code long}
     * range is {@code abs(Long.MIN_VALUE)} (= {@code gcd(Long.MIN_VALUE, 0)}), which the
     * arbitrary-precision JDK returns; {@code Math.absExact} makes the bounded model throw there rather
     * than wrap. Every other gcd fits, because a gcd never exceeds {@code max(|a|, |b|)}.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger gcd(BigInteger val) {
        long a = value;
        long b = val.value;
        while (b != 0L) {
            long t = a % b;
            a = b;
            b = t;
        }
        return new BigInteger(Math.absExact(a));
    }

    /**
     * {@code this} raised to {@code exponent}, by repeated checked multiplication. Mirrors the JDK:
     * {@code pow(0) == ONE} (even for {@code ZERO}), and a negative exponent throws
     * {@link ArithmeticException}. Loud, never silent: an intermediate product that leaves the
     * {@code long} range fails via {@code Math.multiplyExact} rather than wrapping.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger pow(int exponent) {
        if (exponent < 0) {
            throw new ArithmeticException("Negative exponent");
        }
        long r = 1L;
        for (int i = 0; i < exponent; i++) {
            r = Math.multiplyExact(r, value);
        }
        return new BigInteger(r);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger negate() {
        return new BigInteger(Math.negateExact(value));
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger abs() {
        return new BigInteger(Math.absExact(value));
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int signum() {
        return Long.compare(value, 0L);
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int compareTo(BigInteger other) {
        return Long.compare(value, other.value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger min(BigInteger other) {
        return value <= other.value ? this : other;
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger max(BigInteger other) {
        return value >= other.value ? this : other;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public boolean equals(Object o) {
        return o instanceof BigInteger && ((BigInteger) o).value == value;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int intValue() {
        return (int) value;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public long longValue() {
        return value;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public float floatValue() {
        return (float) value;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public double doubleValue() {
        return (double) value;
    }
}
