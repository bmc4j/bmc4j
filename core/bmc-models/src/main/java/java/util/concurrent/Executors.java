package java.util.concurrent;

/**
 * BMC model of {@link java.util.concurrent.Executors} factories. Every factory returns the
 * immediate / same-thread {@link ImmediateExecutorService}: bmc4j proves logic, not scheduling or
 * interleavings (Lincheck's job), and in a single-threaded model "run it now" is observably the same
 * as "run it on a pool of N workers and join". Pool sizes / thread factories are accepted and ignored.
 */
public final class Executors {

    private Executors() {
    }

    public static ExecutorService newFixedThreadPool(int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateExecutorService();
    }

    public static ExecutorService newSingleThreadExecutor() {
        return new ImmediateExecutorService();
    }

    public static ExecutorService newCachedThreadPool() {
        return new ImmediateExecutorService();
    }

    public static ExecutorService newWorkStealingPool() {
        return new ImmediateExecutorService();
    }

    public static ExecutorService newWorkStealingPool(int parallelism) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateExecutorService();
    }

    public static ExecutorService newVirtualThreadPerTaskExecutor() {
        return new ImmediateExecutorService();
    }
}
