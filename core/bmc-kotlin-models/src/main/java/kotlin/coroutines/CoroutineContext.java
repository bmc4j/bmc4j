package kotlin.coroutines;

import kotlin.jvm.functions.Function2;

/**
 * Clean model of {@code kotlin.coroutines.CoroutineContext} for JBMC.
 *
 * <p>The interface itself is just a type with no analysis-relevant body, identical to stdlib's. We
 * bundle it (rather than leaving it to resolve against the real kotlin-stdlib jar) for a SOUNDNESS
 * reason, not a behavioural one: the bundled coroutine subtypes — {@code
 * kotlinx.coroutines.CoroutineDispatcher}, the continuation impls — are cast to {@code
 * CoroutineContext} / {@code Continuation} in compiler-generated suspend bodies (e.g. {@code
 * withContext(Dispatchers.IO) { }} emits {@code checkcast CoroutineContext} on the dispatcher).
 * When the subtype is bundled but its cast-target supertype is the real stdlib class, JBMC has to
 * lazily link that hierarchy edge ACROSS two classpath sources (the prepended model dir and the
 * real stdlib jar); on some platforms/conversion orders that link is dropped and the cast result is
 * havoc'd, producing a spurious "Dynamic cast check" refutation that is nondeterministic across
 * runs. Bundling the whole {@code kotlin.coroutines} core hierarchy keeps every subtype→supertype
 * edge in ONE classpath source, so the cast is decided deterministically. No nondet, sound Java.
 */
public interface CoroutineContext {

    <E extends Element> E get(Key<E> key);

    <R> R fold(R initial, Function2<? super R, ? super Element, ? extends R> operation);

    CoroutineContext plus(CoroutineContext context);

    CoroutineContext minusKey(Key<?> key);

    /** A key for a context element. */
    interface Key<E extends Element> {
    }

    /** A single element of a context — itself a one-element context. */
    interface Element extends CoroutineContext {

        Key<?> getKey();
    }
}
