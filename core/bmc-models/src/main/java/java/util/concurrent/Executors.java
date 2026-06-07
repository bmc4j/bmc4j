package java.util.concurrent;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.concurrent.Executors} factories. Every factory returns the
 * immediate / same-thread {@link ImmediateExecutorService}: bmc4j proves logic, not scheduling or
 * interleavings (Lincheck's job), and in a single-threaded model "run it now" is observably the same
 * as "run it on a pool of N workers and join". Pool sizes / thread factories are accepted and ignored.
 */
@BmcModelTail(reason = "ThreadFactory overloads, scheduled pools (newScheduledThreadPool/newSingleThreadScheduledExecutor), callable/privileged factories, defaultThreadFactory/privilegedThreadFactory, unconfigurable wrappers, newThreadPerTaskExecutor — all route conceptually to the same immediate model but aren't separately modeled; loud under JBMC")
public final class Executors {

    private Executors() {
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newFixedThreadPool(int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newSingleThreadExecutor() {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newCachedThreadPool() {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newWorkStealingPool() {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newWorkStealingPool(int parallelism) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newVirtualThreadPerTaskExecutor() {
        return new ImmediateExecutorService();
    }
}
