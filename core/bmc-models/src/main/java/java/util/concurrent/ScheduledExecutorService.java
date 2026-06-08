package java.util.concurrent;

/**
 * BMC model of {@link java.util.concurrent.ScheduledExecutorService}, implemented by the immediate
 * (same-thread) {@link ImmediateScheduledExecutorService}. A one-shot {@code schedule(...)} runs its
 * task synchronously at submit time and returns a completed {@link ScheduledFuture}; the delay is
 * accepted and ignored (timing/scheduling is not modeled — bmc4j proves logic, not interleavings or
 * scheduling-delay semantics, which is Lincheck's job). The periodic
 * {@code scheduleAtFixedRate}/{@code scheduleWithFixedDelay} variants have no terminating sequential
 * meaning (an unbounded repetition driven by a real clock) and are left loud on the implementation.
 */
public interface ScheduledExecutorService extends ExecutorService {

    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);
}
