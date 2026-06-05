package kotlin.coroutines.jvm.internal;

import kotlin.coroutines.Continuation;

/**
 * Clean model of the base class Kotlin generates for a {@code suspend} lambda
 * (e.g. the block passed to {@code runBlocking { }}). It is a continuation that
 * also carries the lambda arity; the generated subclass supplies {@code create},
 * {@code invoke} and {@code invokeSuspend}.
 */
public abstract class SuspendLambda extends ContinuationImpl {

    private final int arity;

    public SuspendLambda(int arity, Continuation<Object> completion) {
        super(completion);
        this.arity = arity;
    }

    public SuspendLambda(int arity) {
        super(null);
        this.arity = arity;
    }

    public int getArity() {
        return arity;
    }
}
