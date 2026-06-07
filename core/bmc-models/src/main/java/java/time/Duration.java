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
@BmcModelTail(reason = "the TemporalAmount/TemporalUnit plumbing (addTo/subtractFrom/from/get(TemporalUnit)/getUnits, "
    + "of/plus/minus(long,TemporalUnit), between(Temporal,Temporal)), and ISO "
    + "formatting (toString/toMillis-precision variants) are out of scope; all loud under JBMC")
public final class Duration {

    final long millis;

    private Duration(long millis) {
        this.millis = millis;
    }

    @BmcNotModelled(reason = "ISO-8601 text parsing — out of scope for a bounded model (no text parsing)")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.Duration.parse(java.lang.CharSequence) — ISO-8601 text parsing — out of scope for a bounded model (no text parsing)");
    }

    // --- factories (this model is millis-bounded; unit scaling is loud past the bound) -------------
    //
    // Each ofX routes the unit->millis scale through Math.multiplyExact, which MathBytecode redirects
    // to the loud BmcMath under analysis: an out-of-bound count fails LOUDLY rather than wrapping.

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration ofMillis(long millis) {
        return new Duration(millis);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration ofSeconds(long seconds) {
        return new Duration(Math.multiplyExact(seconds, 1000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration ofMinutes(long minutes) {
        return new Duration(Math.multiplyExact(minutes, 60_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration ofHours(long hours) {
        return new Duration(Math.multiplyExact(hours, 3_600_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration ofDays(long days) {
        return new Duration(Math.multiplyExact(days, 86_400_000L));
    }

    @BmcNotModelled(reason = "sub-millisecond resolution — the seconds+nanos adjustment can't be represented on the millis backing")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration ofNanos(long nanos) {
        throw fail("bmc4j: unmodelled member java.time.Duration.ofNanos(long) — sub-millisecond resolution — the seconds+nanos adjustment can't be represented on the millis backing");
    }

    /**
     * Elapsed time between two temporals. The real signature is {@code between(Temporal, Temporal)}, so
     * JDK-compiled proof bytecode {@code checkcast}s each Instant arg to {@code Temporal} and resolves
     * THIS interface-typed overload — the model must mirror that exact descriptor or JBMC finds no body.
     * The epoch-millis model only supports Instant endpoints; we cast back to the Instant model (now an
     * {@code instanceof Temporal}) and subtract. A non-Instant Temporal is out of scope — declined LOUD.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Duration between(java.time.temporal.Temporal start, java.time.temporal.Temporal end) {
        if (!(start instanceof Instant) || !(end instanceof Instant)) {
            throw fail("bmc4j: unmodelled member java.time.Duration.between(java.time.temporal.Temporal,java.time.temporal.Temporal) — only Instant endpoints are modeled on the epoch-millis backing");
        }
        return new Duration(((Instant) end).toEpochMilli() - ((Instant) start).toEpochMilli());
    }

    // --- conversions -------------------------------------------------------------------------------

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
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

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long getSeconds() {
        return floorSeconds();
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toSeconds() {
        return floorSeconds();
    }

    /**
     * toMinutes/toHours/toDays TRUNCATE the (already-floored) seconds toward zero — matching the JDK:
     * ofMillis(-90000).toMinutes() is -1 (not the -2 a millis-floor would give). So they divide the
     * FLOORED second count, never the raw millis.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toMinutes() {
        return floorSeconds() / 60L;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toHours() {
        return floorSeconds() / 3600L;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toDays() {
        return floorSeconds() / 86400L;
    }

    /**
     * Total length in nanoseconds. On the millis backing this is {@code millis * 1_000_000}; loud,
     * never silent at the bound — a millis count whose *1e6 leaves the {@code long} range routes
     * through {@code Math.multiplyExact} (the real nanos-precise Duration overflows here too with its
     * own ArithmeticException, so this matches the JDK contract).
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toNanos() {
        return Math.multiplyExact(millis, 1_000_000L);
    }

    /**
     * Milli-of-second part, always in {@code [0, 999]} — the millis backing's analogue of the JDK's
     * {@code toMillisPart} (which returns the nano-of-second / 1_000_000). It is the remainder after the
     * FLOORED seconds, so it is non-negative even for negative durations: {@code ofMillis(-1)} has
     * floored seconds {@code -1} and a milli part of {@code 999} ({@code -1 - (-1)*1000}). Constant
     * divisor (1000) ⇒ validated on the differential axis (TimeConformanceTest), not @BmcProof.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int toMillisPart() {
        return (int) (millis - floorSeconds() * 1000L);
    }

    /**
     * Seconds part within the minute, {@code (int)(totalSeconds % 60)} like the JDK — truncated toward
     * zero on the FLOORED total seconds, so it can be negative for negative durations (e.g.
     * {@code ofMillis(-90061500).toSecondsPart() == -2}). Constant divisor ⇒ differential-axis only.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int toSecondsPart() {
        return (int) (floorSeconds() % 60L);
    }

    /**
     * Minutes part within the hour, {@code (int)((totalSeconds / 60) % 60)} like the JDK (truncating
     * the floored seconds). Constant divisor ⇒ differential-axis only.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int toMinutesPart() {
        return (int) ((floorSeconds() / 60L) % 60L);
    }

    /**
     * Hours part within the day, {@code (int)((totalSeconds / 3600) % 24)} like the JDK (truncating the
     * floored seconds). Constant divisor ⇒ differential-axis only.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int toHoursPart() {
        return (int) ((floorSeconds() / 3600L) % 24L);
    }

    /**
     * Whole days part, {@code totalSeconds / 86400} like the JDK (truncating the floored seconds; the
     * JDK's toDays() and toDaysPart() agree). Constant divisor ⇒ differential-axis only.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public long toDaysPart() {
        return floorSeconds() / 86400L;
    }

    /**
     * A copy with the seconds field replaced, keeping the sub-second part — exactly like the JDK's
     * {@code withSeconds}, which preserves nanos. On the millis backing the sub-second part is the
     * milli-of-second ({@link #toMillisPart()}, always {@code [0, 999]}), so the result is
     * {@code seconds*1000 + milliOfSecond}; the seconds→millis scale routes through a checked multiply
     * so an out-of-bound seconds count fails LOUDLY rather than wrapping. Differential-axis only (it
     * composes the floored milli part, a constant-divisor decomposition).
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public Duration withSeconds(long seconds) {
        return new Duration(Math.addExact(Math.multiplyExact(seconds, 1000L), toMillisPart()));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isNegative() {
        return millis < 0L;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isPositive() {
        return millis > 0L;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isZero() {
        return millis == 0L;
    }

    // --- arithmetic --------------------------------------------------------------------------------

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration plus(Duration other) {
        return new Duration(this.millis + other.millis);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration minus(Duration other) {
        return new Duration(this.millis - other.millis);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration plusMillis(long millisToAdd) {
        return new Duration(this.millis + millisToAdd);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration minusMillis(long millisToSubtract) {
        return new Duration(this.millis - millisToSubtract);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration plusSeconds(long secondsToAdd) {
        return new Duration(this.millis + Math.multiplyExact(secondsToAdd, 1000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration minusSeconds(long secondsToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(secondsToSubtract, 1000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration plusMinutes(long minutesToAdd) {
        return new Duration(this.millis + Math.multiplyExact(minutesToAdd, 60_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration minusMinutes(long minutesToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(minutesToSubtract, 60_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration plusHours(long hoursToAdd) {
        return new Duration(this.millis + Math.multiplyExact(hoursToAdd, 3_600_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration minusHours(long hoursToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(hoursToSubtract, 3_600_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration plusDays(long daysToAdd) {
        return new Duration(this.millis + Math.multiplyExact(daysToAdd, 86_400_000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration minusDays(long daysToSubtract) {
        return new Duration(this.millis - Math.multiplyExact(daysToSubtract, 86_400_000L));
    }

    /**
     * Divide by a scalar, truncating toward zero exactly like the JDK (which divides the total nanos;
     * on the millis backing the truncation is identical for any millis-representable duration). A zero
     * divisor throws {@link ArithmeticException}, like the JDK. Loud, never silent at the bound: the
     * lone overflow case {@code Long.MIN_VALUE / -1} is rejected loudly (the real Duration overflows
     * here too) rather than silently wrapping to a wrong value.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration dividedBy(long divisor) {
        if (divisor == 0L) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        if (this.millis == Long.MIN_VALUE && divisor == -1L) {
            throw new ArithmeticException("Duration overflow");   // loud, never silent wrap
        }
        return new Duration(this.millis / divisor);
    }

    /**
     * Number of times {@code divisor} fits in this duration, truncated toward zero — exactly like the
     * JDK (which divides the total nanos; on the millis backing millis/millis is identical for any
     * millis-representable pair). A zero-length divisor throws {@link ArithmeticException} like the JDK.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public long dividedBy(Duration divisor) {
        if (divisor.millis == 0L) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return this.millis / divisor.millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration multipliedBy(long multiplicand) {
        // Loud on overflow (Math.multiplyExact redirected to BmcMath under analysis), never wrap.
        return new Duration(Math.multiplyExact(this.millis, multiplicand));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration negated() {
        return new Duration(Math.negateExact(this.millis));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Duration abs() {
        return new Duration(Math.absExact(this.millis));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int compareTo(Duration other) {
        return this.millis < other.millis ? -1 : (this.millis == other.millis ? 0 : 1);
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        return (o instanceof Duration) && ((Duration) o).millis == this.millis;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return (int) (millis ^ (millis >>> 32));
    }
}
