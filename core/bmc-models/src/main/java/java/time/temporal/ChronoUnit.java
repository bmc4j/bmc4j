package java.time.temporal;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.Duration;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.temporal.ChronoUnit} — the standard set of date/time units. The modeled
 * surface is the unit classification ({@code isDateBased}/{@code isTimeBased}/{@code isDurationEstimated})
 * and the {@code addTo}/{@code between}/{@code isSupportedBy} plumbing, which the JDK defines purely by
 * DELEGATING to the temporal ({@code temporal.plus(amount, this)} / {@code start.until(end, this)} /
 * {@code temporal.isSupported(this)}); this model dispatches to the (now-modeled) temporal accessors the
 * same way, so a unit operation is exactly as sound as the temporal's own modeled {@code plus}/{@code
 * until}/{@code isSupported}. The classification booleans are validated bit-for-bit by the differential
 * suite vs the real JDK.
 *
 * <p>NB: NO class-level {@code @BmcModelTail} (the enum-tail lesson, like {@link java.time.DayOfWeek}):
 * the tail loud-body synthesis would override {@code java.lang.Enum}'s FINAL members + the synthetic
 * {@code values}/{@code valueOf} and break the enum. This is a class-level COVERED enum; the required
 * {@code TemporalUnit} abstracts are the per-member-annotated bodies below.
 *
 * <p>{@code getDuration()} is declined LOUD: it returns a {@code Duration}, and the bmc4j Duration model
 * is millis-backed (no nanos), so the sub-millisecond units (NANOS/MICROS) and the FOREVER/ERAS overflow
 * units cannot be represented soundly — a NAMED UNKNOWN if reached, never a lossy value.
 */
public enum ChronoUnit implements TemporalUnit {

    NANOS,
    MICROS,
    MILLIS,
    SECONDS,
    MINUTES,
    HOURS,
    HALF_DAYS,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
    DECADES,
    CENTURIES,
    MILLENNIA,
    ERAS,
    FOREVER;

    /** Time-based units are NANOS..HALF_DAYS (those whose duration divides a standard day). */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isTimeBased() {
        return compareTo(DAYS) < 0;
    }

    /** Date-based units are DAYS..ERAS; NANOS..HALF_DAYS and FOREVER are not. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isDateBased() {
        return compareTo(DAYS) >= 0 && this != FOREVER;
    }

    /** Estimated for the calendar units (DAYS and up) and FOREVER; exact for the clock units below DAYS. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isDurationEstimated() {
        return compareTo(DAYS) >= 0;
    }

    // --- TemporalUnit plumbing: the JDK defines these purely by delegating to the temporal. ----------

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    @SuppressWarnings("unchecked")
    public <R extends Temporal> R addTo(R temporal, long amount) {
        return (R) temporal.plus(amount, this);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public long between(Temporal temporal1Inclusive, Temporal temporal2Exclusive) {
        return temporal1Inclusive.until(temporal2Exclusive, this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupportedBy(Temporal temporal) {
        return temporal.isSupported(this);
    }

    @BmcUnmodelable(reason = "getDuration returns a Duration, but the bmc4j Duration model is millis-backed (no nanos / no FOREVER-ERAS overflow), so the unit durations can't be represented soundly")
    @Override
    public Duration getDuration() {
        throw fail("bmc4j: unmodelled member java.time.temporal.ChronoUnit.getDuration() — the unit Duration can't be represented on the millis-backed Duration model (sub-milli NANOS/MICROS + FOREVER/ERAS overflow)");
    }
}
