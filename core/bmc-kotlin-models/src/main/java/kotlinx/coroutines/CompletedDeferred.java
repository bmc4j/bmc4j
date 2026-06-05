package kotlinx.coroutines;

import kotlin.coroutines.Continuation;

/**
 * A {@link Deferred} that already holds its result — what the {@code async} model
 * returns after driving the block synchronously. {@code await} just returns the value.
 * No context/Element machinery (see {@link Job}).
 */
final class CompletedDeferred implements Deferred {

    private final Object value;

    CompletedDeferred(Object value) {
        this.value = value;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object await(Continuation completion) {
        return value;
    }
}
