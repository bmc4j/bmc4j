package java.util.concurrent;

/**
 * Minimal BMC model of {@link java.util.concurrent.Executor}. The bmc4j executor models run tasks
 * <b>synchronously on the calling thread</b> (immediate/same-thread execution) — bmc4j proves logic,
 * not scheduling/interleavings (Lincheck's job).
 */
public interface Executor {
    void execute(Runnable command);
}
