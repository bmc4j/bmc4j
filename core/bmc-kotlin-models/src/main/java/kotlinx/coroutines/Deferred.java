package kotlinx.coroutines;

import kotlin.coroutines.Continuation;

/**
 * Clean minimal model of {@code kotlinx.coroutines.Deferred}: a Job whose result can
 * be awaited. The model's {@code async} drives the block synchronously and stores the
 * result, so {@code await} just returns it (immediate dispatch — logic, not timing).
 */
public interface Deferred extends Job {

    @SuppressWarnings("rawtypes")
    Object await(Continuation completion);
}
