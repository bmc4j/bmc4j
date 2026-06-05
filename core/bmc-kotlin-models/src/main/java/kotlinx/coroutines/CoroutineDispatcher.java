package kotlinx.coroutines;

import kotlin.coroutines.AbstractCoroutineContextElement;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.ContinuationInterceptor;
import kotlin.coroutines.CoroutineContext;

/**
 * Clean model of the abstract {@code kotlinx.coroutines.CoroutineDispatcher}. The real
 * class drags in the dispatcher/scheduler machinery (DefaultScheduler, event loop)
 * whose coroutine state machines trip JBMC's create_parameter_names invariant. We
 * never actually instantiate a dispatcher (Dispatchers.* return null and builders
 * ignore the context), but JBMC still loads this TYPE for the return-type/hierarchy
 * reference — so it must resolve to this clean version rather than the real one.
 */
public class CoroutineDispatcher extends AbstractCoroutineContextElement
        implements ContinuationInterceptor {

    public CoroutineDispatcher() {
        super(ContinuationInterceptor.Key);
    }

    public boolean isDispatchNeeded(CoroutineContext context) {
        return false;
    }

    public void dispatch(CoroutineContext context, Runnable block) {
        block.run();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Continuation<T> interceptContinuation(Continuation<? super T> continuation) {
        return (Continuation<T>) continuation;
    }

    @Override
    public void releaseInterceptedContinuation(Continuation<?> continuation) {
    }
}
