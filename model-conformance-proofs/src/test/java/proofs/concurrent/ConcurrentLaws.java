package proofs.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the j.u.c "advanced" sequential models: CountDownLatch,
 * Semaphore, the BlockingQueue impls, and the immediate ExecutorService. JBMC verifies, under its own
 * semantics, the algebraic laws real proofs rely on — over SYMBOLIC inputs in tight ranges. These are
 * SEQUENTIAL-semantics models: they model data/logic, NOT interleavings (that's Lincheck's job).
 *
 * <p>The blocking operations ({@code BlockingQueue.put/take}, {@code Semaphore.acquire},
 * {@code CountDownLatch.await}) are idealized as <b>assume-prune</b>: the operation assumes its
 * blocking precondition (room / element / permit / count==0) and prunes the would-block path. That
 * keeps the <em>logic</em> through these constructs testable, which the {@code *_through_*} proofs
 * below demonstrate: producer/consumer FIFO across put/take, a semaphore-guarded critical section
 * whose occupancy never exceeds the permit count, and a latch that gates a computed result. A proof
 * that can ONLY block (e.g. await with no countDown to zero) prunes to no feasible path and is flagged
 * VACUOUS by the vacuity check — see {@code AwaitVacuityProbe} (kept disabled) for that confirmation.
 */
class ConcurrentLaws {

    // --- Atomic update-family (functional-arg dispatch must devirtualize) -------------------------
    // Each op takes a REAL lambda (IntUnaryOperator / IntBinaryOperator / Long variants). bmc4j
    // desugars the lambda so JBMC devirtualizes applyAsInt/applyAsLong onto the body — if it didn't,
    // the result would be nondet and these symbolic-input laws would fail. getAnd* return the PRIOR
    // value and leave the new one stored; *AndGet return and store the new value. The Map functional-op
    // proofs (HashMapLaws) are the precedent for proving lambda dispatch through a model this way.

    /** getAndUpdate applies the lambda, stores the new value, and returns the OLD one. */
    @BmcProof
    void atomicInteger_getAndUpdate_returns_old_stores_new() {
        int start = Bmc.anyInt(-100, 100);
        AtomicInteger a = new AtomicInteger(start);
        int returned = a.getAndUpdate(v -> v + 7);   // real lambda through the model
        Bmc.check(returned == start);                // returns the prior value
        Bmc.check(a.get() == start + 7);             // new value stored (lambda devirtualized)
    }

    /** updateAndGet applies the lambda and returns the NEW value. */
    @BmcProof
    void atomicInteger_updateAndGet_returns_new() {
        int start = Bmc.anyInt(-100, 100);
        AtomicInteger a = new AtomicInteger(start);
        int returned = a.updateAndGet(v -> v * 3);
        Bmc.check(returned == start * 3 && a.get() == start * 3);
    }

    /** getAndAccumulate folds the arg via the binary lambda, returns the OLD value, stores the new. */
    @BmcProof
    void atomicInteger_getAndAccumulate_returns_old_stores_accumulated() {
        int start = Bmc.anyInt(-100, 100);
        int x = Bmc.anyInt(-100, 100);
        AtomicInteger a = new AtomicInteger(start);
        int returned = a.getAndAccumulate(x, (cur, arg) -> cur + arg);
        Bmc.check(returned == start && a.get() == start + x);
    }

    /** accumulateAndGet folds the arg via the binary lambda and returns the NEW value. */
    @BmcProof
    void atomicInteger_accumulateAndGet_returns_accumulated() {
        int start = Bmc.anyInt(-100, 100);
        int x = Bmc.anyInt(-100, 100);
        AtomicInteger a = new AtomicInteger(start);
        int returned = a.accumulateAndGet(x, (cur, arg) -> cur + arg);
        Bmc.check(returned == start + x && a.get() == start + x);
    }

    /** The AtomicLong update-family devirtualizes its Long lambdas the same way. */
    @BmcProof
    void atomicLong_update_family_devirtualizes() {
        long start = Bmc.anyLong(-100, 100);
        long x = Bmc.anyLong(-100, 100);
        AtomicLong g = new AtomicLong(start);
        Bmc.check(g.getAndUpdate(v -> v + 1) == start && g.get() == start + 1);
        AtomicLong u = new AtomicLong(start);
        Bmc.check(u.updateAndGet(v -> v + 1) == start + 1);
        AtomicLong ga = new AtomicLong(start);
        Bmc.check(ga.getAndAccumulate(x, (c, arg) -> c + arg) == start && ga.get() == start + x);
        AtomicLong aa = new AtomicLong(start);
        Bmc.check(aa.accumulateAndGet(x, (c, arg) -> c + arg) == start + x);
    }

    // --- CountDownLatch ---------------------------------------------------------------------------

    /** Counting down n times from n reaches exactly 0 (and floors there). */
    @BmcProof
    void latch_reaches_zero_after_n_countdowns() {
        int n = Bmc.anyInt(0, 8);
        CountDownLatch latch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            latch.countDown();
        }
        Bmc.check(latch.getCount() == 0);
    }

    /** countDown floors at 0: extra countDowns past 0 never make the count negative. */
    @BmcProof
    void latch_floors_at_zero() {
        int n = Bmc.anyInt(0, 5);
        int extra = Bmc.anyInt(0, 5);
        CountDownLatch latch = new CountDownLatch(n);
        for (int i = 0; i < n + extra; i++) {
            latch.countDown();
        }
        Bmc.check(latch.getCount() == 0);
    }

    /** Before fully counting down, the count is exactly n - k. */
    @BmcProof
    void latch_count_is_n_minus_k() {
        int n = Bmc.anyInt(0, 8);
        int k = Bmc.anyInt(0, 8);
        Bmc.assume(k <= n);
        CountDownLatch latch = new CountDownLatch(n);
        for (int i = 0; i < k; i++) {
            latch.countDown();
        }
        Bmc.check(latch.getCount() == n - k);
    }

    // --- Semaphore --------------------------------------------------------------------------------

    /** Permits are conserved: r releases then r tryAcquires returns to the starting permit count. */
    @BmcProof
    void semaphore_permits_conserved() {
        int init = Bmc.anyInt(0, 6);
        int r = Bmc.anyInt(0, 6);
        Semaphore s = new Semaphore(init);
        for (int i = 0; i < r; i++) {
            s.release();
        }
        for (int i = 0; i < r; i++) {
            boolean got = s.tryAcquire();
            Bmc.check(got); // a permit is always available here (we just released r of them)
        }
        Bmc.check(s.availablePermits() == init);
    }

    /** tryAcquire returns false exactly when no permit is available, and never goes negative. */
    @BmcProof
    void semaphore_tryacquire_when_empty_is_false() {
        Semaphore s = new Semaphore(0);
        Bmc.check(!s.tryAcquire());
        Bmc.check(s.availablePermits() == 0);
    }

    /** Acquiring all initial permits leaves exactly zero. */
    @BmcProof
    void semaphore_acquire_all_leaves_zero() {
        int init = Bmc.anyInt(0, 6);
        Semaphore s = new Semaphore(init);
        for (int i = 0; i < init; i++) {
            Bmc.check(s.tryAcquire());
        }
        Bmc.check(s.availablePermits() == 0);
        Bmc.check(!s.tryAcquire());
    }

    // --- BlockingQueue (FIFO) ---------------------------------------------------------------------

    /** ArrayBlockingQueue is FIFO: offer a,b,c then poll yields a,b,c. */
    @BmcProof
    void arrayqueue_fifo_order() {
        int a = Bmc.anyInt(0, 100);
        int b = Bmc.anyInt(0, 100);
        int c = Bmc.anyInt(0, 100);
        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(4);
        q.offer(a);
        q.offer(b);
        q.offer(c);
        Bmc.check(q.size() == 3);
        Bmc.check(q.peek() == a);
        Bmc.check(q.poll() == a);
        Bmc.check(q.poll() == b);
        Bmc.check(q.poll() == c);
        Bmc.check(q.poll() == null);
        Bmc.check(q.isEmpty());
    }

    /** offer rejects past capacity and remainingCapacity tracks it. */
    @BmcProof
    void arrayqueue_bounded_capacity() {
        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(2);
        Bmc.check(q.remainingCapacity() == 2);
        Bmc.check(q.offer(1));
        Bmc.check(q.offer(2));
        Bmc.check(q.remainingCapacity() == 0);
        Bmc.check(!q.offer(3)); // full
        Bmc.check(q.size() == 2);
    }

    /** LinkedBlockingQueue has the same FIFO law. */
    @BmcProof
    void linkedqueue_fifo_order() {
        int a = Bmc.anyInt(0, 100);
        int b = Bmc.anyInt(0, 100);
        LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>(4);
        q.offer(a);
        q.offer(b);
        Bmc.check(q.poll() == a);
        Bmc.check(q.poll() == b);
        Bmc.check(q.poll() == null);
    }

    /**
     * The DEFAULT LinkedBlockingQueue is unbounded, exactly like the JDK: within the model's
     * storage bound, {@code offer} can never return false and {@code remainingCapacity} counts
     * down from {@code Integer.MAX_VALUE} — never from the model bound. The model used to default
     * its LOGICAL capacity to the 64-slot storage bound, which admitted rejection behaviors the
     * real default queue cannot produce: a proof over an offer-returns-false backpressure branch
     * could VERIFY against a branch that is unreachable in production (a silent false green).
     * This law regression-pins the fix on the engine axis.
     */
    @BmcProof
    void default_linkedqueue_is_unbounded_offer_never_rejects() {
        LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>();
        int n = Bmc.anyInt(0, 4);
        for (int i = 0; i < n; i++) {
            Bmc.check(q.offer(i));                                    // unbounded: always accepted
        }
        Bmc.check(q.remainingCapacity() == Integer.MAX_VALUE - n);    // logical capacity, not 64
        Bmc.check(q.size() == n);
    }

    // --- Immediate ExecutorService ----------------------------------------------------------------

    /** submit(Callable) runs synchronously; the future is done and get() returns the computed value. */
    @BmcProof
    void executor_submit_callable_runs_synchronously() throws Exception {
        int x = Bmc.anyInt(0, 1000);
        ExecutorService ex = Executors.newFixedThreadPool(3);
        Future<Integer> f = ex.submit(() -> x * 2);
        Bmc.check(f.isDone());
        Bmc.check(f.get() == x * 2);
    }

    /** submit(Runnable, result) runs the task and the future yields the supplied result. */
    @BmcProof
    void executor_submit_runnable_with_result() throws Exception {
        int x = Bmc.anyInt(0, 1000);
        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<Integer> f = ex.submit(() -> { }, x);
        Bmc.check(f.get() == x);
    }

    // --- LOGIC THROUGH THE BLOCKING CONSTRUCTS ----------------------------------------------------
    // These exercise the assume-prune blocking ops (put/take/acquire/await) and prove the logic that
    // flows THROUGH them still verifies — the whole point of the assume-prune idealization.

    /**
     * Producer/consumer through the blocking API: put n symbolic values, then take n. They come out in
     * FIFO order and their running sum is preserved — proven over the blocking put()/take() (each
     * assumes room / non-empty and proceeds; no would-block path survives to break the logic).
     */
    @BmcProof
    void blockingqueue_producer_consumer_fifo_through_put_take() throws Exception {
        int a = Bmc.anyInt(0, 50);
        int b = Bmc.anyInt(0, 50);
        int c = Bmc.anyInt(0, 50);
        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(8);
        q.put(a);
        q.put(b);
        q.put(c);
        // FIFO out, via the blocking take():
        int x = q.take();
        int y = q.take();
        int z = q.take();
        Bmc.check(x == a);
        Bmc.check(y == b);
        Bmc.check(z == c);
        Bmc.check(x + y + z == a + b + c); // sum invariant preserved across the handoff
        Bmc.check(q.isEmpty());
    }

    /** The same producer/consumer logic through a LinkedBlockingQueue's blocking put()/take(). */
    @BmcProof
    void linkedqueue_producer_consumer_through_put_take() throws Exception {
        int a = Bmc.anyInt(0, 50);
        int b = Bmc.anyInt(0, 50);
        LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>(8);
        q.put(a);
        q.put(b);
        Bmc.check(q.take() == a);
        Bmc.check(q.take() == b);
        Bmc.check(q.isEmpty());
    }

    /**
     * A Semaphore(k)-guarded critical section: blocking acquire() before entering, release() on exit.
     * Over symbolic enter/exit ops the live occupancy never exceeds k and never goes negative — the
     * permit invariant a real mutex/pool relies on. acquire() is assume-prune (assume a permit, then
     * take it), so only feasible interleavings of this single thread's ops survive.
     */
    @BmcProof
    void semaphore_guarded_section_never_exceeds_k() throws Exception {
        int k = Bmc.anyInt(1, 4);
        Semaphore s = new Semaphore(k);
        int inside = 0;
        // A bounded sequence of enter/leave decisions; acquire blocks (prunes) when full.
        for (int i = 0; i < 6; i++) {
            boolean enter = Bmc.anyBoolean();
            if (enter) {
                s.acquire();          // assume a permit is available, then take it
                inside++;
                Bmc.check(inside <= k);   // occupancy never exceeds the permit count
            } else if (inside > 0) {
                inside--;
                s.release();
            }
            Bmc.check(inside >= 0);
            Bmc.check(s.availablePermits() + inside == k); // permits conserved
        }
    }

    /**
     * A CountDownLatch coordinating a computed result: n countDowns drive it to zero, await() gates,
     * then the result computed after the gate is correct. await() is assume-prune (assume count==0,
     * then proceed) — here the count genuinely reaches 0, so the gate is satisfiable and the logic
     * after it is proven.
     */
    @BmcProof
    void latch_gates_a_computed_result() throws Exception {
        int n = Bmc.anyInt(1, 6);
        int seed = Bmc.anyInt(0, 100);
        CountDownLatch ready = new CountDownLatch(n);
        int acc = seed;
        for (int i = 0; i < n; i++) {
            acc += 1;          // "worker" progress
            ready.countDown();
        }
        ready.await();          // gate: assume the latch reached 0 (it did), then proceed
        Bmc.check(ready.getCount() == 0);
        Bmc.check(acc == seed + n); // result computed after the gate is correct
    }
}
