package java.util.concurrent;

/**
 * Minimal BMC model of {@link java.util.concurrent.Future}, returned by the immediate (same-thread)
 * {@link ExecutorService} model. In bmc4j a task submitted to the immediate executor has already run
 * synchronously, so the future is always done and {@code get()} returns its value (mirrors the
 * {@link CompletableFuture} model). Concurrency/cancellation timing is not modeled.
 */
public interface Future<V> {

    V get() throws InterruptedException, ExecutionException;

    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException;

    boolean isDone();

    boolean isCancelled();

    boolean cancel(boolean mayInterruptIfRunning);
}
