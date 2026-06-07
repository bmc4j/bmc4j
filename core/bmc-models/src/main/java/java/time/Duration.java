package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * JBMC model of {@link java.time.Duration} as a {@code long} of milliseconds.
 * {@link #between} is the common source of "negative duration" bugs.
 *
 * <p>The model is DELIBERATELY millis-bounded (the real Duration is seconds + nanos, ±9.2e18s with
 * nanosecond resolution). Within the millis range, observable behavior matches the JDK exactly; a
 * unit→millis scale that leaves the {@code long} range routes through a checked {@code Math.*Exact}
 * so it fails LOUDLY (ArithmeticException, surfaced as a property violation under analysis) rather
 * than silently wrapping. The sub-millisecond surface (nanos, the {@code *Part} accessors,
 * {@code ofSeconds(long, long)}) cannot be modeled on a millis backing and is declined LOUD, never a
 * silent nondet stub.
 *
 * <p>Floor-vs-truncate is exactly as the JDK: {@link #toSeconds()}/{@link #getSeconds()} FLOOR toward
 * negative infinity (so {@code ofMillis(-1).toSeconds() == -1}), while {@link #toMinutes()}/
 * {@link #toHours()}/{@link #toDays()} then TRUNCATE the floored seconds toward zero (so
 * {@code ofMillis(-90000).toMinutes() == -1}, not {@code -2}). {@code Math.floorDiv} is unmodeled by
 * JBMC, so the floor is inlined with explicit sign handling.
 */
@BmcModelConforms("millis-backed Duration — differential (TimeConformanceTest) + @BmcProof (proofs.time): "
    + "ofMillis/ofSeconds/ofMinutes/ofHours/ofDays, plus/minus(Duration), plusMillis/plusSeconds/plusMinutes/"
    + "plusHours/plusDays + minus* mirror, multipliedBy(long), negated/abs/isPositive/isNegative/isZero, "
    + "toMillis/toSeconds/toMinutes/toHours/toDays + getSeconds, compareTo/equals/hashCode/between(Instant,Instant)")
@BmcModelTail(reason = "the TemporalAmount/TemporalUnit plumbing (addTo/subtractFrom/from/get(TemporalUnit)/getUnits, "
    + "of/plus/minus(long,TemporalUnit), between(Temporal,Temporal)), Duration/long division (dividedBy), and ISO "
    + "formatting (toString/toMillis-precision variants) are out of scope; all loud under JBMC")
public final class Duration {

    final long millis;

    private Duration(long millis) {
        this.millis = millis;
    }

    @BmcNotModelled(reason = "ISO-8601 text parsing — out of scope for a bounded model (no text parsing)")
    public static Duration parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.Duration.parse(java.lang.CharSequence) — ISO-8601 text parsing — out of scope for a bounded model (no text parsing)");
    }

    // --- factories (this model is millis-bounded; unit scaling is loud past the bound) -------------
    //
    // Each ofX routes the unit->millis scale through Math.multiplyExact, which MathBytecode redirects
    // to the loud BmcMath under analysis: an out-of-bound count fails LOUDLY rather than wrapping.

    public static Duration ofMillis(long millis) {
        return new Duration(millis);
    }

    public static Duration ofSeconds(long seconds) {
        return new Duration(Math.multiplyExact(seconds, 1000L));
    }

    public static Duration ofMinutes(long minutes) {
        return new Duration(Math.multiplyExact(minutes, 60_000L));
    }

    public static Duration ofHours(long hours) {
        return new Duration(Math.multiplyExact(hours, 3_600_000L));
    }

    public static Duration ofDays(long days) {
        return new Duration(Math.multiplyExact(days, 86_400_000L));
    }

    @BmcNotModelled(reason = "sub-millisecond resolution — the seconds+nanos adjustment can't be represented on the millis backing")
    public static Duration ofNanos(long nanos) {
        throw fail("bmc4j: unmodelled member java.time.Duration.ofNanos(long) — sub-millisecond resolution — the seconds+nanos adjustment can't be represented on the millis backing");
    }

    public static Duration between(Instant start, Instant end) {
        return new Duration(end.toEpochMilli() - start.toEpochMilli());
    }

    // --- conversions -------------------------------------------------------------------------------

    public long toMillis() {
        return millis;
    }

    /**
     * Floor toward negative infinity like the real Duration (seconds + 0..999ms), NOT truncate
     * toward zero: ofMillis(-1).getSeconds() is -1, not 0. Math.floorDiv is unmodeled by JBMC, so the
     * floor is inlined with explicit sign handling.
     */
    private long floorSeconds() {
        long s = millis / 1000L;
        if (millis % 1000L != 0L && millis < 0L) {
            s--;
        }
        return s;
    }

    public long getSeconds() {
        return floorSeconds();
    }

    public long toSeconds() {
        return floorSeconds();
    }

    /**
     * toMinutes/toHours/toDays TRUNCATE the (already-floored) seconds toward zero — matching the JDK:
     * ofMillis(-90000).toMinutes() is -1 (not the -2 a millis-floor would give). So they divide the
     * FLOORED second count, never the raw millis.
     */
    public long toMinutes() {
        return floorSeconds() / 60L;
    }

    public long toHours() {
        return floorSeconds() / 3600L;
    }

    public long toDays() {
        return floorSeconds() / 86400L;
    }

    public boolean isNegative() {
        return millis < 0L;
    }

    public boolean isPositive() {
        return millis > 0L;
    }

    public boolean isZero() {
        return millis == 0L;
    }

    // --- arithmetic --------------------------------------------------------------------------------

    public Duration plus(Duration other) {
        return new Duration(this.millis + other.millis);
    }

    public Duration minus(Duration other) {
        return new Duration(this.millis - other.millis);
    }

    public Duration plusMillis(long millisToAdd) {
        return new Duration(this.millis + millisToAdd);
    }

    public Duration minusMillis(long millisToSubtract) {
        return new Duration(this.millis - millisToSubtract);
    }

    public Duration plusSeconds(long secondsToAdd) {
        return new Duration(this.millis + Math.multiplyExact(secondsToAdd, 1000L));
    }

    public Duration minusSeconds(long secondsToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(secondsToSubtract, 1000L));
    }

    public Duration plusMinutes(long minutesToAdd) {
        return new Duration(this.millis + Math.multiplyExact(minutesToAdd, 60_000L));
    }

    public Duration minusMinutes(long minutesToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(minutesToSubtract, 60_000L));
    }

    public Duration plusHours(long hoursToAdd) {
        return new Duration(this.millis + Math.multiplyExact(hoursToAdd, 3_600_000L));
    }

    public Duration minusHours(long hoursToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(hoursToSubtract, 3_600_000L));
    }

    public Duration plusDays(long daysToAdd) {
        return new Duration(this.millis + Math.multiplyExact(daysToAdd, 86_400_000L));
    }

    public Duration minusDays(long daysToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(daysToSubtract, 86_400_000L));
    }

    public Duration multipliedBy(long multiplicand) {
        // Loud on overflow (Math.multiplyExact redirected to BmcMath under analysis), never wrap.
        return new Duration(Math.multiplyExact(this.millis, multiplicand));
    }

    public Duration negated() {
        return new Duration(Math.negateExact(this.millis));
    }

    public Duration abs() {
        return new Duration(Math.absExact(this.millis));
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
