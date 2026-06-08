package java.util.concurrent;

/**
 * Minimal BMC model of {@link java.util.concurrent.ScheduledFuture}, returned by the immediate
 * (same-thread) {@link ScheduledExecutorService} model. In bmc4j a scheduled task has already run
 * synchronously at submit time (the delay is ignored — scheduling/timing is not modeled), so the
 * future is always done and {@code get()} returns its value, exactly like the plain {@link Future}
 * model. The real {@code ScheduledFuture} also extends {@code Delayed}/{@code Comparable}; the delay
 * ordering is a scheduling concern bmc4j does not represent, so it is not modeled here.
 */
public interface ScheduledFuture<V> extends Future<V> {
}
