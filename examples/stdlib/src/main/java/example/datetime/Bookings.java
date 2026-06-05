package example.datetime;

import java.time.Instant;

/** Time-window checks — a classic home for inclusive/exclusive boundary bugs. */
public final class Bookings {

    private Bookings() {
    }

    /**
     * Is {@code when} within [{@code start}, {@code end}]? BUG: the upper bound uses
     * {@code isBefore}, which excludes {@code end} itself.
     */
    public static boolean within(Instant when, Instant start, Instant end) {
        return !when.isBefore(start) && when.isBefore(end);
    }

    /** The fix: inclusive on both ends. */
    public static boolean withinInclusive(Instant when, Instant start, Instant end) {
        return !when.isBefore(start) && !when.isAfter(end);
    }
}
