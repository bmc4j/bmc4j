package java.util.concurrent;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * BMC model of {@link java.util.concurrent.Executors} factories. Every pool factory returns the
 * immediate / same-thread {@link ImmediateExecutorService} (the scheduled factories return the
 * {@link ImmediateScheduledExecutorService}): bmc4j proves logic, not scheduling or interleavings
 * (Lincheck's job), and in a single-threaded model "run it now" is observably the same as "run it on a
 * pool of N workers and join". Pool sizes / thread factories are accepted and ignored — a
 * single-threaded model never invokes the factory to spawn a worker. The scheduled factories ignore
 * the configured delay/period for the same reason.
 *
 * <p>The {@code callable(Runnable[, result])} adapters are pure same-thread logic (wrap a Runnable as a
 * Callable that runs it and returns the result), so they ARE modeled. What stays loud: the factories
 * whose entire job is to manufacture real {@code Thread}s ({@code defaultThreadFactory}/
 * {@code privilegedThreadFactory}) or to run under a {@code java.security} privileged context
 * ({@code privilegedCallable*}, the {@code PrivilegedAction}/{@code PrivilegedExceptionAction}
 * {@code callable} overloads) — those only make sense with real threads / a security manager and have
 * no sequential meaning.
 */
@BmcModelTail(reason = "the Thread-manufacturing factories (defaultThreadFactory/privilegedThreadFactory) and the java.security privileged adapters (privilegedCallable*, callable(PrivilegedAction)/callable(PrivilegedExceptionAction)) only make sense with real threads / a security context — no sequential meaning; loud under JBMC")
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

    @BmcModelConforms("the ThreadFactory pool overloads route to the same-thread model; the factory is never invoked (no worker is ever spawned)")
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newSingleThreadExecutor() {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the ThreadFactory pool overloads route to the same-thread model; the factory is never invoked (no worker is ever spawned)")
    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the common newFixedThreadPool/newSingleThreadExecutor/newCachedThreadPool/newWorkStealing*/newVirtualThreadPerTaskExecutor route to the same-thread model")
    public static ExecutorService newCachedThreadPool() {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the ThreadFactory pool overloads route to the same-thread model; the factory is never invoked (no worker is ever spawned)")
    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ImmediateExecutorService();
    }

    @BmcModelConforms("the ThreadFactory pool overloads route to the same-thread model; the factory is never invoked (no worker is ever spawned)")
    public static ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
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

    @BmcModelConforms("the scheduled factories return the same-thread scheduled model; one-shot schedule runs synchronously, the configured delay/period is ignored")
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateScheduledExecutorService();
    }

    @BmcModelConforms("the scheduled factories return the same-thread scheduled model; the ThreadFactory is never invoked and the configured delay/period is ignored")
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException();
        }
        return new ImmediateScheduledExecutorService();
    }

    @BmcModelConforms("the scheduled factories return the same-thread scheduled model; one-shot schedule runs synchronously, the configured delay is ignored")
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new ImmediateScheduledExecutorService();
    }

    @BmcModelConforms("the scheduled factories return the same-thread scheduled model; the ThreadFactory is never invoked and the configured delay is ignored")
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory threadFactory) {
        return new ImmediateScheduledExecutorService();
    }

    @BmcModelConforms("the unconfigurable wrapper is a pass-through on a sequential model (no config surface to hide); returns the delegate")
    public static ExecutorService unconfigurableExecutorService(ExecutorService executor) {
        if (executor == null) {
            throw new NullPointerException();
        }
        return executor;
    }

    @BmcModelConforms("the unconfigurable wrapper is a pass-through on a sequential model (no config surface to hide); returns the delegate")
    public static ScheduledExecutorService unconfigurableScheduledExecutorService(ScheduledExecutorService executor) {
        if (executor == null) {
            throw new NullPointerException();
        }
        return executor;
    }

    @BmcModelConforms("callable(Runnable, result) wraps the task as a Callable that runs it synchronously and returns the supplied result")
    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException();
        }
        return () -> {
            task.run();
            return result;
        };
    }

    @BmcModelConforms("callable(Runnable) wraps the task as a Callable that runs it synchronously and returns null")
    public static Callable<Object> callable(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        return () -> {
            task.run();
            return null;
        };
    }

    @BmcNotModelled(reason = "java.security privileged execution context — only meaningful under a security manager, no sequential semantics")
    public static Callable<Object> callable(java.security.PrivilegedAction<?> action) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.Executors.callable(java.security.PrivilegedAction) — java.security privileged execution context, no sequential semantics");
    }

    @BmcNotModelled(reason = "java.security privileged execution context — only meaningful under a security manager, no sequential semantics")
    public static Callable<Object> callable(java.security.PrivilegedExceptionAction<?> action) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.Executors.callable(java.security.PrivilegedExceptionAction) — java.security privileged execution context, no sequential semantics");
    }

    @BmcNotModelled(reason = "java.security privileged execution context — only meaningful under a security manager, no sequential semantics")
    public static <T> Callable<T> privilegedCallable(Callable<T> callable) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.Executors.privilegedCallable(java.util.concurrent.Callable) — java.security privileged execution context, no sequential semantics");
    }

    @BmcNotModelled(reason = "java.security privileged execution context — only meaningful under a security manager, no sequential semantics")
    public static <T> Callable<T> privilegedCallableUsingCurrentClassLoader(Callable<T> callable) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.Executors.privilegedCallableUsingCurrentClassLoader(java.util.concurrent.Callable) — java.security privileged execution context, no sequential semantics");
    }

    @BmcNotModelled(reason = "manufactures real java.lang.Thread instances — thread creation has no sequential meaning (the immediate model never spawns a worker)")
    public static ThreadFactory defaultThreadFactory() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.Executors.defaultThreadFactory() — manufactures real java.lang.Thread instances, no sequential meaning");
    }

    @BmcNotModelled(reason = "manufactures real java.lang.Thread instances under a privileged context — thread creation has no sequential meaning")
    public static ThreadFactory privilegedThreadFactory() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.Executors.privilegedThreadFactory() — manufactures real java.lang.Thread instances under a privileged context, no sequential meaning");
    }
}
