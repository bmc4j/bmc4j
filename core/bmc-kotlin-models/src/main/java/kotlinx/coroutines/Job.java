package kotlinx.coroutines;

/**
 * Clean minimal model of {@code kotlinx.coroutines.Job}. The real interface is a
 * {@code CoroutineContext.Element} with ~20 lifecycle methods; a logic proof only ever
 * holds a Job (from launch) or awaits a Deferred and never uses it as a context or
 * calls its lifecycle, so a bare marker interface suffices — and avoids dragging in
 * the context machinery (and its not-null checks) that a real Element would.
 */
public interface Job {
}
