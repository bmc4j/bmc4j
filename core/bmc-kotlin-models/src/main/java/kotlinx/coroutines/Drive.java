package kotlinx.coroutines;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

/**
 * Shared "immediate dispatch" helpers for the bundled coroutine builder models.
 *
 * <p>The real kotlinx builders spin up dispatchers and an event loop — far too much
 * machinery for JBMC. Our models instead drive the suspend block <em>synchronously</em>
 * on an immediately-completing continuation, which is the correct semantics for a
 * coroutine that does not actually suspend (immediate dispatcher / unit-testable
 * business logic). This proves <b>logic</b>, not concurrency or timing (use Lincheck
 * for those). Named (non-anonymous) classes so there is no invokedynamic and the
 * bundled class names stay predictable.
 */
final class Drive {

    private Drive() {
    }

    static CoroutineScope scope() {
        return new ImmediateScope();
    }

    static Continuation<Object> completion() {
        return new Completion();
    }

    static final class ImmediateScope implements CoroutineScope {
        @Override
        public CoroutineContext getCoroutineContext() {
            return EmptyCoroutineContext.INSTANCE;
        }
    }

    static final class Completion implements Continuation<Object> {
        @Override
        public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
        }

        @Override
        public void resumeWith(Object result) {
            // synchronous drive: nothing to resume
        }
    }
}
