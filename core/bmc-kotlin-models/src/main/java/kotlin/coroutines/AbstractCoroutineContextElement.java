package kotlin.coroutines;

import kotlin.jvm.functions.Function2;

/**
 * Clean model of {@code kotlin.coroutines.AbstractCoroutineContextElement} — the base the bundled
 * {@link CoroutineDispatcher kotlinx.coroutines.CoroutineDispatcher} extends. Bundled (rather than
 * resolved against the real stdlib jar) so the dispatcher's whole supertype chain up to {@link
 * CoroutineContext} is single-source; see {@link CoroutineContext} for why that matters for the
 * cast-check determinism. The context-algebra methods are never exercised by a synchronous-drive
 * proof (builders ignore the context), so they are minimal, sound stand-ins.
 */
public abstract class AbstractCoroutineContextElement implements CoroutineContext.Element {

    private final CoroutineContext.Key<?> key;

    public AbstractCoroutineContextElement(CoroutineContext.Key<?> key) {
        this.key = key;
    }

    @Override
    public CoroutineContext.Key<?> getKey() {
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends CoroutineContext.Element> E get(CoroutineContext.Key<E> key) {
        return this.key == key ? (E) this : null;
    }

    @Override
    public <R> R fold(R initial, Function2<? super R, ? super CoroutineContext.Element, ? extends R> operation) {
        return operation.invoke(initial, this);
    }

    @Override
    public CoroutineContext minusKey(CoroutineContext.Key<?> key) {
        return this.key == key ? EmptyCoroutineContext.INSTANCE : this;
    }

    @Override
    public CoroutineContext plus(CoroutineContext context) {
        return context == EmptyCoroutineContext.INSTANCE ? this : context;
    }
}
