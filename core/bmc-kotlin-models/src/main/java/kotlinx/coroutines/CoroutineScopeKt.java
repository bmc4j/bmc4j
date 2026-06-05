package kotlinx.coroutines;

import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;

/**
 * Clean model of {@code kotlinx.coroutines.coroutineScope} / {@code supervisorScope}
 * for JBMC: run the block synchronously in an immediate scope and return its result.
 * Structured-concurrency semantics (awaiting children, cancellation) are not modelled
 * — this proves the block's logic under immediate dispatch. Bundled on JBMC's analysis
 * classpath; never on a runtime classpath.
 */
public final class CoroutineScopeKt {

    private CoroutineScopeKt() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object coroutineScope(Function2 block, Continuation completion) {
        return block.invoke(Drive.scope(), completion);
    }
}
