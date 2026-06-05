package java.util.concurrent;

/**
 * Minimal BMC model of {@link java.util.concurrent.TimeUnit}. bmc4j does not model real time or
 * blocking, so the unit only ever appears as an (ignored) argument to timed methods on the
 * sequential concurrency models (e.g. {@code CountDownLatch.await(t, unit)},
 * {@code BlockingQueue.poll(t, unit)}). The enum constants exist so such call sites compile and
 * devirtualize; their conversion factors are the real ones for completeness.
 */
public enum TimeUnit {
    NANOSECONDS,
    MICROSECONDS,
    MILLISECONDS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS;
}
