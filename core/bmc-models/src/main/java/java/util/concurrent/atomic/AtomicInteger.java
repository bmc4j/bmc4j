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
@BmcModelTail(reason = "VarHandle memory-ordering variants (getAcquire/getOpaque/getPlain/setOpaque/setPlain/setRelease/compareAndExchange*/weakCompareAndSet{Acquire,Release,Volatile}) collapse to the plain op under sequential analysis and aren't separately modeled; Number's byteValue/shortValue narrowing too. All loud under JBMC")
public class AtomicInteger extends Number {

    private int value;

    public AtomicInteger() {
    }

    public AtomicInteger(int initialValue) {
        this.value = initialValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void set(int newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void lazySet(int newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int getAndSet(int newValue) {
        int old = value;
        value = newValue;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean compareAndSet(int expect, int update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean weakCompareAndSet(int expect, int update) {
        return compareAndSet(expect, update);
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean weakCompareAndSetPlain(int expect, int update) {
        return compareAndSet(expect, update);
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int getAndIncrement() {
        return value++;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int getAndDecrement() {
        return value--;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int getAndAdd(int delta) {
        int old = value;
        value += delta;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int incrementAndGet() {
        return ++value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int decrementAndGet() {
        return --value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int addAndGet(int delta) {
        value += delta;
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int updateAndGet(IntUnaryOperator updateFunction) {
        value = updateFunction.applyAsInt(value);
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int old = value;
        value = updateFunction.applyAsInt(value);
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int getAndAccumulate(int x, IntBinaryOperator f) {
        int old = value;
        value = f.applyAsInt(value, x);
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final int accumulateAndGet(int x, IntBinaryOperator f) {
        value = f.applyAsInt(value, x);
        return value;
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public int intValue() {
        return value;
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public long longValue() {
        return value;
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public float floatValue() {
        return value;
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public double doubleValue() {
        return value;
    }
}
