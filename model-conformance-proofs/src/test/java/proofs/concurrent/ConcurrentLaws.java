package proofs.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

    // --- Atomic VarHandle memory-ordering variants (sequential == plain/strong counterpart) -------
    // On ONE thread there is no other thread to observe a relaxed ordering, so every fence variant is
    // observably identical to its plain/strong counterpart. These laws pin that equivalence under JBMC.

    /** compareAndExchange returns the WITNESSED value and stores newValue only on a match. */
    @BmcProof
    void atomicInteger_compareAndExchange_returns_witnessed() {
        int start = Bmc.anyInt(-50, 50);
        int expected = Bmc.anyInt(-50, 50);
        int next = Bmc.anyInt(-50, 50);
        AtomicInteger a = new AtomicInteger(start);
        int witnessed = a.compareAndExchange(expected, next);
        Bmc.check(witnessed == start);                       // always the prior value
        Bmc.check(a.get() == (start == expected ? next : start)); // stored iff it matched
    }

    /** The acquire/release compareAndExchange variants are identical to the plain one on one thread. */
    @BmcProof
    void atomicInteger_compareAndExchange_variants_agree() {
        int start = Bmc.anyInt(-50, 50);
        int next = Bmc.anyInt(-50, 50);
        // a matching exchange: both variants witness `start` and store `next`.
        AtomicInteger acq = new AtomicInteger(start);
        AtomicInteger rel = new AtomicInteger(start);
        Bmc.check(acq.compareAndExchangeAcquire(start, next) == start && acq.get() == next);
        Bmc.check(rel.compareAndExchangeRelease(start, next) == start && rel.get() == next);
    }

    /** Every weakCompareAndSet* variant never spuriously fails on one thread: it == compareAndSet. */
    @BmcProof
    void atomicInteger_weak_cas_never_spuriously_fails() {
        int start = Bmc.anyInt(-50, 50);
        int next = Bmc.anyInt(-50, 50);
        // expected matches -> all variants succeed and store next (no spurious failure on one thread).
        Bmc.check(new AtomicInteger(start).weakCompareAndSetPlain(start, next));
        Bmc.check(new AtomicInteger(start).weakCompareAndSetAcquire(start, next));
        Bmc.check(new AtomicInteger(start).weakCompareAndSetRelease(start, next));
        AtomicInteger v = new AtomicInteger(start);
        Bmc.check(v.weakCompareAndSetVolatile(start, next) && v.get() == next);
    }

    /** The fenced reads/writes are plain reads/writes on one thread. */
    @BmcProof
    void atomicInteger_fenced_accessors_are_plain() {
        int x = Bmc.anyInt(-50, 50);
        AtomicInteger a = new AtomicInteger();
        a.setRelease(x);
        Bmc.check(a.getAcquire() == x && a.getPlain() == x && a.getOpaque() == x && a.get() == x);
        a.setOpaque(x + 1);
        Bmc.check(a.get() == x + 1);
    }

    /** AtomicLong's memory-ordering variants collapse the same way. */
    @BmcProof
    void atomicLong_memory_ordering_variants_agree() {
        long start = Bmc.anyLong(-50, 50);
        long next = Bmc.anyLong(-50, 50);
        AtomicLong a = new AtomicLong(start);
        Bmc.check(a.compareAndExchange(start, next) == start && a.get() == next);
        AtomicLong w = new AtomicLong(start);
        Bmc.check(w.weakCompareAndSetVolatile(start, next) && w.get() == next);
        AtomicLong f = new AtomicLong();
        f.setRelease(next);
        Bmc.check(f.getAcquire() == next && f.getPlain() == next);
    }

    // --- CompletableFuture (sequential ready-value / ready-failure) -------------------------------
    // A future is "a value that is ready" or "a failure that is ready". These laws pin the trust-
    // critical sequential semantics: the right combinator runs on a normal completion, and on an
    // exceptional completion the dependent actions SHORT-CIRCUIT (propagate) while exceptionally/handle
    // RECOVER. Lambdas devirtualize through the model exactly like the atomic update-family above.

    /** A completed future carries its value through thenApply (lambda devirtualized) to join. */
    @BmcProof
    void completablefuture_thenApply_runs_on_value() {
        int v = Bmc.anyInt(-100, 100);
        CompletableFuture<Integer> f = CompletableFuture.completedFuture(v);
        int r = f.thenApply(x -> x + 7).join();
        Bmc.check(r == v + 7);
        Bmc.check(f.isDone() && !f.isCompletedExceptionally());
    }

    /** thenCompose flattens a future-returning lambda; the composed value reaches join. */
    @BmcProof
    void completablefuture_thenCompose_flattens() {
        int v = Bmc.anyInt(-100, 100);
        CompletableFuture<Integer> f = CompletableFuture.completedFuture(v);
        int r = f.thenCompose(x -> CompletableFuture.completedFuture(x * 2)).join();
        Bmc.check(r == v * 2);
    }

    /**
     * thenCombine merges two ready futures through its BiFunction. The model now implements the REAL
     * {@code thenCombine(CompletionStage, BiFunction)} signature (the future {@code implements
     * CompletionStage}), so a source-level {@code fa.thenCombine(fb, …)} binds to the modeled overload —
     * it is no longer stranded in the loud tail, and the law goes through.
     */
    @BmcProof
    void completablefuture_thenCombine_merges_two_stages() {
        int a = Bmc.anyInt(-50, 50);
        int b = Bmc.anyInt(-50, 50);
        CompletableFuture<Integer> fa = CompletableFuture.completedFuture(a);
        CompletableFuture<Integer> fb = CompletableFuture.completedFuture(b);
        int r = fa.thenCombine(fb, (x, y) -> x + y).join();
        Bmc.check(r == a + b);
    }

    /**
     * The no-arg {@code *Async} twin reduces to its synchronous combinator under the immediate executor
     * (a sequential model has no real executor). thenApplyAsync therefore carries the value through
     * exactly like thenApply — so {@code CompletionStage}-shaped code that reaches for the {@code *Async}
     * builder still verifies.
     */
    @BmcProof
    void completablefuture_thenApplyAsync_reduces_to_sync() {
        int v = Bmc.anyInt(-100, 100);
        int r = CompletableFuture.completedFuture(v).thenApplyAsync(x -> x + 7).join();
        Bmc.check(r == v + 7);
    }

    /**
     * A method TYPED as {@link java.util.concurrent.CompletionStage} devirtualizes to the
     * {@link CompletableFuture} backing: the stage combinators dispatch to the single model
     * implementation, and {@code toCompletableFuture().join()} realizes the value. This is the enabling
     * property — code written against the interface (not the concrete future) now verifies.
     */
    @BmcProof
    void completionStage_typed_usage_devirtualizes() {
        int v = Bmc.anyInt(-100, 100);
        java.util.concurrent.CompletionStage<Integer> stage = CompletableFuture.completedFuture(v);
        int r = stage.thenApply(x -> x + 1).thenCompose(x -> CompletableFuture.completedFuture(x * 2))
                .toCompletableFuture().join();
        Bmc.check(r == (v + 1) * 2);
    }

    /** supplyAsync runs the supplier eagerly (single-threaded) and the value reaches get(). */
    @BmcProof
    void completablefuture_supplyAsync_runs_eagerly() throws Exception {
        int v = Bmc.anyInt(-100, 100);
        CompletableFuture<Integer> f = CompletableFuture.supplyAsync(() -> v + 1);
        Bmc.check(f.isDone());
        Bmc.check(f.get() == v + 1);
    }

    /**
     * EXCEPTION FLOW — propagation: a future completed exceptionally short-circuits thenApply (the
     * dependent action must NOT run) and the RESULT future is itself exceptional. We assert this on the
     * model's own decidable state ({@code isCompletedExceptionally} on the chained result), NOT by
     * catching the join() throw and inspecting the real-JDK CompletionException internals — those
     * wrapper classes are unmodeled real types whose getCause() JBMC cannot reason about (it can on a
     * real JVM, which the differential test checks). This is the core short-circuit property a proof
     * relies on: the failure flows past the combinator untouched.
     */
    @BmcProof
    void completablefuture_exceptional_thenApply_short_circuits() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException());
        Bmc.check(f.isCompletedExceptionally());

        // The dependent action records whether it ran; on an exceptional source it must NOT.
        AtomicInteger ran = new AtomicInteger(0);
        CompletableFuture<Integer> g = f.thenApply(x -> {
            ran.incrementAndGet();
            return x + 1;
        });
        Bmc.check(ran.get() == 0);            // short-circuited: the action never ran
        Bmc.check(g.isCompletedExceptionally()); // and the failure propagated to the result
    }

    /**
     * EXCEPTION FLOW — recovery via exceptionally: a failed future is recovered to a normal value; the
     * recovered value reaches join with no exception, and the recovered future is no longer
     * exceptional. The companion to short-circuit: this is the "catch" of the future world.
     */
    @BmcProof
    void completablefuture_exceptionally_recovers() {
        int fallback = Bmc.anyInt(-100, 100);
        CompletableFuture<Integer> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException());
        CompletableFuture<Integer> recovered = f.exceptionally(cause -> fallback);
        Bmc.check(!recovered.isCompletedExceptionally());
        Bmc.check(recovered.join() == fallback); // recovered: no throw
    }

    /** exceptionally on a NORMAL future passes the value through unchanged (the fn is not invoked). */
    @BmcProof
    void completablefuture_exceptionally_passthrough_on_normal() {
        int v = Bmc.anyInt(-100, 100);
        CompletableFuture<Integer> f = CompletableFuture.completedFuture(v);
        int r = f.exceptionally(cause -> -1).join();
        Bmc.check(r == v);
    }

    /**
     * EXCEPTION FLOW — recovery via handle: handle sees (null, cause) on failure and (value, null) on
     * success, and its return becomes the result either way. Proves both arms of the BiFunction.
     */
    @BmcProof
    void completablefuture_handle_recovers_and_passes() {
        int v = Bmc.anyInt(0, 100);

        // failure arm: value is null, cause is present -> recover to a sentinel.
        CompletableFuture<Integer> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException());
        int rf = failed.handle((value, cause) -> cause != null ? -1 : value).join();
        Bmc.check(rf == -1);

        // success arm: value present, cause null -> transform the value.
        CompletableFuture<Integer> ok = CompletableFuture.completedFuture(v);
        int ro = ok.handle((value, cause) -> cause != null ? -1 : value + 1).join();
        Bmc.check(ro == v + 1);
    }

    /** whenComplete observes but does NOT recover: the result future stays exceptional. */
    @BmcProof
    void completablefuture_whenComplete_does_not_recover() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException());
        AtomicInteger observed = new AtomicInteger(0);
        CompletableFuture<Integer> g = f.whenComplete((value, cause) -> observed.incrementAndGet());
        Bmc.check(observed.get() == 1);          // the observer DID run (unlike thenApply)
        Bmc.check(g.isCompletedExceptionally()); // but the failure is NOT recovered
    }

    /** allOf over all-normal futures completes normally; a single failure makes the result exceptional. */
    @BmcProof
    void completablefuture_allOf_propagates_a_failure() {
        int a = Bmc.anyInt(-50, 50);
        CompletableFuture<Integer> ok = CompletableFuture.completedFuture(a);
        CompletableFuture<Integer> bad = new CompletableFuture<>();
        bad.completeExceptionally(new RuntimeException());

        Bmc.check(!CompletableFuture.allOf(ok).isCompletedExceptionally());     // all normal
        Bmc.check(CompletableFuture.allOf(ok, bad).isCompletedExceptionally()); // one failed: propagates
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

    /**
     * The submitted task actually RUNS (its side effect is observed), not just that get() returns a
     * value. Pins the synchronous-execute contract on the ThreadFactory pool overload too: the factory
     * is never invoked, the task runs inline on submit. execute() is the bare Executor surface.
     */
    @BmcProof
    void executor_execute_runs_task_synchronously() {
        int x = Bmc.anyInt(-100, 100);
        // A real ThreadFactory lambda: the immediate model never invokes it (no worker is spawned),
        // so its body is dead at analysis time — it just exercises the ThreadFactory pool overload.
        ExecutorService ex = Executors.newFixedThreadPool(4, r -> null);
        AtomicInteger sink = new AtomicInteger(0);
        ex.execute(() -> sink.set(x + 1));
        Bmc.check(sink.get() == x + 1);          // ran inline, synchronously
        ex.shutdown();
        Bmc.check(ex.isShutdown());              // lifecycle state flips
    }

    /**
     * invokeAll runs every submitted task synchronously and returns a completed Future per task, in
     * order. Proves the collection-driving path through the interface-typed reference.
     */
    @BmcProof
    void executor_invokeAll_runs_all_in_order() throws Exception {
        int a = Bmc.anyInt(0, 100);
        int b = Bmc.anyInt(0, 100);
        ExecutorService ex = Executors.newCachedThreadPool();
        java.util.List<Callable<Integer>> tasks = new java.util.ArrayList<>();
        tasks.add(() -> a + 1);
        tasks.add(() -> b + 2);
        java.util.List<Future<Integer>> fs = ex.invokeAll(tasks);
        Bmc.check(fs.get(0).get() == a + 1);
        Bmc.check(fs.get(1).get() == b + 2);
    }

    // --- Immediate ScheduledExecutorService (sequential, delay ignored) ---------------------------
    // A one-shot schedule runs its task synchronously at submit time; the delay/unit are ignored
    // (timing is not modeled). The model class IMPLEMENTS ScheduledExecutorService (a JDK interface),
    // so dispatching schedule/submit through the interface-typed reference devirtualizes — these
    // proofs exercise exactly that interface-typed dispatch (the devirt-safe path).

    /** schedule(Callable, delay) runs the callable synchronously; the future is done with its value. */
    @BmcProof
    void scheduled_schedule_callable_runs_synchronously() throws Exception {
        int x = Bmc.anyInt(0, 1000);
        ScheduledExecutorService ex = Executors.newScheduledThreadPool(2);
        Future<Integer> f = ex.schedule((Callable<Integer>) () -> x * 2, 5, TimeUnit.SECONDS);
        Bmc.check(f.isDone());
        Bmc.check(f.get() == x * 2);             // delay ignored, value computed
    }

    /** schedule(Runnable, delay) runs the runnable synchronously (side effect observed). */
    @BmcProof
    void scheduled_schedule_runnable_runs_synchronously() {
        int x = Bmc.anyInt(-100, 100);
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger sink = new AtomicInteger(0);
        ex.schedule(() -> sink.set(x - 3), 100, TimeUnit.MILLISECONDS);
        Bmc.check(sink.get() == x - 3);
    }

    /**
     * A ScheduledExecutorService IS an ExecutorService: submit() through the inherited interface
     * surface runs synchronously too (the impl extends the immediate executor model). Pins that the
     * inherited submit path works through the ScheduledExecutorService-typed reference.
     */
    @BmcProof
    void scheduled_inherits_synchronous_submit() throws Exception {
        int x = Bmc.anyInt(0, 1000);
        ScheduledExecutorService ex = Executors.newScheduledThreadPool(1);
        Future<Integer> f = ex.submit((Callable<Integer>) () -> x + 9);
        Bmc.check(f.get() == x + 9);
    }

    // --- Executors.callable adapter (pure same-thread logic) --------------------------------------

    /** callable(Runnable, result) yields a Callable that runs the task and returns the supplied result. */
    @BmcProof
    void executors_callable_runs_runnable_and_returns_result() throws Exception {
        int x = Bmc.anyInt(-100, 100);
        AtomicInteger sink = new AtomicInteger(0);
        Callable<Integer> c = Executors.callable(() -> sink.set(x + 5), x);
        int r = c.call();                        // running the adapter runs the wrapped Runnable
        Bmc.check(sink.get() == x + 5);          // side effect happened
        Bmc.check(r == x);                       // and the supplied result is returned
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
