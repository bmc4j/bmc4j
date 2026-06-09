package kotlin.coroutines;

/**
 * Clean model of {@code kotlin.coroutines.ContinuationInterceptor} — the interface the bundled
 * {@link CoroutineDispatcher kotlinx.coroutines.CoroutineDispatcher} implements. Bundled so the
 * dispatcher's interface chain is single-source for cast resolution (see {@link CoroutineContext}).
 * Carries the {@code Key} constant the real interface exposes so {@code CoroutineDispatcher}'s
 * {@code super(ContinuationInterceptor.Key)} binds to the bundled member.
 */
public interface ContinuationInterceptor extends CoroutineContext.Element {

    /** The key real code references as {@code ContinuationInterceptor.Key}. */
    KeyImpl Key = new KeyImpl();

    <T> Continuation<T> interceptContinuation(Continuation<? super T> continuation);

    void releaseInterceptedContinuation(Continuation<?> continuation);

    /** Key type for the interceptor element. */
    final class KeyImpl implements CoroutineContext.Key<ContinuationInterceptor> {
    }
}
