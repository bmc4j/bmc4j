package example.stubs;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates the nondet-stub footnote. {@link #clampedMillis(long)} calls
 * {@link TimeUnit#toMillis(long)} — a JDK method bmc4j ships no model for, so JBMC analyzes it as a
 * nondet stub (it returns an unconstrained {@code long}). The proof below still verifies — clamping is
 * sound for <em>any</em> returned value, so nondet is conservative here — but bmc4j surfaces the fact
 * rather than letting the green verdict silently rest on a havoc'd stand-in.
 */
public final class TokenIds {

    private TokenIds() {
    }

    /** Convert {@code seconds} to millis via the JDK, then clamp the result into {@code [0, 1000]}. */
    public static long clampedMillis(long seconds) {
        long millis = TimeUnit.SECONDS.toMillis(seconds); // unmodeled -> nondet stub
        if (millis < 0) {
            return 0;
        }
        if (millis > 1000) {
            return 1000;
        }
        return millis;
    }
}
