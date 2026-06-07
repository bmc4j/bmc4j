package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * JBMC model of {@link java.time.Instant} as an epoch-millisecond {@code long}, so
 * temporal logic reduces to integer arithmetic JBMC reasons about precisely.
 *
 * <p>Only the common methods are modeled; time zones, leap seconds and sub-milli
 * precision are out of scope (a model, not a reimplementation). {@code now()} is
 * intentionally not modeled — pass Instants as proof parameters (symbolic inputs).
 */
@BmcModelConforms("epoch-millis Instant — differential (TimeConformanceTest) + @BmcProof (proofs.time)")
@BmcModelTail(reason = "time-zone/leap-second/sub-milli precision, the Temporal interface plumbing (with/get/until/query/adjustInto/range/isSupported/plus(TemporalAmount)), atZone/atOffset, and text parse/format are out of scope for the epoch-millis model; all loud under JBMC")
public final class Instant {

    final long millis;

    private Instant(long millis) {
        this.millis = millis;
    }

    @BmcNotModelled(reason = "wall-clock read is non-deterministic external state — pass Instants as symbolic proof parameters")
    public static Instant now() {
        throw fail("bmc4j: unmodelled member java.time.Instant.now() — wall-clock read is non-deterministic external state — pass Instants as symbolic proof parameters");
    }

    // The epoch-millis backing has no sub-millisecond resolution, so the nanosecond surface
    // (getNano / plusNanos / minusNanos / ofEpochSecond(long, nanoAdjustment)) cannot be modeled
    // soundly — declined LOUD rather than silently dropping precision.

    @BmcNotModelled(reason = "sub-millisecond resolution — the nano-of-second field can't be represented on the epoch-millis backing")
    public int getNano() {
        throw fail("bmc4j: unmodelled member java.time.Instant.getNano() — sub-millisecond resolution — the nano-of-second field can't be represented on the epoch-millis backing");
    }

    @BmcNotModelled(reason = "sub-millisecond resolution — nanos can't be represented on the epoch-millis backing")
    public Instant plusNanos(long nanosToAdd) {
        throw fail("bmc4j: unmodelled member java.time.Instant.plusNanos(long) — sub-millisecond resolution — nanos can't be represented on the epoch-millis backing");
    }

    @BmcNotModelled(reason = "sub-millisecond resolution — nanos can't be represented on the epoch-millis backing")
    public Instant minusNanos(long nanosToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.Instant.minusNanos(long) — sub-millisecond resolution — nanos can't be represented on the epoch-millis backing");
    }

    @BmcNotModelled(reason = "the nanoAdjustment second-overflow normalization needs sub-millisecond resolution the epoch-millis backing lacks")
    public static Instant ofEpochSecond(long epochSecond, long nanoAdjustment) {
        throw fail("bmc4j: unmodelled member java.time.Instant.ofEpochSecond(long,long) — the nanoAdjustment second-overflow normalization needs sub-millisecond resolution the epoch-millis backing lacks");
    }

    public static Instant ofEpochMilli(long epochMilli) {
        return new Instant(epochMilli);
    }

    public static Instant ofEpochSecond(long epochSecond) {
        // This model is millis-bounded (narrower than the real Instant's range). Route the
        // seconds->millis scale through a checked multiply so an out-of-bound second count fails
        // LOUDLY (MathBytecode redirects Math.multiplyExact to the loud BmcMath under analysis)
        // rather than silently wrapping to a wrong value.
        return new Instant(Math.multiplyExact(epochSecond, 1000L));
    }

    public long toEpochMilli() {
        return millis;
    }

    public long getEpochSecond() {
        // Floor toward negative infinity like the real Instant (seconds + 0..999ms), NOT truncate
        // toward zero: ofEpochMilli(-1).getEpochSecond() is -1, not 0.
        long s = millis / 1000L;
        if (millis % 1000L != 0L && millis < 0L) {
            s--;
        }
        return s;
    }

    public boolean isBefore(Instant other) {
        return this.millis < other.millis;
    }

    public boolean isAfter(Instant other) {
        return this.millis > other.millis;
    }

    public int compareTo(Instant other) {
        return this.millis < other.millis ? -1 : (this.millis == other.millis ? 0 : 1);
    }

    public Instant plusMillis(long ms) {
        return new Instant(this.millis + ms);
    }

    public Instant minusMillis(long ms) {
        return new Instant(this.millis - ms);
    }

    public Instant plusSeconds(long seconds) {
        return new Instant(this.millis + seconds * 1000L);
    }

    public Instant minusSeconds(long seconds) {
        return new Instant(this.millis - seconds * 1000L);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Instant) && ((Instant) o).millis == this.millis;
    }

    @Override
    public int hashCode() {
        return (int) (millis ^ (millis >>> 32));
    }
}
