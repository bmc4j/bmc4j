package java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Sequential BMC model of an {@link ExecutorService}: an immediate / same-thread executor. Every
 * task runs <b>synchronously on the calling thread</b> at submit time, and the returned
 * {@link Future} is already completed (mirrors the {@link CompletableFuture} "a future is a ready
 * value" model and the coroutine immediate-drive runtime). bmc4j proves logic, not scheduling or
 * interleavings (Lincheck's job), so there is no real thread pool — which makes this sound: in a
 * single-threaded model "run it now" is observably identical to "run it on a worker and join".
 *
 * <p>Returned by {@link Executors#newFixedThreadPool(int)} and friends.
 */
// Model-only class (no real java.util.concurrent.ImmediateExecutorService twin) implementing the
// bmc4j ExecutorService model interface with same-thread semantics; exercised via the concurrency
// example + proofs. Every member of the modeled ExecutorService surface is implemented and conforming
// below; the JDK's scheduling-bound extras (timed awaitTermination/invokeAll, invokeAny) are not part
// of the bmc4j ExecutorService interface, so there is no own member left to classify.
public class ImmediateExecutorService implements ExecutorService {

    private boolean shutdown;

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public <T> Future<T> submit(Callable<T> task) {
        try {
            return new ImmediateFuture<>(task.call());
        } catch (Exception e) {
            return new ImmediateFuture<>(e);
        }
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public <T> Future<T> submit(Runnable task, T result) {
        task.run();
        return new ImmediateFuture<>(result);
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public Future<?> submit(Runnable task) {
        task.run();
        return new ImmediateFuture<>(null);
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> results = new ArrayList<>();
        for (Callable<T> task : tasks) {
            results.add(submit(task));
        }
        return results;
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public void shutdown() {
        shutdown = true;
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return new ArrayList<>();
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    @BmcModelConforms("synchronous submit, completed Future")
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    /**
     * A {@link Future} whose result is already available (the task ran synchronously at submit time).
     * If the task threw, {@link #get()} wraps it in an {@link ExecutionException}, matching the JDK.
     */
    private static final class ImmediateFuture<V> implements Future<V> {
        private final V value;
        private final Throwable failure;

        ImmediateFuture(V value) {
            this.value = value;
            this.failure = null;
        }

        ImmediateFuture(Throwable failure) {
            this.value = null;
            this.failure = failure;
        }

        @Override
        public V get() throws ExecutionException {
            if (failure != null) {
                throw new ExecutionException(failure);
            }
            return value;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws ExecutionException {
            return get();
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false; // already completed — cannot cancel, per the JDK
        }
    }
}
