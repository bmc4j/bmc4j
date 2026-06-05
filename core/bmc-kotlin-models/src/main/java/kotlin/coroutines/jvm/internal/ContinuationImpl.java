package kotlin.coroutines.jvm.internal;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;

/**
 * Clean model of {@code ContinuationImpl} — the superclass the compiler-generated
 * continuation for a suspend member function extends. Adds the captured context;
 * drops {@code intercepted()}/dispatcher plumbing not needed for a synchronous drive.
 */
public abstract class ContinuationImpl extends BaseContinuationImpl {

    private final CoroutineContext _context;

    public ContinuationImpl(Continuation<Object> completion, CoroutineContext context) {
        super(completion);
        this._context = context;
    }

    public ContinuationImpl(Continuation<Object> completion) {
        this(completion, completion == null ? null : completion.getContext());
    }

    @Override
    public CoroutineContext getContext() {
        return _context;
    }
}
