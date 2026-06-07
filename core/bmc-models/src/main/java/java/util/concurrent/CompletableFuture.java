package java.util.concurrent;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.CompletableFuture} — a future is just "a value
 * that is ready". `*Async` builders run their task eagerly (single-threaded), and `get`/`join`
 * return the value. Lets logic proofs go through code structured around futures; bmc4j does not model
 * actual asynchrony/scheduling (concurrency is Lincheck's job).
 */
@BmcModelTail(reason = "the wide async-combinator surface not on the modeled eager path — *Async overloads taking an Executor, exceptionally*/handle*/whenComplete*, allOf/anyOf, orTimeout/completeOnTimeout, get(timeout)/getNow, cancel/obtrude*/minimalCompletionStage/newIncompleteFuture/defaultExecutor/copy — out of scope for a sequential ready-value model; all loud under JBMC")
public class CompletableFuture<T> {

    private T value;
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
    public boolean isDone() {
        return done;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public T get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public T join() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public T getNow(T valueIfAbsent) {
        return done ? value : valueIfAbsent;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return completedFuture(fn.apply(value));
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        action.accept(value);
        return completedFuture(null);
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public CompletableFuture<Void> thenRun(Runnable action) {
        action.run();
        return completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletableFuture<U>> fn) {
        return (CompletableFuture<U>) fn.apply(value);
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public <U, V> CompletableFuture<V> thenCombine(
            CompletableFuture<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return completedFuture(fn.apply(value, other.value));
    }
}
