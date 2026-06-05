package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

/** Sequential BMC model of {@link java.util.concurrent.atomic.AtomicLong} — a mutable long holder. */
public class AtomicLong extends Number {

    private long value;

    public AtomicLong() {
    }

    public AtomicLong(long initialValue) {
        this.value = initialValue;
    }

    public final long get() {
        return value;
    }

    public final void set(long newValue) {
        value = newValue;
    }

    public final void lazySet(long newValue) {
        value = newValue;
    }

    public final long getAndSet(long newValue) {
        long old = value;
        value = newValue;
        return old;
    }

    public final boolean compareAndSet(long expect, long update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final long getAndIncrement() {
        return value++;
    }

    public final long getAndDecrement() {
        return value--;
    }

    public final long getAndAdd(long delta) {
        long old = value;
        value += delta;
        return old;
    }

    public final long incrementAndGet() {
        return ++value;
    }

    public final long decrementAndGet() {
        return --value;
    }

    public final long addAndGet(long delta) {
        value += delta;
        return value;
    }

    public final long updateAndGet(LongUnaryOperator updateFunction) {
        value = updateFunction.applyAsLong(value);
        return value;
    }

    public final long accumulateAndGet(long x, LongBinaryOperator f) {
        value = f.applyAsLong(value, x);
        return value;
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
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }
}
