package java.math;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

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
@BmcModelTail(reason = "the probabilistic number-theory (isProbablePrime/nextProbablePrime/probablePrime) and radix formatting (toString(int)) are out of scope for a long-backed bounded model; all loud under JBMC")
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
     * {@code {this/val, this%val}} in one shot, like the JDK — the same quotient as {@link #divide} and
     * remainder as {@link #remainder}. A zero divisor throws {@link ArithmeticException}, like the JDK.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger[] divideAndRemainder(BigInteger val) {
        return new BigInteger[] {new BigInteger(value / val.value), new BigInteger(value % val.value)};
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

    /**
     * Modular exponentiation {@code this^exponent mod m} by square-and-multiply, kept exact on the
     * {@code long} backing. Mirrors the JDK: {@code m} must be positive (else ArithmeticException),
     * the result is the non-negative residue in {@code [0, m)}, and {@code exponent == 0} yields
     * {@code 1 mod m}. A NEGATIVE exponent needs the modular inverse (modInverse), which is part of the
     * unmodeled tail — so a negative exponent fails LOUDLY rather than returning a wrong value. Loud,
     * never silent at the bound: every intermediate is reduced mod {@code m} before multiplying, and the
     * product {@code (a*b)} with {@code a,b < m} is checked via {@code Math.multiplyExact}, so a modulus
     * large enough to overflow the {@code long} product throws instead of wrapping.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger modPow(BigInteger exponent, BigInteger m) {
        if (m.value <= 0L) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        if (exponent.value < 0L) {
            throw org.bmc4j.analysis.BmcUnmodelledReached.fail(
                "bmc4j: unmodelled member java.math.BigInteger.modPow(java.math.BigInteger, java.math.BigInteger)"
                    + " with a negative exponent — needs the modular inverse (modInverse), part of the unmodeled tail");
        }
        if (m.value == 1L) {
            return ZERO;
        }
        long base = value % m.value;
        if (base < 0L) {
            base += m.value;
        }
        long result = 1L % m.value;
        long e = exponent.value;
        while (e > 0L) {
            if ((e & 1L) == 1L) {
                result = Math.multiplyExact(result, base) % m.value;
            }
            e >>= 1;
            if (e > 0L) {
                base = Math.multiplyExact(base, base) % m.value;
            }
        }
        return new BigInteger(result);
    }

    /**
     * The integer square root {@code floor(sqrt(this))}, like the JDK ({@code sqrt} of a NEGATIVE value
     * throws ArithmeticException). Computed with a checked Newton/Heron iteration on the {@code long}
     * backing; every value in the {@code long} range has a square root that fits, so this never trips
     * the bound (the {@code Math.*Exact}-checked arithmetic in the iteration stays loud regardless).
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger sqrt() {
        if (value < 0L) {
            throw new ArithmeticException("negative BigInteger");
        }
        return new BigInteger(isqrt(value));
    }

    /**
     * {@code {sqrt, this - sqrt*sqrt}} in one shot, like the JDK — the floor square root and the
     * remainder it leaves. A negative value throws ArithmeticException (via {@link #sqrt()}).
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger[] sqrtAndRemainder() {
        long s = isqrt(value < 0L ? sqrtThrows() : value);
        return new BigInteger[] {new BigInteger(s), new BigInteger(value - s * s)};
    }

    private static long sqrtThrows() {
        throw new ArithmeticException("negative BigInteger");
    }

    /** floor(sqrt(n)) for n &gt;= 0 on a long. */
    private static long isqrt(long n) {
        if (n < 2L) {
            return n;
        }
        long x = (long) Math.sqrt((double) n);
        // Correct the double rounding in either direction so the result is exactly floor(sqrt(n)).
        while (x > 1L && x > n / x) {
            x--;
        }
        while ((x + 1L) <= n / (x + 1L)) {
            x++;
        }
        return x;
    }

    // --- bitwise ops (two's-complement, matching the JDK's infinite-width two's-complement view) -----
    // A long IS two's-complement, so the native long operators agree with BigInteger's conceptual
    // sign-extended two's-complement bitwise semantics bit-for-bit within the bound.

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger and(BigInteger val) {
        return new BigInteger(value & val.value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger or(BigInteger val) {
        return new BigInteger(value | val.value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger xor(BigInteger val) {
        return new BigInteger(value ^ val.value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger not() {
        return new BigInteger(~value);
    }

    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger andNot(BigInteger val) {
        return new BigInteger(value & ~val.value);
    }

    /**
     * {@code this << n}. The JDK's shift is over the infinite-width two's-complement value; on the
     * {@code long} backing a left shift that pushes set bits past bit 63 overflows the bound, so the
     * result is checked (left shift == multiply by {@code 2^n}) and fails LOUDLY rather than dropping
     * high bits. A negative {@code n} is a right shift, like the JDK. {@code n == 0} is identity.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger shiftLeft(int n) {
        if (n < 0) {
            return shiftRight(-n);
        }
        long r = value;
        for (int i = 0; i < n; i++) {
            r = Math.multiplyExact(r, 2L);   // loud if a bit would be lost past the long bound
        }
        return new BigInteger(r);
    }

    /**
     * {@code this >> n}, an ARITHMETIC (sign-extending) shift like the JDK's two's-complement
     * {@code shiftRight} (which rounds toward negative infinity). A negative {@code n} is a left shift.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger shiftRight(int n) {
        if (n < 0) {
            return shiftLeft(-n);
        }
        if (n >= 64) {
            return new BigInteger(value >> 63);   // collapses to 0 (>=0) or -1 (<0)
        }
        return new BigInteger(value >> n);
    }

    /**
     * Returns {@code true} iff bit {@code n} is set in the two's-complement representation. A negative
     * index throws ArithmeticException, like the JDK. For {@code n >= 64} the answer is the (infinitely
     * repeated) sign bit, so a negative value reads {@code true} and a non-negative value {@code false}.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public boolean testBit(int n) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        if (n >= 64) {
            return value < 0L;
        }
        return ((value >> n) & 1L) != 0L;
    }

    /** Set bit {@code n} (two's-complement). Negative index throws, like the JDK; a high index may push
     *  the value past the {@code long} bound, which fails LOUDLY (checked add of the bit's weight). */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger setBit(int n) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        if (n >= 64) {
            // The bit is already 1 for a negative value (sign extension); for a non-negative value it
            // would require precision past the long bound — loud, never a silent wrong value.
            if (value < 0L) {
                return this;
            }
            throw new ArithmeticException("BigInteger model overflow: setBit past the ~63-bit bound");
        }
        return new BigInteger(value | (1L << n));
    }

    /** Clear bit {@code n} (two's-complement). Negative index throws, like the JDK. */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger clearBit(int n) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        if (n >= 64) {
            // For a non-negative value the bit is already 0; for a negative value clearing a sign bit
            // needs precision past the long bound — loud, never silent.
            if (value >= 0L) {
                return this;
            }
            throw new ArithmeticException("BigInteger model overflow: clearBit past the ~63-bit bound");
        }
        return new BigInteger(value & ~(1L << n));
    }

    /** Flip bit {@code n} (two's-complement). Negative index throws, like the JDK. */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger flipBit(int n) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        if (n >= 64) {
            throw new ArithmeticException("BigInteger model overflow: flipBit past the ~63-bit bound");
        }
        return new BigInteger(value ^ (1L << n));
    }

    /**
     * The index of the rightmost set bit, or {@code -1} when {@code this == 0} — exactly the JDK
     * contract. {@code Long.numberOfTrailingZeros} gives this directly on the {@code long} backing
     * (it returns 64 for zero, which we map to {@code -1}).
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int getLowestSetBit() {
        return value == 0L ? -1 : Long.numberOfTrailingZeros(value);
    }

    /**
     * The number of bits in the two's-complement representation that DIFFER from the sign bit — the
     * JDK's {@code bitCount}. For {@code value >= 0} that is {@code Long.bitCount(value)}; for a negative
     * value it is the popcount of {@code ~value} (the bits differing from the all-ones sign).
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int bitCount() {
        return Long.bitCount(value >= 0L ? value : ~value);
    }

    /**
     * The number of bits in the minimal two's-complement representation EXCLUDING the sign bit — the
     * JDK's {@code bitLength}: {@code ceil(log2(value < 0 ? -value : value + 1))}. {@code bitLength()}
     * of 0 and -1 is 0. Computed from leading zeros: for {@code value >= 0} it's
     * {@code 64 - nlz(value)}; for a negative value it's {@code 64 - nlz(~value)}.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int bitLength() {
        long magnitudeBits = value >= 0L ? value : ~value;
        return 64 - Long.numberOfLeadingZeros(magnitudeBits);
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
    public byte byteValue() {
        return (byte) value;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public short shortValue() {
        return (short) value;
    }

    /**
     * The exact {@code byte} value, throwing {@link ArithmeticException} ("BigInteger out of byte
     * range") if it doesn't fit in a {@code byte} — exactly the JDK contract.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public byte byteValueExact() {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new ArithmeticException("BigInteger out of byte range");
        }
        return (byte) value;
    }

    /**
     * The exact {@code short} value, throwing {@link ArithmeticException} ("BigInteger out of short
     * range") if it doesn't fit in a {@code short} — exactly the JDK contract.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public short shortValueExact() {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new ArithmeticException("BigInteger out of short range");
        }
        return (short) value;
    }

    @Override
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public long longValue() {
        return value;
    }

    /**
     * The exact {@code int} value, throwing {@link ArithmeticException} if it doesn't fit — exactly the
     * JDK contract. On the {@code long} backing this is the {@code Math.toIntExact} range check.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public int intValueExact() {
        return Math.toIntExact(value);
    }

    /**
     * The exact {@code long} value. The {@code long}-backed model can only hold values that fit a
     * {@code long}, so within the bound this never throws (the arbitrary-precision JDK throws here only
     * for magnitudes past the {@code long} range, which this bounded model cannot represent anyway).
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public long longValueExact() {
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

    /**
     * The modular multiplicative inverse {@code this^-1 mod m}: the unique value {@code x} in
     * {@code [0, m)} with {@code this·x ≡ 1 (mod m)}, computed by the extended Euclidean algorithm on
     * the {@code long} backing. Mirrors the JDK: {@code m} must be positive (else ArithmeticException
     * "BigInteger: modulus not positive"), and if {@code this} is not invertible modulo {@code m}
     * (i.e. {@code gcd(this, m) != 1}, including {@code m == 1} where the only residue is 0) it throws
     * ArithmeticException "BigInteger not invertible." Loud, never silent at the bound: the modulus and
     * residues are below {@code m}, and the extended-Euclid bookkeeping stays within the {@code long}
     * range for any {@code m} that fits the backing.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger modInverse(BigInteger m) {
        if (m.value <= 0L) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        if (m.value == 1L) {
            // The ring Z/1 has the single residue 0; nothing is invertible there (gcd(this,1)==1 but the
            // inverse, reduced mod 1, is 0 — the JDK returns ZERO here).
            return ZERO;
        }
        // Reduce this into [0, m) first (the inverse depends only on the residue class).
        long a = value % m.value;
        if (a < 0L) {
            a += m.value;
        }
        // Extended Euclid: track the Bezout coefficient of `a` only (t), modulo m.value.
        long oldR = a;
        long r = m.value;
        long oldT = 1L;
        long t = 0L;
        while (r != 0L) {
            long q = oldR / r;
            long tmpR = oldR - q * r;
            oldR = r;
            r = tmpR;
            long tmpT = oldT - q * t;
            oldT = t;
            t = tmpT;
        }
        if (oldR != 1L) {
            throw new ArithmeticException("BigInteger not invertible.");
        }
        long inv = oldT % m.value;
        if (inv < 0L) {
            inv += m.value;
        }
        return new BigInteger(inv);
    }

    /**
     * {@code this · val}, identical to {@link #multiply(BigInteger)} — the JDK's {@code parallelMultiply}
     * differs only by using multiple threads for very large magnitudes, an optimization with no
     * observable effect on the (single-threaded) bounded model. Loud past the {@code long} bound, exactly
     * like {@link #multiply(BigInteger)}.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public BigInteger parallelMultiply(BigInteger val) {
        return multiply(val);
    }

    /**
     * The minimal two's-complement big-endian byte array, exactly the JDK contract: the array's length
     * is {@code bitLength()/8 + 1}, the high bit of {@code byte[0]} is the sign bit, and the value of
     * {@code 0} is the single byte {@code {0}}. On the {@code long} backing this is the minimal-length
     * big-endian encoding of the (signed) {@code long}; every {@code long} value encodes in at most 8
     * bytes, so this never trips the bound.
     */
    @BmcModelConforms("differential (BigIntegerConformanceTest) + @BmcProof (proofs.biginteger)")
    public byte[] toByteArray() {
        // Number of bytes the JDK uses: one more than the minimal magnitude bit-length over 8 (the extra
        // bit/byte carries the sign), with a floor of one byte (value 0 -> {0}).
        int byteLen = bitLength() / 8 + 1;
        byte[] out = new byte[byteLen];
        long v = value;
        for (int i = byteLen - 1; i >= 0; i--) {
            out[i] = (byte) (v & 0xFFL);
            v >>= 8;   // arithmetic shift sign-extends, matching two's-complement big-endian
        }
        return out;
    }

    @BmcUnmodelable(reason = "probabilistic primality (Miller-Rabin/Lucas) — genuine number-theoretic wall, no sound bounded model")
    public boolean isProbablePrime(int certainty) {
        throw fail("bmc4j: unmodelled member java.math.BigInteger.isProbablePrime(int)"
            + " — probabilistic primality (Miller-Rabin/Lucas) is a genuine number-theoretic wall with no sound bounded model");
    }

    @BmcUnmodelable(reason = "probabilistic primality search — genuine number-theoretic wall, no sound bounded model")
    public BigInteger nextProbablePrime() {
        throw fail("bmc4j: unmodelled member java.math.BigInteger.nextProbablePrime()"
            + " — probabilistic primality search is a genuine number-theoretic wall with no sound bounded model");
    }

    @BmcUnmodelable(reason = "probabilistic prime generation over a Random source — genuine number-theoretic wall, no sound bounded model")
    public static BigInteger probablePrime(int bitLength, java.util.Random rnd) {
        throw fail("bmc4j: unmodelled member java.math.BigInteger.probablePrime(int, java.util.Random)"
            + " — probabilistic prime generation is a genuine number-theoretic wall with no sound bounded model");
    }

    @BmcUnmodelable(reason = "radix formatting (dtoa-class) — not soundly modelable in the bounded model")
    public String toString(int radix) {
        throw fail("bmc4j: unmodelled member java.math.BigInteger.toString(int)"
            + " — radix formatting (dtoa-class) is not soundly modelable in the bounded model");
    }
}
