package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.atomic.AtomicLong} — a mutable long holder.
 *
 * <p>The VarHandle memory-ordering accessors (the {@code *Acquire}/{@code *Release}/{@code *Opaque}/
 * {@code *Plain} reads and writes, {@code compareAndExchange*}, and the {@code weakCompareAndSet*}
 * family) only relax the happens-before guarantees of the plain op for performance; on one thread
 * there is no other thread to observe a relaxed ordering, so each is observably identical to its
 * plain/strong counterpart and is modeled by delegating to it (documented per method).
 */
@BmcModelTail(reason = "Number's byteValue()/shortValue() narrowing is out of scope for the long-backed model; loud under JBMC")
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

    /**
     * Weak CAS may spuriously fail under contention; on a single thread there is no contention, so it
     * never spuriously fails and is identical to {@link #compareAndSet(long, long)}.
     */
    @Deprecated
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSet(long expect, long update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (plain memory); no spurious failure on one thread → identical to {@link #compareAndSet(long, long)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetPlain(long expect, long update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (acquire); no spurious failure on one thread → identical to {@link #compareAndSet(long, long)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetAcquire(long expect, long update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (release); no spurious failure on one thread → identical to {@link #compareAndSet(long, long)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetRelease(long expect, long update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (volatile); no spurious failure on one thread → identical to {@link #compareAndSet(long, long)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetVolatile(long expect, long update) {
        return compareAndSet(expect, update);
    }

    /** Plain read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final long getPlain() {
        return value;
    }

    /** Acquire read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final long getAcquire() {
        return value;
    }

    /** Opaque read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final long getOpaque() {
        return value;
    }

    /** Plain write; identical to {@link #set(long)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setPlain(long newValue) {
        value = newValue;
    }

    /** Release write; identical to {@link #set(long)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setRelease(long newValue) {
        value = newValue;
    }

    /** Opaque write; identical to {@link #set(long)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setOpaque(long newValue) {
        value = newValue;
    }

    /** CAS returning the witnessed value; the plain compareAndSet logic, returning the prior value. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); plain compare-and-exchange on one thread")
    public final long compareAndExchange(long expectedValue, long newValue) {
        long witnessed = value;
        if (witnessed == expectedValue) {
            value = newValue;
        }
        return witnessed;
    }

    /** Acquire-mode {@link #compareAndExchange(long, long)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final long compareAndExchangeAcquire(long expectedValue, long newValue) {
        return compareAndExchange(expectedValue, newValue);
    }

    /** Release-mode {@link #compareAndExchange(long, long)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final long compareAndExchangeRelease(long expectedValue, long newValue) {
        return compareAndExchange(expectedValue, newValue);
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
