package kotlinx.coroutines;

import kotlin.Unit;
import kotlin.coroutines.Continuation;

/**
 * Clean model of {@code kotlinx.coroutines.delay} for JBMC: a no-op that returns
 * immediately. Timing is not part of a logic proof (immediate-dispatch semantics);
 * the real delay suspends on a dispatcher's scheduled queue, which we don't model.
 * Bundled on JBMC's analysis classpath; never on a runtime classpath.
 */
public final class DelayKt {

    private DelayKt() {
    }

    public static Object delay(long timeMillis, Continuation completion) {
        return Unit.INSTANCE;
    }
}
