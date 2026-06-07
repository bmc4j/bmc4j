package java.time;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * JBMC model of {@link java.time.Duration} as a {@code long} of milliseconds.
 * {@link #between} is the common source of "negative duration" bugs.
 */
@BmcModelConforms("millis-backed Duration — differential (TimeConformanceTest) + @BmcProof (proofs.time): of*/plus*/minus*/multipliedBy/negated/abs/compareTo/get*/is*")
@BmcNotModelled(member = "parse(java.lang.CharSequence)", reason = "ISO-8601 text parsing — out of scope for a bounded model (no text parsing)")
@BmcModelTail(reason = "the TemporalAmount/TemporalUnit plumbing (addTo/subtractFrom/from/get(TemporalUnit)/getUnits, of/plus/minus(long,TemporalUnit), between(Temporal,Temporal)), Duration/long division (dividedBy), and ISO formatting (toString/toMillis-precision variants) are out of scope; all loud under JBMC")
public final class Duration {

    final long millis;

    private Duration(long millis) {
        this.millis = millis;
    }

    public static Duration ofMillis(long millis) {
        return new Duration(millis);
    }

    public static Duration ofSeconds(long seconds) {
        // This model is millis-bounded (narrower than the real Duration's range). Route the
        // seconds->millis scale through a checked multiply so an out-of-bound second count fails
        // LOUDLY (MathBytecode redirects Math.multiplyExact to the loud BmcMath under analysis)
        // rather than silently wrapping to a wrong value.
        return new Duration(Math.multiplyExact(seconds, 1000L));
    }

    public static Duration between(Instant start, Instant end) {
        return new Duration(end.toEpochMilli() - start.toEpochMilli());
    }

    public long toMillis() {
        return millis;
    }

    public long getSeconds() {
        // Floor toward negative infinity like the real Duration (seconds + 0..999ms), NOT truncate
        // toward zero: ofMillis(-1).getSeconds() is -1, not 0.
        long s = millis / 1000L;
        if (millis % 1000L != 0L && millis < 0L) {
            s--;
        }
        return s;
    }

    public boolean isNegative() {
        return millis < 0L;
    }

    public boolean isZero() {
        return millis == 0L;
    }

    public Duration plus(Duration other) {
        return new Duration(this.millis + other.millis);
    }

    public Duration minus(Duration other) {
        return new Duration(this.millis - other.millis);
    }

    public Duration negated() {
        return new Duration(-this.millis);
    }

    public int compareTo(Duration other) {
        return this.millis < other.millis ? -1 : (this.millis == other.millis ? 0 : 1);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Duration) && ((Duration) o).millis == this.millis;
    }

    @Override
    public int hashCode() {
        return (int) (millis ^ (millis >>> 32));
    }
}
