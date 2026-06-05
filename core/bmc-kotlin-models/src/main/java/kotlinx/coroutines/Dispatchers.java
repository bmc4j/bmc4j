package kotlinx.coroutines;

/**
 * Clean model of {@code kotlinx.coroutines.Dispatchers} for JBMC. Returns {@code null}
 * dispatchers: the dispatcher is only ever passed to a builder (withContext / async /
 * launch) whose model drives the block synchronously and ignores the target context,
 * so the null is never dereferenced. This keeps the real dispatcher machinery
 * (DefaultScheduler, event loop, …) — which trips JBMC's create_parameter_names
 * invariant — off the analysis classpath. Bundled on JBMC's analysis classpath only.
 */
public final class Dispatchers {

    private Dispatchers() {
    }

    public static CoroutineDispatcher getDefault() {
        return null;
    }

    public static CoroutineDispatcher getIO() {
        return null;
    }

    public static CoroutineDispatcher getUnconfined() {
        return null;
    }
}
