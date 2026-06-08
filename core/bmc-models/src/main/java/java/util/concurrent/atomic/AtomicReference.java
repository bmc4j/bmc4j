package java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Sequential BMC model of {@link java.util.concurrent.atomic.AtomicReference} — a mutable holder.
 *
 * <p>The VarHandle memory-ordering accessors (the {@code *Acquire}/{@code *Release}/{@code *Opaque}/
 * {@code *Plain} reads and writes, {@code compareAndExchange*}, and the {@code weakCompareAndSet*}
 * family) only relax the happens-before guarantees of the plain op for performance; on one thread
 * there is no other thread to observe a relaxed ordering, so each is observably identical to its
 * plain/strong counterpart and is modeled by delegating to it (documented per method). Identity (==)
 * comparison is used for the expected value, matching {@link java.util.concurrent.atomic.AtomicReference}.
 */
public class AtomicReference<V> {

    private V value;

    public AtomicReference() {
    }

    public AtomicReference(V initialValue) {
        this.value = initialValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void set(V newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void lazySet(V newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V getAndSet(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean compareAndSet(V expect, V update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V updateAndGet(UnaryOperator<V> updateFunction) {
        value = updateFunction.apply(value);
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V getAndUpdate(UnaryOperator<V> updateFunction) {
        V old = value;
        value = updateFunction.apply(value);
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V accumulateAndGet(V x, BinaryOperator<V> f) {
        value = f.apply(value, x);
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V getAndAccumulate(V x, BinaryOperator<V> f) {
        V old = value;
        value = f.apply(value, x);
        return old;
    }

    // --- VarHandle memory-ordering variants: equal to their plain/strong counterpart on one thread ---

    /** Plain read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final V getPlain() {
        return value;
    }

    /** Acquire read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final V getAcquire() {
        return value;
    }

    /** Opaque read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final V getOpaque() {
        return value;
    }

    /** Plain write; identical to {@link #set(Object)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setPlain(V newValue) {
        value = newValue;
    }

    /** Release write; identical to {@link #set(Object)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setRelease(V newValue) {
        value = newValue;
    }

    /** Opaque write; identical to {@link #set(Object)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setOpaque(V newValue) {
        value = newValue;
    }

    /** CAS returning the witnessed value; the plain compareAndSet logic, returning the prior value. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); plain compare-and-exchange on one thread")
    public final V compareAndExchange(V expectedValue, V newValue) {
        V witnessed = value;
        if (witnessed == expectedValue) {
            value = newValue;
        }
        return witnessed;
    }

    /** Acquire-mode {@link #compareAndExchange(Object, Object)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final V compareAndExchangeAcquire(V expectedValue, V newValue) {
        return compareAndExchange(expectedValue, newValue);
    }

    /** Release-mode {@link #compareAndExchange(Object, Object)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final V compareAndExchangeRelease(V expectedValue, V newValue) {
        return compareAndExchange(expectedValue, newValue);
    }

    /**
     * Weak CAS may spuriously fail under contention; on a single thread there is no contention, so it
     * never spuriously fails and is identical to {@link #compareAndSet(Object, Object)}.
     */
    @Deprecated
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSet(V expectedValue, V newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (plain memory); no spurious failure on one thread → identical to {@link #compareAndSet(Object, Object)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetPlain(V expectedValue, V newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (acquire); no spurious failure on one thread → identical to {@link #compareAndSet(Object, Object)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetAcquire(V expectedValue, V newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (release); no spurious failure on one thread → identical to {@link #compareAndSet(Object, Object)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetRelease(V expectedValue, V newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (volatile); no spurious failure on one thread → identical to {@link #compareAndSet(Object, Object)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetVolatile(V expectedValue, V newValue) {
        return compareAndSet(expectedValue, newValue);
    }
}
