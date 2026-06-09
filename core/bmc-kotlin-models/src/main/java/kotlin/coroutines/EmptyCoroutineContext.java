package kotlin.coroutines;

import kotlin.jvm.functions.Function2;

/**
 * Clean model of the {@code kotlin.coroutines.EmptyCoroutineContext} singleton — the context the
 * bundled coroutine builder models hand to their immediate scopes/continuations. Bundled alongside
 * the rest of the {@code kotlin.coroutines} core so the hierarchy is single-source for cast
 * resolution (see {@link CoroutineContext}).
 */
public final class EmptyCoroutineContext implements CoroutineContext {

    public static final EmptyCoroutineContext INSTANCE = new EmptyCoroutineContext();

    private EmptyCoroutineContext() {
    }

    @Override
    public <E extends Element> E get(Key<E> key) {
        return null;
    }

    @Override
    public <R> R fold(R initial, Function2<? super R, ? super Element, ? extends R> operation) {
        return initial;
    }

    @Override
    public CoroutineContext plus(CoroutineContext context) {
        return context;
    }

    @Override
    public CoroutineContext minusKey(Key<?> key) {
        return this;
    }
}
