package java.math;

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
public class BigInteger extends Number implements Comparable<BigInteger> {

    public static final BigInteger ZERO = new BigInteger(0L);
    public static final BigInteger ONE = new BigInteger(1L);
    public static final BigInteger TWO = new BigInteger(2L);
    public static final BigInteger TEN = new BigInteger(10L);

    private final long value;

    private BigInteger(long value) {
        this.value = value;
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
