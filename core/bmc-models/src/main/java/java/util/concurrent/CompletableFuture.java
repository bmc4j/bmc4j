package java.util.concurrent;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Sequential BMC model of {@link java.util.concurrent.CompletableFuture} — a future is just "a value
 * that is ready" (or "a failure that is ready"). `*Async` builders run their task eagerly
 * (single-threaded), and `get`/`join` return the value or surface the failure. Lets logic proofs go
 * through code structured around futures; bmc4j does not model actual asynchrony/scheduling
 * (concurrency is Lincheck's job).
 *
 * <p><b>Two completion states are modeled</b> — normal (a ready {@code value}) and exceptional (a ready
 * {@code ex} cause) — because the trust-critical question for sequential logic is the same as the JDK's:
 * does the right combinator run, and is the exception propagated or recovered correctly? So the
 * dependent-action surface ({@code thenApply}/{@code thenAccept}/{@code thenRun}/{@code thenCompose}/
 * {@code thenCombine}) <b>short-circuits</b> on an exceptional source (propagates the cause, like the
 * JDK), and the recovery surface ({@code exceptionally}/{@code handle}) and observation surface
 * ({@code whenComplete}) are modeled so exception-flow proofs (recover a failed future) go through.
 *
 * <p><b>{@link CompletionStage} interface surface (devirtualization):</b> the model {@code implements
 * CompletionStage<T>}, mirroring the real type hierarchy, so code TYPED as {@code CompletionStage<T>}
 * dispatches to this single backing implementation under JBMC. The stage combinators that work under
 * immediate semantics ({@code thenApply}/{@code thenAccept}/{@code thenRun}/{@code thenCompose}/
 * {@code thenCombine}/{@code handle}/{@code whenComplete}/{@code exceptionally}) take the real interface
 * signatures — notably {@code thenCompose}/{@code thenCombine} take a {@link CompletionStage} (read via
 * {@link #toCompletableFuture()}), so a source-level {@code fa.thenCombine(fb, …)} now binds to the
 * modeled overload instead of the loud tail. {@link #toCompletableFuture()} returns the backing
 * ({@code this}).
 *
 * <p><b>{@code *Async} stance:</b> the no-arg static <em>builders</em>
 * ({@code supplyAsync(Supplier)}/{@code runAsync(Runnable)}) and the no-arg instance {@code *Async}
 * chaining/recovery twins ({@code thenApplyAsync}/{@code thenAcceptAsync}/{@code thenRunAsync}/
 * {@code thenComposeAsync}/{@code thenCombineAsync}/{@code handleAsync}/{@code whenCompleteAsync}/
 * {@code exceptionallyAsync}) are modeled as their synchronous equivalents — a sequential model has no
 * executor, so the immediate-executor {@code *Async} twin is observably identical to the plain
 * combinator on one thread.
 *
 * <p><b>The concurrency wall (per-member loud waivers).</b> Three groups cannot be modeled and are each
 * a loud-if-reached {@link BmcUnmodelable}: every overload taking an explicit {@link Executor} (the
 * {@code *Async(..., Executor)} twins, {@code supplyAsync/runAsync(..., Executor)}, {@code completeAsync},
 * {@code defaultExecutor}, {@code delayedExecutor}) — a non-immediate executor's true concurrency is the
 * wall; the real wall-clock timeouts ({@code orTimeout}/{@code completeOnTimeout}/{@code get(timeout)});
 * and the cancellation/obtrusion/racing-introspection surface ({@code cancel}/{@code isCancelled}/
 * {@code obtrude*}/{@code exceptionNow}/{@code resultNow}/{@code state}/{@code getNumberOfDependents})
 * which needs genuine happens-before. The stage/copy plumbing that DOES have a sequential meaning
 * ({@code failedFuture}/{@code failedStage}/{@code newIncompleteFuture}/{@code copy}/
 * {@code minimalCompletionStage}) is modeled.
 */
public class CompletableFuture<T> implements CompletionStage<T> {

    private T value;
    private Throwable ex;
    private boolean done;

    public CompletableFuture() {
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public static <U> CompletableFuture<U> completedFuture(U value) {
        CompletableFuture<U> f = new CompletableFuture<>();
        f.value = value;
        f.done = true;
        return f;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return completedFuture(supplier.get());
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        runnable.run();
        return completedFuture(null);
    }

    /**
     * Completes ALL of the given futures' join into one: in a sequential model every argument is
     * already completed, so the result is a ready {@code Void} unless one completed exceptionally, in
     * which case the FIRST such failure becomes the result's cause (the JDK reports one of the
     * exceptional completions; first-in-array is the deterministic sequential choice).
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        for (CompletableFuture<?> cf : cfs) {
            if (cf.ex != null) {
                return failed(cf.ex);
            }
        }
        return completedFuture(null);
    }

    /**
     * Completes when ANY of the given futures completes: in a sequential model all are already
     * completed, so this yields the FIRST argument's completion (its value, or its exception). The JDK
     * reports the first to complete; first-in-array is the deterministic sequential choice. An empty
     * argument array yields a never-completing future in the JDK — out of scope here (loud if reached
     * via get/join on an empty result, since done stays false).
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        CompletableFuture<Object> r = new CompletableFuture<>();
        if (cfs.length == 0) {
            return r; // JDK: never completes
        }
        CompletableFuture<?> first = cfs[0];
        r.value = first.value;
        r.ex = first.ex;
        r.done = first.done;
        return r;
    }

    /** Internal: a future already completed exceptionally with the given cause. */
    private static <U> CompletableFuture<U> failed(Throwable cause) {
        CompletableFuture<U> f = new CompletableFuture<>();
        f.ex = cause;
        f.done = true;
        return f;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public boolean complete(T v) {
        if (done) {
            return false;
        }
        value = v;
        done = true;
        return true;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public boolean completeExceptionally(Throwable t) {
        if (done) {
            return false;
        }
        ex = t;
        done = true;
        return true;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public boolean isDone() {
        return done;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public boolean isCompletedExceptionally() {
        return done && ex != null;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public T get() throws InterruptedException, ExecutionException {
        if (ex != null) {
            throw new ExecutionException(ex);
        }
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public T join() {
        if (ex != null) {
            throw wrapForJoin(ex);
        }
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public T getNow(T valueIfAbsent) {
        if (ex != null) {
            throw wrapForJoin(ex);
        }
        return done ? value : valueIfAbsent;
    }

    // join/getNow surface the cause wrapped in a CompletionException, EXCEPT a cause that already IS a
    // CompletionException is rethrown as-is — matching the JDK's reportJoin.
    private static CompletionException wrapForJoin(Throwable cause) {
        if (cause instanceof CompletionException) {
            return (CompletionException) cause;
        }
        return new CompletionException(cause);
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        if (ex != null) {
            return failed(ex);
        }
        return completedFuture(fn.apply(value));
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApply(fn);
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        if (ex != null) {
            return failed(ex);
        }
        action.accept(value);
        return completedFuture(null);
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAccept(action);
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<Void> thenRun(Runnable action) {
        if (ex != null) {
            return failed(ex);
        }
        action.run();
        return completedFuture(null);
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return thenRun(action);
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        if (ex != null) {
            return failed(ex);
        }
        return (CompletableFuture<U>) fn.apply(value).toCompletableFuture();
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn);
    }

    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U, V> CompletableFuture<V> thenCombine(
            CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        if (ex != null) {
            return failed(ex);
        }
        CompletableFuture<? extends U> o = other.toCompletableFuture();
        if (o.ex != null) {
            return failed(o.ex);
        }
        return completedFuture(fn.apply(value, o.value));
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public <U, V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn);
    }

    /**
     * Recovers an exceptional completion: if this future failed, returns a future completed with
     * {@code fn.apply(cause)}; otherwise passes the value through unchanged. The JDK passes the RAW
     * cause to {@code fn} (not wrapped in CompletionException), which this matches.
     */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        if (ex != null) {
            return completedFuture(fn.apply(ex));
        }
        return completedFuture(value);
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return exceptionally(fn);
    }

    /**
     * Handles both outcomes: runs {@code fn.apply(value, cause)} (exactly one of the two is non-null —
     * value on normal completion, cause on exceptional) and completes the result with whatever {@code fn}
     * returns. This RECOVERS a failed future. The JDK passes the RAW cause to {@code fn}, which matches.
     */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return completedFuture(fn.apply(ex != null ? null : value, ex));
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn);
    }

    /**
     * Observes the completion without changing it: runs {@code action.accept(value, cause)} (one of the
     * two is non-null) and returns a future with the SAME completion — value passes through, exception
     * propagates (NOT recovered). The JDK passes the RAW cause to {@code action}, which matches.
     */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        action.accept(ex != null ? null : value, ex);
        if (ex != null) {
            return failed(ex);
        }
        return completedFuture(value);
    }

    @Override
    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin (sequential model has no executor) — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action);
    }

    // --- either/both combinators (sequential: both sources are ready) ------------------------------
    // The JDK either-combinators complete when the FIRST source completes; in a sequential model both
    // are already completed, so the deterministic sequential choice is THIS future's completion (the
    // receiver), with the other's failure NOT consulted (it never "won the race"). The both-combinators
    // need both completions: they short-circuit on either exceptional source (propagate the cause), then
    // run on the two ready values — exactly the thenCombine pattern. Lambdas devirtualize through the
    // model like the other combinators. The *Async no-arg twins reduce to their synchronous form (no
    // executor in a sequential model). The explicit-Executor twins stay in the loud tail.

    /** Either-combinator: applies {@code fn} to the receiver's ready value (the deterministic sequential winner). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest either/both combinators)")
    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        if (ex != null) {
            return failed(ex);
        }
        return completedFuture(fn.apply(value));
    }

    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin — differential (ConcurrencyConformanceTest)")
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEither(other, fn);
    }

    /** Either-combinator: consumes the receiver's ready value (the deterministic sequential winner). */
    @BmcModelConforms("differential (ConcurrencyConformanceTest either/both combinators)")
    public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        if (ex != null) {
            return failed(ex);
        }
        action.accept(value);
        return completedFuture(null);
    }

    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEither(other, action);
    }

    /** Either-combinator: runs {@code action} once the receiver (the deterministic sequential winner) is ready. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest either/both combinators)")
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        if (ex != null) {
            return failed(ex);
        }
        action.run();
        return completedFuture(null);
    }

    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return runAfterEither(other, action);
    }

    /** Both-combinator: consumes both ready values; short-circuits on either exceptional source. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest either/both combinators)")
    public <U> CompletableFuture<Void> thenAcceptBoth(
            CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        if (ex != null) {
            return failed(ex);
        }
        CompletableFuture<? extends U> o = other.toCompletableFuture();
        if (o.ex != null) {
            return failed(o.ex);
        }
        action.accept(value, o.value);
        return completedFuture(null);
    }

    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin — differential (ConcurrencyConformanceTest)")
    public <U> CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action);
    }

    /** Both-combinator: runs {@code action} once both are ready; short-circuits on either exceptional source. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest either/both combinators)")
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        if (ex != null) {
            return failed(ex);
        }
        CompletableFuture<?> o = other.toCompletableFuture();
        if (o.ex != null) {
            return failed(o.ex);
        }
        action.run();
        return completedFuture(null);
    }

    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return runAfterBoth(other, action);
    }

    /**
     * Recovers an exceptional completion by flattening a stage-returning function (the future analogue
     * of {@code thenCompose} on the failure path): if this future failed, returns the future {@code fn}
     * produces from the raw cause; otherwise passes the value through unchanged. The JDK passes the RAW
     * cause to {@code fn}, which this matches.
     */
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ConcurrencyConformanceTest exceptionallyCompose)")
    public CompletableFuture<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        if (ex != null) {
            return (CompletableFuture<T>) fn.apply(ex).toCompletableFuture();
        }
        return completedFuture(value);
    }

    @BmcModelConforms("immediate executor: *Async reduces to its synchronous twin — differential (ConcurrencyConformanceTest)")
    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return exceptionallyCompose(fn);
    }

    /** A completed stage carrying {@code value}, like {@code CompletableFuture.completedStage} — a ready stage. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a ready stage is a completed future")
    public static <U> CompletionStage<U> completedStage(U value) {
        return completedFuture(value);
    }

    /** Returns the backing future ({@code this}) — a {@link CompletableFuture} already IS its own stage. */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a CompletableFuture is its own backing stage")
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // --- ready-value / ready-failure constructions (sequential, no executor/scheduling) -----------
    // These build or mirror a completion with no asynchrony, timing, or executor involved, so they are
    // observably exact on one thread.

    /** A future already completed exceptionally with {@code ex} — the ready-failure constructor. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a ready failure is a completed-exceptionally future")
    public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        return failed(ex);
    }

    /** A stage already completed exceptionally with {@code ex} — the ready-failure stage. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a ready-failure stage is a completed-exceptionally future")
    public static <U> CompletionStage<U> failedStage(Throwable ex) {
        return failed(ex);
    }

    /** A fresh, not-yet-completed future — the factory dependents use; on one thread it is just {@code new}. */
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a fresh incomplete future")
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CompletableFuture<>();
    }

    /**
     * A snapshot copy carrying the SAME completion (value or failure). On one thread the source is already
     * settled, so the copy is a fresh future with the identical completion — observably exact.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest): copy mirrors the settled completion")
    public CompletableFuture<T> copy() {
        if (ex != null) {
            return failed(ex);
        }
        if (done) {
            return completedFuture(value);
        }
        return new CompletableFuture<>();
    }

    /**
     * A {@link CompletionStage} view that restricts the API to the stage combinators. Backed by this same
     * settled future (the combinators dispatch to the single model implementation), so on one thread it
     * is observably the same completion reachable through {@link #toCompletableFuture()}.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a minimal stage is backed by this settled future")
    public CompletionStage<T> minimalCompletionStage() {
        return copy();
    }

    // --- the concurrency wall: executors, real timeouts, cancellation / obtrusion (loud stubs) -----
    // Every member below needs something a sequential, single-threaded ready-value model cannot supply:
    // a real Executor's true concurrency, a wall-clock timeout, or the happens-before of cancellation /
    // obtrusion / async introspection. Each is a loud-if-reached waiver so a proof that touches it FAILS
    // named, rather than proceeding on a fiction. The no-arg *Async twins (which reduce to their
    // synchronous combinator under the immediate executor) ARE modeled above; only the explicit-Executor
    // overloads are walled here.

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.runAsync(java.lang.Runnable, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use runAsync(Runnable)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.supplyAsync(java.util.function.Supplier, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use supplyAsync(Supplier)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.completeAsync(java.util.function.Supplier) — async completion on an executor is the concurrency wall; use complete(value)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.completeAsync(java.util.function.Supplier, java.util.concurrent.Executor) — async completion on an executor is the concurrency wall; use complete(value)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.thenApplyAsync(java.util.function.Function, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use thenApply/thenApplyAsync(Function)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.thenAcceptAsync(java.util.function.Consumer, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use thenAccept/thenAcceptAsync(Consumer)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.thenRunAsync(java.lang.Runnable, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use thenRun/thenRunAsync(Runnable)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.thenComposeAsync(java.util.function.Function, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use thenCompose/thenComposeAsync(Function)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public <U, V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.thenCombineAsync(java.util.concurrent.CompletionStage, java.util.function.BiFunction, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use thenCombine/thenCombineAsync(CompletionStage, BiFunction)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public <U> CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.thenAcceptBothAsync(java.util.concurrent.CompletionStage, java.util.function.BiConsumer, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use thenAcceptBoth(CompletionStage, BiConsumer)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.runAfterBothAsync(java.util.concurrent.CompletionStage, java.lang.Runnable, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use runAfterBoth(CompletionStage, Runnable)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public <U> CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.applyToEitherAsync(java.util.concurrent.CompletionStage, java.util.function.Function, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use applyToEither(CompletionStage, Function)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.acceptEitherAsync(java.util.concurrent.CompletionStage, java.util.function.Consumer, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use acceptEither(CompletionStage, Consumer)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.runAfterEitherAsync(java.util.concurrent.CompletionStage, java.lang.Runnable, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use runAfterEither(CompletionStage, Runnable)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.handleAsync(java.util.function.BiFunction, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use handle/handleAsync(BiFunction)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.whenCompleteAsync(java.util.function.BiConsumer, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use whenComplete/whenCompleteAsync(BiConsumer)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.exceptionallyAsync(java.util.function.Function, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use exceptionally/exceptionallyAsync(Function)");
    }

    @BmcUnmodelable(reason = "an explicit Executor's true concurrency is the concurrency wall — a sequential model has no executor; use the synchronous combinator")
    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.exceptionallyComposeAsync(java.util.function.Function, java.util.concurrent.Executor) — an explicit Executor's true concurrency is the concurrency wall; use exceptionallyCompose/exceptionallyComposeAsync(Function)");
    }

    @BmcUnmodelable(reason = "a future's default Executor is real scheduling infrastructure — the concurrency wall in a sequential model")
    public Executor defaultExecutor() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.defaultExecutor() — a future's default Executor is real scheduling infrastructure — the concurrency wall");
    }

    @BmcUnmodelable(reason = "a delayed Executor needs a real wall-clock + scheduler — the concurrency wall in a sequential model")
    public static Executor delayedExecutor(long delay, TimeUnit unit) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.delayedExecutor(long, java.util.concurrent.TimeUnit) — a delayed Executor needs a real wall-clock + scheduler — the concurrency wall");
    }

    @BmcUnmodelable(reason = "a delayed Executor needs a real wall-clock + scheduler — the concurrency wall in a sequential model")
    public static Executor delayedExecutor(long delay, TimeUnit unit, Executor executor) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.delayedExecutor(long, java.util.concurrent.TimeUnit, java.util.concurrent.Executor) — a delayed Executor needs a real wall-clock + scheduler — the concurrency wall");
    }

    @BmcUnmodelable(reason = "a timed get blocks on a real wall-clock + scheduler — the concurrency wall; use get()/join() on a settled future")
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.get(long, java.util.concurrent.TimeUnit) — a timed get blocks on a real wall-clock + scheduler — the concurrency wall; use get()/join()");
    }

    @BmcUnmodelable(reason = "a real wall-clock timeout is the concurrency wall in a sequential model")
    public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.orTimeout(long, java.util.concurrent.TimeUnit) — a real wall-clock timeout is the concurrency wall");
    }

    @BmcUnmodelable(reason = "a real wall-clock timeout is the concurrency wall in a sequential model")
    public CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.completeOnTimeout(java.lang.Object, long, java.util.concurrent.TimeUnit) — a real wall-clock timeout is the concurrency wall");
    }

    @BmcUnmodelable(reason = "cancellation needs genuine happens-before between threads — the concurrency wall in a sequential model")
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.cancel(boolean) — cancellation needs genuine happens-before between threads — the concurrency wall");
    }

    @BmcUnmodelable(reason = "cancellation needs genuine happens-before between threads — the concurrency wall in a sequential model")
    public boolean isCancelled() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.isCancelled() — cancellation needs genuine happens-before between threads — the concurrency wall");
    }

    @BmcUnmodelable(reason = "obtruding a settled future's result needs genuine happens-before — the concurrency wall in a sequential model")
    public void obtrudeValue(T value) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.obtrudeValue(java.lang.Object) — obtruding a settled result needs genuine happens-before — the concurrency wall");
    }

    @BmcUnmodelable(reason = "obtruding a settled future's failure needs genuine happens-before — the concurrency wall in a sequential model")
    public void obtrudeException(Throwable ex) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.obtrudeException(java.lang.Throwable) — obtruding a settled failure needs genuine happens-before — the concurrency wall");
    }

    @BmcUnmodelable(reason = "Future.exceptionNow snapshots a racing completion — needs genuine happens-before — the concurrency wall; use isCompletedExceptionally()")
    public Throwable exceptionNow() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.exceptionNow() — snapshotting a racing completion needs genuine happens-before — the concurrency wall; use isCompletedExceptionally()");
    }

    @BmcUnmodelable(reason = "Future.resultNow snapshots a racing completion — needs genuine happens-before — the concurrency wall; use join()/getNow()")
    public T resultNow() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.resultNow() — snapshotting a racing completion needs genuine happens-before — the concurrency wall; use join()/getNow()");
    }

    @BmcUnmodelable(reason = "Future.state snapshots a racing completion (RUNNING/SUCCESS/FAILED/CANCELLED) — needs genuine happens-before — the concurrency wall")
    public Future.State state() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.state() — snapshotting a racing completion state needs genuine happens-before — the concurrency wall; use isDone()/isCompletedExceptionally()");
    }

    @BmcUnmodelable(reason = "the live dependent count is a racing-scheduler observable — the concurrency wall in a sequential model")
    public int getNumberOfDependents() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CompletableFuture.getNumberOfDependents() — the live dependent count is a racing-scheduler observable — the concurrency wall");
    }
}
