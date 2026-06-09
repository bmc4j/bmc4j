package java.util.concurrent;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Sequential BMC model of a {@link ScheduledExecutorService}: an immediate / same-thread scheduled
 * executor. It IS an {@link ImmediateExecutorService} (so all the {@code submit}/{@code execute}/
 * {@code invokeAll}/lifecycle surface is inherited with the same synchronous semantics) and adds the
 * one-shot {@code schedule(...)} factories, which run their task <b>synchronously at submit time</b>
 * and return an already-completed {@link ScheduledFuture}. The {@code delay}/{@code unit} are accepted
 * and ignored: scheduling-delay timing only has meaning with a real clock and real threads, which
 * bmc4j does not model (Lincheck's job). On a single thread "run it now" is observably identical to
 * "schedule it and let it fire".
 *
 * <p>The periodic variants ({@code scheduleAtFixedRate}/{@code scheduleWithFixedDelay}) describe an
 * unbounded, clock-driven repetition with no terminating sequential meaning, so they stay loud.
 *
 * <p>Returned by {@link Executors#newScheduledThreadPool(int)} and friends.
 */
// Model-only class (no real java.util.concurrent.ImmediateScheduledExecutorService twin) implementing
// the bmc4j ScheduledExecutorService model interface with same-thread semantics; exercised via proofs +
// differential. The one-shot schedule(...) factories conform; the periodic clock-driven repetition
// (scheduleAtFixedRate/scheduleWithFixedDelay) carries per-member loud @BmcUnmodelable stubs below.
public class ImmediateScheduledExecutorService extends ImmediateExecutorService
        implements ScheduledExecutorService {

    @Override
    @BmcModelConforms("one-shot schedule runs the Runnable synchronously (delay ignored), completed ScheduledFuture")
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        command.run();
        return new ImmediateScheduledFuture<>(null);
    }

    @Override
    @BmcModelConforms("one-shot schedule runs the Callable synchronously (delay ignored), completed ScheduledFuture")
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        try {
            return new ImmediateScheduledFuture<>(callable.call());
        } catch (Exception e) {
            return new ImmediateScheduledFuture<>(e);
        }
    }

    @BmcUnmodelable(reason = "fixed-rate periodic scheduling is an unbounded clock-driven repetition with no terminating sequential meaning")
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ScheduledExecutorService.scheduleAtFixedRate(java.lang.Runnable, long, long, java.util.concurrent.TimeUnit) — fixed-rate periodic scheduling is an unbounded clock-driven repetition with no terminating sequential meaning");
    }

    @BmcUnmodelable(reason = "fixed-delay periodic scheduling is an unbounded clock-driven repetition with no terminating sequential meaning")
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ScheduledExecutorService.scheduleWithFixedDelay(java.lang.Runnable, long, long, java.util.concurrent.TimeUnit) — fixed-delay periodic scheduling is an unbounded clock-driven repetition with no terminating sequential meaning");
    }

    /**
     * A {@link ScheduledFuture} whose result is already available (the task ran synchronously at submit
     * time, the delay ignored). Mirrors {@link ImmediateExecutorService}'s completed-future model: if
     * the task threw, {@link #get()} wraps it in an {@link ExecutionException}, matching the JDK.
     */
    private static final class ImmediateScheduledFuture<V> implements ScheduledFuture<V> {
        private final V value;
        private final Throwable failure;

        ImmediateScheduledFuture(V value) {
            this.value = value;
            this.failure = null;
        }

        ImmediateScheduledFuture(Throwable failure) {
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
