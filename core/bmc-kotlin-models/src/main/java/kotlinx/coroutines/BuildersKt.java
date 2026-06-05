package kotlinx.coroutines;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;

/**
 * Clean model of {@code kotlinx.coroutines.runBlocking} for JBMC. The real builder
 * spins up an event loop and dispatcher — far too much machinery to analyze. This
 * model drives the suspend block synchronously with an immediately-completing
 * continuation, which is exactly the semantics for a coroutine that does not
 * actually suspend (an immediate dispatcher / unit-testable business logic).
 *
 * <p>So a proof can call suspend functions idiomatically:
 * {@code Bmc.check(runBlocking { svc.compute(x) } == expected)} — no hand-written
 * driver. Bundled on JBMC's analysis classpath; never on a runtime classpath.
 */
public final class BuildersKt {

    private BuildersKt() {
    }

    /** Drives the block to completion and returns its result. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T runBlocking(CoroutineContext context, Function2 block) {
        // Named nested classes (not lambdas/anon) so there is no invokedynamic and
        // the bundled class names are predictable.
        return (T) block.invoke(new ImmediateScope(), new Completion());
    }

    private static final class ImmediateScope implements CoroutineScope {
        @Override
        public CoroutineContext getCoroutineContext() {
            return EmptyCoroutineContext.INSTANCE;
        }
    }

    private static final class Completion implements Continuation<Object> {
        @Override
        public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
        }

        @Override
        public void resumeWith(Object result) {
            // synchronous drive: nothing to resume
        }
    }

    /** Bytecode shape the compiler emits for the default-argument call. */
    @SuppressWarnings("rawtypes")
    public static Object runBlocking$default(CoroutineContext context, Function2 block, int flags, Object marker) {
        return runBlocking(context == null ? EmptyCoroutineContext.INSTANCE : context, block);
    }

    /**
     * Model of {@code withContext(ctx) { }}: the target context (usually a
     * Dispatcher) is irrelevant to a logic proof — drive the block synchronously
     * and return its result.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object withContext(CoroutineContext context, Function2 block, Continuation completion) {
        return block.invoke(Drive.scope(), completion);
    }

    /**
     * Model of {@code async { } }: run the block synchronously now and return an
     * already-completed Deferred holding its result (so {@code await()} returns it).
     * Concurrency/laziness aren't modelled — this proves the logic, not interleavings.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Deferred async$default(CoroutineScope scope, CoroutineContext context,
                                         CoroutineStart start, Function2 block, int flags, Object marker) {
        return new CompletedDeferred(block.invoke(Drive.scope(), Drive.completion()));
    }

    /**
     * Model of {@code launch { } }: run the launched block synchronously now (its
     * effects are visible by the time a structured scope returns) and hand back an
     * already-completed Job. Fire-and-forget timing/interleaving isn't modelled —
     * concurrency belongs in Lincheck; this proves the launched body's logic.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Job launch$default(CoroutineScope scope, CoroutineContext context,
                                     CoroutineStart start, Function2 block, int flags, Object marker) {
        block.invoke(Drive.scope(), Drive.completion());
        return new CompletedJob();
    }
}
