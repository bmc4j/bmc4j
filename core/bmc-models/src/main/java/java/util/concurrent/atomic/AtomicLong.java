package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Sequential BMC model of {@link java.util.concurrent.atomic.AtomicLong} — a mutable long holder. */
@BmcModelTail(reason = "VarHandle memory-ordering variants collapse to the plain op under sequential analysis; Number's byteValue/shortValue/intValue/floatValue/doubleValue narrowing where unmodeled. All loud under JBMC")
public class AtomicLong extends Number {

    private long value;

    public AtomicLong() {
    }

    public AtomicLong(long initialValue) {
        this.value = initialValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void set(long newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void lazySet(long newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long getAndSet(long newValue) {
        long old = value;
        value = newValue;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean compareAndSet(long expect, long update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long getAndIncrement() {
        return value++;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long getAndDecrement() {
        return value--;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long getAndAdd(long delta) {
        long old = value;
        value += delta;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long incrementAndGet() {
        return ++value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long decrementAndGet() {
        return --value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long addAndGet(long delta) {
        value += delta;
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long updateAndGet(LongUnaryOperator updateFunction) {
        value = updateFunction.applyAsLong(value);
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long getAndUpdate(LongUnaryOperator updateFunction) {
        long old = value;
        value = updateFunction.applyAsLong(value);
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long getAndAccumulate(long x, LongBinaryOperator f) {
        long old = value;
        value = f.applyAsLong(value, x);
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final long accumulateAndGet(long x, LongBinaryOperator f) {
        value = f.applyAsLong(value, x);
        return value;
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public int intValue() {
        return (int) value;
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
