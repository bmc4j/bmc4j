package example.datetime;

import java.time.Instant;

/** A feature toggle that is "on" during a time window. */
public final class Toggle {

    private Toggle() {
    }

    /**
     * BUG: the window is checked against local wall-clock. Across a DST fall-back the
     * local clock repeats an hour, so the window can be entered twice — the toggle
     * goes on, off, then on again.
     */
    public static boolean activeByLocalTime(Instant now, Zone zone, long windowStart, long windowEnd) {
        long local = zone.localMillis(now);
        return local >= windowStart && local < windowEnd;
    }

    /** FIX: check the window against the instant (UTC), which always moves forward. */
    public static boolean activeByInstant(Instant now, long windowStartUtc, long windowEndUtc) {
        long ms = now.toEpochMilli();
        return ms >= windowStartUtc && ms < windowEndUtc;
    }
}
