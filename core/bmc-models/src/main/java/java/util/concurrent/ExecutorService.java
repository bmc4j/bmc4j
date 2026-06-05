package java.util.concurrent;

import java.util.Collection;
import java.util.List;

/**
 * BMC model of {@link java.util.concurrent.ExecutorService}, implemented by the immediate
 * (same-thread) executor. {@code submit} runs the task synchronously and returns a completed
 * {@link Future}; the lifecycle methods are modeled trivially. bmc4j proves logic, not scheduling /
 * interleavings (Lincheck's job), so there is no real thread pool — see {@link ImmediateExecutorService}.
 */
public interface ExecutorService extends Executor {

    <T> Future<T> submit(Callable<T> task);

    <T> Future<T> submit(Runnable task, T result);

    Future<?> submit(Runnable task);

    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;

    void shutdown();

    List<Runnable> shutdownNow();

    boolean isShutdown();

    boolean isTerminated();

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
