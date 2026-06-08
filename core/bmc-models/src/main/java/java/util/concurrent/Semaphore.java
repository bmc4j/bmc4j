package java.util.concurrent;

import org.cprover.CProver;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.Semaphore} — a permit counter. bmc4j proves
 * logic, not interleavings (Lincheck's job), so the permit count is modeled as single-threaded
 * mutable state: {@code release}/{@code tryAcquire} are sequential permit arithmetic.
 *
 * <p><b>Blocking idealization (assume-prune):</b> a real {@code acquire()} blocks when no permit is
 * available, waiting for another thread to {@code release()}. We idealize that the standard BMC way:
 * {@code acquire()} <em>assumes</em> a permit is available (the blocking precondition the thread
 * waits for) and then decrements, pruning the no-permit path. So a critical section guarded by
 * {@code acquire()/release()} stays fully testable — the permit invariant holds along every surviving
 * path — without a deadlocking path quietly passing. A proof that can ONLY block (acquire with no
 * reachable permit) has its single path pruned and is correctly flagged <b>vacuous</b>.
 * {@code tryAcquire()} is unchanged: it soundly returns a boolean (no pruning) for code that probes
 * availability.
 *
 * <p><b>Axis note:</b> the blocking {@code acquire*} forms use the {@link CProver#assume} prune
 * primitive (JBMC-only), so they are exercised on the {@code @BmcProof} axis only, never on the
 * JVM-runnable differential axis. The non-blocking surface ({@code tryAcquire}/{@code release}/
 * {@code availablePermits}/{@code drainPermits}) stays pure Java and is differential-tested.
 */
@BmcModelTail(reason = "fairness (isFair) and the thread-queue introspection (getQueueLength/getQueuedThreads/hasQueuedThreads) are scheduling/interleaving concerns a sequential model can't represent — the concurrency wall; all loud under JBMC")
public class Semaphore {

    private int permits;

    public Semaphore(int permits) {
        this.permits = permits;
    }

    /** Fairness has no meaning in a single-threaded model; the flag is accepted and ignored. */
    public Semaphore(int permits, boolean fair) {
        this.permits = permits;
    }

    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public int availablePermits() {
        return permits;
    }

    /**
     * Take one permit, blocking until one is available. Idealized as assume-prune (see class javadoc):
     * assume a permit is available, then decrement — the no-permit path is pruned. Use
     * {@link #tryAcquire()} to probe instead of block.
     */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public void acquire() throws InterruptedException {
        CProver.assume(permits > 0);
        permits--;
    }

    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public void acquireUninterruptibly() {
        CProver.assume(permits > 0);
        permits--;
    }

    /** Take {@code n} permits at once, uninterruptibly, blocking until available (assume-prune; see {@link #acquire()}). */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public void acquireUninterruptibly(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        CProver.assume(permits >= n);
        permits -= n;
    }

    /** Take {@code n} permits at once, blocking until they are available (assume-prune; see {@link #acquire()}). */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public void acquire(int n) throws InterruptedException {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        CProver.assume(permits >= n);
        permits -= n;
    }

    /** Non-blocking acquire: take a permit and return true, else return false. Fully sound sequentially. */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public boolean tryAcquire() {
        if (permits > 0) {
            permits--;
            return true;
        }
        return false;
    }

    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public boolean tryAcquire(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        if (permits >= n) {
            permits -= n;
            return true;
        }
        return false;
    }

    /**
     * Timed tryAcquire. bmc4j models no time/blocking, so this is the non-blocking probe: take a
     * permit if available, else return false immediately (a real timed acquire on an empty semaphore
     * would block until timeout and then return false — same observable here for the available case,
     * and the sound "false" for the unavailable case).
     */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return tryAcquire();
    }

    /**
     * Timed n-permit tryAcquire. bmc4j models no time/blocking, so this is the non-blocking probe:
     * take {@code n} permits if available, else return false immediately (a real timed acquire on an
     * empty semaphore would block until timeout and then return false — same observable here).
     */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public boolean tryAcquire(int n, long timeout, TimeUnit unit) throws InterruptedException {
        return tryAcquire(n);
    }

    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public void release() {
        permits++;
    }

    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public void release(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        permits += n;
    }

    /**
     * Shrink the permit pool by {@code n} (the protected hook a subclass uses to reduce capacity).
     * Pure sequential permit arithmetic — the count may go negative, exactly like the JDK.
     */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    protected void reducePermits(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        permits -= n;
    }

    /** Acquire all currently available permits and return how many were taken. */
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public int drainPermits() {
        int n = permits;
        permits = 0;
        return n;
    }

    @Override
    @BmcModelConforms("differential (tryAcquire/release/availablePermits/drainPermits) + @BmcProof (acquire assume-prune)")
    public String toString() {
        return super.toString() + "[Permits = " + permits + "]";
    }
}
