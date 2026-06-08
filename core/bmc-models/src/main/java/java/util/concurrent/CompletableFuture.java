package java.util.concurrent;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

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
 * combinator on one thread. Every overload taking an explicit {@link Executor} stays in the
 * <b>tail</b> (loud under JBMC): a non-immediate executor's true concurrency is the concurrency wall a
 * sequential model cannot soundly model.
 */
@BmcModelTail(reason = "every overload taking an explicit Executor (then*Async(…,Executor)/handleAsync(…,Executor)/whenCompleteAsync(…,Executor)/exceptionallyAsync(…,Executor)/supplyAsync(…,Executor)/runAsync(…,Executor)/completeAsync) — a non-immediate executor's true concurrency is the concurrency wall, out of scope; plus the either/both combinators (applyToEither/acceptEither/runAfterBoth/runAfterEither/thenAcceptBoth and their *Async twins), exceptionallyCompose*, timeouts (orTimeout/completeOnTimeout/get(timeout)/delayedExecutor), cancellation/obtrusion (cancel/isCancelled/obtrude*/exceptionNow/resultNow/state), and stage/copy plumbing (minimalCompletionStage/completedStage/failedFuture/failedStage/newIncompleteFuture/defaultExecutor/copy/getNumberOfDependents) — out of scope for a sequential ready-value/ready-failure model; all loud under JBMC")
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

    /** Returns the backing future ({@code this}) — a {@link CompletableFuture} already IS its own stage. */
    @Override
    @BmcModelConforms("differential (ConcurrencyConformanceTest): a CompletableFuture is its own backing stage")
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }
}
