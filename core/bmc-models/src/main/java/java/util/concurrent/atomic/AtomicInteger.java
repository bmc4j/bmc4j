package java.util.concurrent.atomic;

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.atomic.AtomicInteger} — a plain mutable
 * holder. bmc4j does not verify concurrency (that's Lincheck's job), but code that *uses* atomics
 * should still be analysable for its logic; under single-threaded analysis an atomic is just a
 * mutable int (CAS succeeds iff the witnessed value matches).
 */
@BmcModelConforms("mutable-int holder — differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
@BmcModelTail(reason = "VarHandle memory-ordering variants (getAcquire/getOpaque/getPlain/setOpaque/setPlain/setRelease/compareAndExchange*/weakCompareAndSet{Acquire,Release,Volatile}) collapse to the plain op under sequential analysis and aren't separately modeled; Number's byteValue/shortValue narrowing too. All loud under JBMC")
public class AtomicInteger extends Number {

    private int value;

    public AtomicInteger() {
    }

    public AtomicInteger(int initialValue) {
        this.value = initialValue;
    }

    public final int get() {
        return value;
    }

    public final void set(int newValue) {
        value = newValue;
    }

    public final void lazySet(int newValue) {
        value = newValue;
    }

    public final int getAndSet(int newValue) {
        int old = value;
        value = newValue;
        return old;
    }

    public final boolean compareAndSet(int expect, int update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(int expect, int update) {
        return compareAndSet(expect, update);
    }

    public final boolean weakCompareAndSetPlain(int expect, int update) {
        return compareAndSet(expect, update);
    }

    public final int getAndIncrement() {
        return value++;
    }

    public final int getAndDecrement() {
        return value--;
    }

    public final int getAndAdd(int delta) {
        int old = value;
        value += delta;
        return old;
    }

    public final int incrementAndGet() {
        return ++value;
    }

    public final int decrementAndGet() {
        return --value;
    }

    public final int addAndGet(int delta) {
        value += delta;
        return value;
    }

    public final int updateAndGet(IntUnaryOperator updateFunction) {
        value = updateFunction.applyAsInt(value);
        return value;
    }

    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int old = value;
        value = updateFunction.applyAsInt(value);
        return old;
    }

    public final int accumulateAndGet(int x, IntBinaryOperator f) {
        value = f.applyAsInt(value, x);
        return value;
    }

    @Override
    public int intValue() {
        return value;
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
