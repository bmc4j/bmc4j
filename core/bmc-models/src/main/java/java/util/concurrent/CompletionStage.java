package java.util.concurrent;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * BMC model of {@link java.util.concurrent.CompletionStage}, the interface surface of the
 * {@link CompletableFuture} model. It exists so code TYPED as {@code CompletionStage<T>} (a method
 * returning {@code CompletionStage}, a field of that type, a parameter) devirtualizes to the
 * {@link CompletableFuture} model under JBMC: the real {@code CompletableFuture implements
 * CompletionStage}, so the model mirrors that hierarchy and the stage combinators dispatch to the
 * single backing implementation.
 *
 * <p><b>Semantics are the {@link CompletableFuture} model's:</b> a stage is "a value that is ready"
 * (or "a failure that is ready"), the combinators apply EAGERLY (immediate / same-thread executor), and
 * the {@code *Async} no-arg variants reduce to their synchronous twins because a sequential model has
 * no real executor (bmc4j proves logic, not scheduling — Lincheck's job). The combinators that take
 * another stage call {@link #toCompletableFuture()} on it to read its ready completion.
 *
 * <p><b>What this interface declares</b> is exactly the modeled stage surface: the dependent-action and
 * recovery combinators that work under immediate semantics, their no-arg {@code *Async} twins, and
 * {@link #toCompletableFuture()}. The genuinely-out-of-scope members — every overload taking an
 * {@link Executor} (a non-immediate executor's true concurrency is the concurrency wall), the
 * either/both combinators ({@code applyToEither}/{@code acceptEither}/{@code runAfterBoth}/
 * {@code runAfterEither}/{@code thenAcceptBoth}), and {@code exceptionallyCompose*} — are NOT declared
 * here; they stay in {@link CompletableFuture}'s loud {@code @BmcModelTail} (reaching them under JBMC
 * fails NAMED AND LOUD, never a silent nondet stub). Modeling them would need real happens-before /
 * scheduling, which a sequential model cannot soundly supply.
 *
 * <p>Covered structurally via the {@link CompletableFuture} model (differential conformance +
 * {@code @BmcProof} laws in {@code proofs.concurrent}); waived at class level as an interface exercised
 * through its single concrete impl, exactly like {@link Future} / {@link ExecutorService}.
 */
public interface CompletionStage<T> {

    <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn);

    <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    CompletionStage<Void> thenAccept(Consumer<? super T> action);

    CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action);

    CompletionStage<Void> thenRun(Runnable action);

    CompletionStage<Void> thenRunAsync(Runnable action);

    <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    <U, V> CompletionStage<V> thenCombine(
            CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    <U, V> CompletionStage<V> thenCombineAsync(
            CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn);

    CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn);

    CompletionStage<T> exceptionallyAsync(Function<Throwable, ? extends T> fn);

    <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    CompletableFuture<T> toCompletableFuture();
}
