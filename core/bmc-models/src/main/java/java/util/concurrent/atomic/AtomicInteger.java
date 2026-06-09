package java.util.concurrent.atomic;

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Sequential BMC model of {@link java.util.concurrent.atomic.AtomicInteger} — a plain mutable
 * holder. bmc4j does not verify concurrency (that's Lincheck's job), but code that *uses* atomics
 * should still be analysable for its logic; under single-threaded analysis an atomic is just a
 * mutable int (CAS succeeds iff the witnessed value matches).
 *
 * <p>The VarHandle memory-ordering accessors (the {@code *Acquire}/{@code *Release}/{@code *Opaque}/
 * {@code *Plain} reads and writes, {@code compareAndExchange*}, and the {@code weakCompareAndSet*}
 * family) only relax the happens-before guarantees of the plain op for performance; on one thread
 * there is no other thread to observe a relaxed ordering, so each is observably identical to its
 * plain/strong counterpart and is modeled by delegating to it (documented per method).
 */
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

    /**
     * Weak CAS may spuriously fail under contention; on a single thread there is no contention, so it
     * never spuriously fails and is identical to {@link #compareAndSet(int, int)}.
     */
    @Deprecated
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean weakCompareAndSet(int expect, int update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (plain memory); no spurious failure on one thread → identical to {@link #compareAndSet(int, int)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean weakCompareAndSetPlain(int expect, int update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (acquire); no spurious failure on one thread → identical to {@link #compareAndSet(int, int)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetAcquire(int expect, int update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (release); no spurious failure on one thread → identical to {@link #compareAndSet(int, int)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetRelease(int expect, int update) {
        return compareAndSet(expect, update);
    }

    /** Weak CAS (volatile); no spurious failure on one thread → identical to {@link #compareAndSet(int, int)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetVolatile(int expect, int update) {
        return compareAndSet(expect, update);
    }

    /** Plain read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final int getPlain() {
        return value;
    }

    /** Acquire read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final int getAcquire() {
        return value;
    }

    /** Opaque read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final int getOpaque() {
        return value;
    }

    /** Plain write; identical to {@link #set(int)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setPlain(int newValue) {
        value = newValue;
    }

    /** Release write; identical to {@link #set(int)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setRelease(int newValue) {
        value = newValue;
    }

    /** Opaque write; identical to {@link #set(int)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setOpaque(int newValue) {
        value = newValue;
    }

    /** CAS returning the witnessed value; the plain compareAndSet logic, returning the prior value. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); plain compare-and-exchange on one thread")
    public final int compareAndExchange(int expectedValue, int newValue) {
        int witnessed = value;
        if (witnessed == expectedValue) {
            value = newValue;
        }
        return witnessed;
    }

    /** Acquire-mode {@link #compareAndExchange(int, int)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final int compareAndExchangeAcquire(int expectedValue, int newValue) {
        return compareAndExchange(expectedValue, newValue);
    }

    /** Release-mode {@link #compareAndExchange(int, int)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final int compareAndExchangeRelease(int expectedValue, int newValue) {
        return compareAndExchange(expectedValue, newValue);
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

    /** Narrowing read inherited from {@link Number}; the int value truncated to a byte (sequential, exact). */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest); Number narrowing == (byte) get() on one thread")
    public byte byteValue() {
        return (byte) value;
    }

    /** Narrowing read inherited from {@link Number}; the int value truncated to a short (sequential, exact). */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest); Number narrowing == (short) get() on one thread")
    public short shortValue() {
        return (short) value;
    }
}
