package example.datetime;

import java.time.Instant;

/**
 * A time zone with a single offset transition (a DST fall-back). The offset before
 * the transition is larger than after it, so at the transition the local wall-clock
 * jumps <em>backward</em> — making {@link #localMillis} non-monotonic. That backward
 * jump is the entire source of the "toggle fires again" class of bug.
 */
public final class Zone {

    final long offsetBeforeMillis; // e.g. summer / DST
    final long offsetAfterMillis;  // e.g. winter / STD (smaller -> clocks go back)
    final Instant transition;      // the fall-back instant

    public Zone(long offsetBeforeMillis, long offsetAfterMillis, Instant transition) {
        this.offsetBeforeMillis = offsetBeforeMillis;
        this.offsetAfterMillis = offsetAfterMillis;
        this.transition = transition;
    }

    /** Local wall-clock as epoch-millis. Drops by (before - after) at the transition. */
    public long localMillis(Instant t) {
        long offset = t.isBefore(transition) ? offsetBeforeMillis : offsetAfterMillis;
        return t.toEpochMilli() + offset;
    }
}
