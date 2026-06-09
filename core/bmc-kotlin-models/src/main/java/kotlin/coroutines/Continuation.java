package kotlin.coroutines;

/**
 * Clean model of {@code kotlin.coroutines.Continuation} for JBMC. Identical in shape to stdlib's;
 * bundled so that the relationship between it and the bundled continuation impls
 * ({@code BaseContinuationImpl}, {@code ContinuationImpl}, the compiler-generated {@code …$N}
 * state-machine classes that extend them) lives in ONE classpath source. A suspend body's
 * {@code checkcast Continuation} on such a subtype then resolves without a cross-source lazy link —
 * the nondeterministic-havoc shape that produced spurious "Dynamic cast check" refutations. See
 * {@link CoroutineContext} for the full rationale.
 */
public interface Continuation<T> {

    CoroutineContext getContext();

    void resumeWith(Object result);
}
