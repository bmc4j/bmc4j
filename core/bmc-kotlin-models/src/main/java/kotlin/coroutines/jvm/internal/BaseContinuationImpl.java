package kotlin.coroutines.jvm.internal;

import kotlin.coroutines.Continuation;

/**
 * Clean model of the coroutine continuation base. The real class implements a
 * resume trampoline plus stack-frame/serialization machinery that floods analysis;
 * for synchronous drives we only need the abstract {@code invokeSuspend} hook and a
 * minimal {@code resumeWith} that forwards to it once.
 */
public abstract class BaseContinuationImpl implements Continuation<Object> {

    private final Continuation<Object> completion;

    public BaseContinuationImpl(Continuation<Object> completion) {
        this.completion = completion;
    }

    protected abstract Object invokeSuspend(Object result);

    public final Continuation<Object> getCompletion() {
        return completion;
    }

    @Override
    public final void resumeWith(Object result) {
        invokeSuspend(result);
    }
}
