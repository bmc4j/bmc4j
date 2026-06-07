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
 * <p><b>{@code *Async} stance (documented, unchanged):</b> the no-arg static <em>builders</em>
 * ({@code supplyAsync(Supplier)}/{@code runAsync(Runnable)}) are modeled as their synchronous
 * equivalents — a sequential model has no executor. The instance {@code then*Async}/{@code handleAsync}/
 * {@code whenCompleteAsync}/{@code exceptionallyAsync} chaining variants and ALL overloads taking an
 * {@link Executor} stay in the <b>tail</b> (loud under JBMC): proofs should call the plain sync
 * combinator, which the model treats as identical to its {@code *Async} twin would be on one thread —
 * so modeling the async overloads would add surface without adding any distinct sequential behavior.
 * This keeps the modeled set to exactly the combinators a sequential logic proof needs to name.
 */
@BmcModelTail(reason = "the *Async chaining/recovery overloads (then*Async/handleAsync/whenCompleteAsync/exceptionallyAsync) and every overload taking an Executor — a sequential model adds no distinct behavior over the plain sync combinator; plus the either/both combinators (applyToEither/acceptEither/runAfterBoth/runAfterEither), timeouts (orTimeout/completeOnTimeout/get(timeout)/delayedExecutor), cancellation/obtrusion (cancel/isCancelled/obtrude*/exceptionNow/resultNow/state), and stage/copy plumbing (minimalCompletionStage/completedStage/failedFuture/failedStage/newIncompleteFuture/defaultExecutor/copy/toCompletableFuture/getNumberOfDependents) — out of scope for a sequential ready-value/ready-failure model; all loud under JBMC")
public class CompletableFuture<T> {

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

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        if (ex != null) {
            return failed(ex);
        }
        return completedFuture(fn.apply(value));
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        if (ex != null) {
            return failed(ex);
        }
        action.accept(value);
        return completedFuture(null);
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<Void> thenRun(Runnable action) {
        if (ex != null) {
            return failed(ex);
        }
        action.run();
        return completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletableFuture<U>> fn) {
        if (ex != null) {
            return failed(ex);
        }
        return (CompletableFuture<U>) fn.apply(value);
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U, V> CompletableFuture<V> thenCombine(
            CompletableFuture<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        if (ex != null) {
            return failed(ex);
        }
        if (other.ex != null) {
            return failed(other.ex);
        }
        return completedFuture(fn.apply(value, other.value));
    }

    /**
     * Recovers an exceptional completion: if this future failed, returns a future completed with
     * {@code fn.apply(cause)}; otherwise passes the value through unchanged. The JDK passes the RAW
     * cause to {@code fn} (not wrapped in CompletionException), which this matches.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        if (ex != null) {
            return completedFuture(fn.apply(ex));
        }
        return completedFuture(value);
    }

    /**
     * Handles both outcomes: runs {@code fn.apply(value, cause)} (exactly one of the two is non-null —
     * value on normal completion, cause on exceptional) and completes the result with whatever {@code fn}
     * returns. This RECOVERS a failed future. The JDK passes the RAW cause to {@code fn}, which matches.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return completedFuture(fn.apply(ex != null ? null : value, ex));
    }

    /**
     * Observes the completion without changing it: runs {@code action.accept(value, cause)} (one of the
     * two is non-null) and returns a future with the SAME completion — value passes through, exception
     * propagates (NOT recovered). The JDK passes the RAW cause to {@code action}, which matches.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        action.accept(ex != null ? null : value, ex);
        if (ex != null) {
            return failed(ex);
        }
        return completedFuture(value);
    }
}
