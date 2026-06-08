package java.util.concurrent.atomic;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Sequential BMC model of {@link java.util.concurrent.atomic.AtomicBoolean} — a mutable boolean.
 *
 * <p>The VarHandle memory-ordering accessors (the {@code *Acquire}/{@code *Release}/{@code *Opaque}/
 * {@code *Plain} reads and writes, {@code compareAndExchange*}, and the {@code weakCompareAndSet*}
 * family) exist only to relax the happens-before guarantees of the plain volatile op for performance.
 * bmc4j analyzes a single thread (it proves logic, not interleavings — Lincheck does the latter), and
 * on one thread there are no other threads to observe a relaxed ordering: every fence variant is
 * observably identical to its plain/strong counterpart. So each is modeled by delegating to that
 * counterpart, and the equivalence is documented per method below.
 */
public class AtomicBoolean {

    private boolean value;

    public AtomicBoolean() {
    }

    public AtomicBoolean(boolean initialValue) {
        this.value = initialValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void set(boolean newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void lazySet(boolean newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean getAndSet(boolean newValue) {
        boolean old = value;
        value = newValue;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean compareAndSet(boolean expect, boolean update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    // --- VarHandle memory-ordering variants: equal to their plain/strong counterpart on one thread ---

    /** Plain read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final boolean getPlain() {
        return value;
    }

    /** Acquire read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final boolean getAcquire() {
        return value;
    }

    /** Opaque read; identical to {@link #get()} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == get() on one thread")
    public final boolean getOpaque() {
        return value;
    }

    /** Plain write; identical to {@link #set(boolean)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setPlain(boolean newValue) {
        value = newValue;
    }

    /** Release write; identical to {@link #set(boolean)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setRelease(boolean newValue) {
        value = newValue;
    }

    /** Opaque write; identical to {@link #set(boolean)} on a single thread (no ordering to relax). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == set() on one thread")
    public final void setOpaque(boolean newValue) {
        value = newValue;
    }

    /** CAS returning the witnessed value; the plain compareAndSet logic, returning the prior value. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); plain compare-and-exchange on one thread")
    public final boolean compareAndExchange(boolean expectedValue, boolean newValue) {
        boolean witnessed = value;
        if (witnessed == expectedValue) {
            value = newValue;
        }
        return witnessed;
    }

    /** Acquire-mode {@link #compareAndExchange(boolean, boolean)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final boolean compareAndExchangeAcquire(boolean expectedValue, boolean newValue) {
        return compareAndExchange(expectedValue, newValue);
    }

    /** Release-mode {@link #compareAndExchange(boolean, boolean)}; identical on one thread. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndExchange() on one thread")
    public final boolean compareAndExchangeRelease(boolean expectedValue, boolean newValue) {
        return compareAndExchange(expectedValue, newValue);
    }

    /**
     * Weak CAS may spuriously fail under contention; on a single thread there is no contention, so it
     * never spuriously fails and is identical to {@link #compareAndSet(boolean, boolean)}.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetPlain(boolean expectedValue, boolean newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (acquire); no spurious failure on one thread → identical to {@link #compareAndSet(boolean, boolean)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetAcquire(boolean expectedValue, boolean newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (release); no spurious failure on one thread → identical to {@link #compareAndSet(boolean, boolean)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetRelease(boolean expectedValue, boolean newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /** Weak CAS (volatile); no spurious failure on one thread → identical to {@link #compareAndSet(boolean, boolean)}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSetVolatile(boolean expectedValue, boolean newValue) {
        return compareAndSet(expectedValue, newValue);
    }

    /**
     * Deprecated pre-Java-9 weak CAS (plain-memory semantics); no spurious failure on one thread →
     * identical to {@link #compareAndSet(boolean, boolean)}.
     */
    @Deprecated
    @BmcModelConforms("differential (ConcurrencyConformanceTest); == compareAndSet() on one thread (no spurious failure)")
    public final boolean weakCompareAndSet(boolean expectedValue, boolean newValue) {
        return compareAndSet(expectedValue, newValue);
    }
}
