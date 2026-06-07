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
@BmcModelConforms("ready-value future — differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
@BmcModelTail(reason = "the wide async-combinator surface not on the modeled eager path — *Async overloads taking an Executor, exceptionally*/handle*/whenComplete*, allOf/anyOf, orTimeout/completeOnTimeout, get(timeout)/getNow, cancel/obtrude*/minimalCompletionStage/newIncompleteFuture/defaultExecutor/copy — out of scope for a sequential ready-value model; all loud under JBMC")
public class CompletableFuture<T> {

    private T value;
    private boolean done;

    public CompletableFuture() {
    }

    public static <U> CompletableFuture<U> completedFuture(U value) {
        CompletableFuture<U> f = new CompletableFuture<>();
        f.value = value;
        f.done = true;
        return f;
    }

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return completedFuture(supplier.get());
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        runnable.run();
        return completedFuture(null);
    }

    public boolean complete(T v) {
        if (done) {
            return false;
        }
        value = v;
        done = true;
        return true;
    }

    public boolean isDone() {
        return done;
    }

    public T get() {
        return value;
    }

    public T join() {
        return value;
    }

    public T getNow(T valueIfAbsent) {
        return done ? value : valueIfAbsent;
    }

    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return completedFuture(fn.apply(value));
    }

    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        action.accept(value);
        return completedFuture(null);
    }

    public CompletableFuture<Void> thenRun(Runnable action) {
        action.run();
        return completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletableFuture<U>> fn) {
        return (CompletableFuture<U>) fn.apply(value);
    }

    public <U, V> CompletableFuture<V> thenCombine(
            CompletableFuture<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return completedFuture(fn.apply(value, other.value));
    }
}
