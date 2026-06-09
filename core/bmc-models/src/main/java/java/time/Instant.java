package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.ValueRange;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.Instant} as an epoch-millisecond {@code long}, so
 * temporal logic reduces to integer arithmetic JBMC reasons about precisely.
 *
 * <p>Only the common methods are modeled; time zones, leap seconds and sub-milli
 * precision are out of scope (a model, not a reimplementation). {@code now()} is
 * intentionally not modeled — pass Instants as proof parameters (symbolic inputs).
 *
 * <p><b>Why the 20-member tail is genuinely not-modelable here (deliberate, not an oversight):</b> the
 * epoch-millis backing carries a single {@code long} of milliseconds and NOTHING else, so the entire
 * Instant tail falls into one of three buckets that a millis {@code long} simply cannot represent:
 * (1) <b>sub-millisecond precision</b> — {@code getNano}/{@code plusNanos}/{@code minusNanos}/
 * {@code ofEpochSecond(long,long)} need the nano-of-second field (declined LOUD per-member above);
 * (2) <b>zone / offset / calendar projection</b> — {@code atZone}/{@code atOffset} and the
 * {@code TemporalField}/{@code TemporalUnit}/{@code TemporalAdjuster}/{@code TemporalQuery} plumbing
 * ({@code with}/{@code get}/{@code getLong}/{@code until}/{@code range}/{@code isSupported}/{@code query}/
 * {@code adjustInto}/{@code plus}/{@code minus}(TemporalAmount/long,TemporalUnit)) all require a
 * ZoneId/ZoneOffset or a field-enum the bounded model deliberately doesn't carry; (3) <b>external state /
 * text</b> — {@code now(Clock)} (non-deterministic) and {@code parse}/{@code from}. None can be made
 * sound on a millis {@code long}, so the whole tail stays LOUD under JBMC rather than forcing a
 * lossy/wrong body — reaching any of it is an honest member-named UNKNOWN, never a silent wrong value.
 *
 * <p>It {@code implements java.time.temporal.Temporal} ONLY so an Instant survives the
 * {@code checkcast java.time.temporal.Temporal} the JDK-compiled proof bytecode emits when an Instant
 * is passed to an interface-typed parameter (e.g. {@code Duration.between(Temporal, Temporal)}). Without
 * it that cast fails under JBMC and refutes spuriously ("Dynamic cast check"). The {@code Temporal}
 * abstract methods are NOT modeled — each is a LOUD stub ({@link #fail}) so reaching it is a NAMED
 * UNKNOWN, never a silent nondet: implementing the interface buys only {@code instanceof}, never turns
 * unmodeled temporal plumbing into a fake answer.
 *
 * <p>The whole real {@code Instant} surface is now accounted per-member: the modeled epoch-millis core
 * plus a LOUD {@link BmcUnmodelable} stub for every genuinely-unmodelable member (zone/offset projection,
 * the TemporalAmount/Adjuster/Query plumbing, sub-precision {@code truncatedTo}, and external-state/text).
 * There is NO class-level {@code @BmcModelTail}: nothing falls through.
 */
public final class Instant implements Temporal {

    final long millis;

    private Instant(long millis) {
        this.millis = millis;
    }

    @BmcUnmodelable(reason = "wall-clock read is non-deterministic external state — pass Instants as symbolic proof parameters")
    public static Instant now() {
        throw fail("bmc4j: unmodelled member java.time.Instant.now() — wall-clock read is non-deterministic external state — pass Instants as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "a Clock is non-deterministic external state — pass Instants as symbolic proof parameters")
    public static Instant now(Clock clock) {
        throw fail("bmc4j: unmodelled member java.time.Instant.now(java.time.Clock) — a Clock is non-deterministic external state — pass Instants as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "ISO-8601 instant text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)")
    public static Instant parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.Instant.parse(java.lang.CharSequence) — ISO-8601 instant text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)");
    }

    @BmcUnmodelable(reason = "extracting an Instant from an arbitrary TemporalAccessor needs its open-ended field surface; build via ofEpochMilli/ofEpochSecond")
    public static Instant from(TemporalAccessor temporal) {
        throw fail("bmc4j: unmodelled member java.time.Instant.from(java.time.temporal.TemporalAccessor) — extracting an Instant from an arbitrary TemporalAccessor needs its open-ended field surface; build via ofEpochMilli/ofEpochSecond");
    }

    @BmcUnmodelable(reason = "projecting an Instant onto a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits")
    public ZonedDateTime atZone(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.Instant.atZone(java.time.ZoneId) — projecting an Instant onto a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits");
    }

    @BmcUnmodelable(reason = "projecting an Instant onto an offset builds an OffsetDateTime (date+time+nanos) the epoch-millis backing can't carry")
    public OffsetDateTime atOffset(ZoneOffset offset) {
        throw fail("bmc4j: unmodelled member java.time.Instant.atOffset(java.time.ZoneOffset) — projecting an Instant onto an offset builds an OffsetDateTime (date+time+nanos) the epoch-millis backing can't carry");
    }

    @BmcUnmodelable(reason = "truncating to a unit below MILLIS (e.g. MICROS/NANOS) needs sub-millisecond resolution the epoch-millis backing lacks; the open-ended TemporalUnit surface is out of scope")
    public Instant truncatedTo(TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.Instant.truncatedTo(java.time.temporal.TemporalUnit) — truncating to a unit below MILLIS needs sub-millisecond resolution the epoch-millis backing lacks; the open-ended TemporalUnit surface is out of scope");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed plus*/minus* on the epoch-millis backing")
    public Instant with(TemporalAdjuster adjuster) {
        throw fail("bmc4j: unmodelled member java.time.Instant.with(java.time.temporal.TemporalAdjuster) — the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed plus*/minus* on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plusMillis/plusSeconds or plus(long,TemporalUnit)")
    public Instant plus(TemporalAmount amountToAdd) {
        throw fail("bmc4j: unmodelled member java.time.Instant.plus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plusMillis/plusSeconds or plus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minusMillis/minusSeconds or minus(long,TemporalUnit)")
    public Instant minus(TemporalAmount amountToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.Instant.minus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minusMillis/minusSeconds or minus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor")
    public <R> R query(TemporalQuery<R> query) {
        throw fail("bmc4j: unmodelled member java.time.Instant.query(java.time.temporal.TemporalQuery) — the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor");
    }

    @BmcUnmodelable(reason = "adjusting an arbitrary Temporal with this Instant's INSTANT_SECONDS/NANO_OF_SECOND fields needs the sub-millisecond field the epoch-millis backing lacks")
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.Instant.adjustInto(java.time.temporal.Temporal) — adjusting an arbitrary Temporal with this Instant's INSTANT_SECONDS/NANO_OF_SECOND fields needs the sub-millisecond field the epoch-millis backing lacks");
    }

    // The epoch-millis backing has no sub-millisecond resolution, so the nanosecond surface
    // (getNano / plusNanos / minusNanos / ofEpochSecond(long, nanoAdjustment)) cannot be modeled
    // soundly — declined LOUD rather than silently dropping precision.

    @BmcUnmodelable(reason = "sub-millisecond resolution — the nano-of-second field can't be represented on the epoch-millis backing")
    public int getNano() {
        throw fail("bmc4j: unmodelled member java.time.Instant.getNano() — sub-millisecond resolution — the nano-of-second field can't be represented on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "sub-millisecond resolution — nanos can't be represented on the epoch-millis backing")
    public Instant plusNanos(long nanosToAdd) {
        throw fail("bmc4j: unmodelled member java.time.Instant.plusNanos(long) — sub-millisecond resolution — nanos can't be represented on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "sub-millisecond resolution — nanos can't be represented on the epoch-millis backing")
    public Instant minusNanos(long nanosToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.Instant.minusNanos(long) — sub-millisecond resolution — nanos can't be represented on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "the nanoAdjustment second-overflow normalization needs sub-millisecond resolution the epoch-millis backing lacks")
    public static Instant ofEpochSecond(long epochSecond, long nanoAdjustment) {
        throw fail("bmc4j: unmodelled member java.time.Instant.ofEpochSecond(long,long) — the nanoAdjustment second-overflow normalization needs sub-millisecond resolution the epoch-millis backing lacks");
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Instant ofEpochMilli(long epochMilli) {
        return new Instant(epochMilli);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Instant ofEpochSecond(long epochSecond) {
        // This model is millis-bounded (narrower than the real Instant's range). Route the
        // seconds->millis scale through a checked multiply so an out-of-bound second count fails
        // LOUDLY (MathBytecode redirects Math.multiplyExact to the loud BmcMath under analysis)
        // rather than silently wrapping to a wrong value.
        return new Instant(Math.multiplyExact(epochSecond, 1000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toEpochMilli() {
        return millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long getEpochSecond() {
        // Floor toward negative infinity like the real Instant (seconds + 0..999ms), NOT truncate
        // toward zero: ofEpochMilli(-1).getEpochSecond() is -1, not 0.
        long s = millis / 1000L;
        if (millis % 1000L != 0L && millis < 0L) {
            s--;
        }
        return s;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isBefore(Instant other) {
        return this.millis < other.millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isAfter(Instant other) {
        return this.millis > other.millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int compareTo(Instant other) {
        return this.millis < other.millis ? -1 : (this.millis == other.millis ? 0 : 1);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant plusMillis(long ms) {
        return new Instant(this.millis + ms);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant minusMillis(long ms) {
        return new Instant(this.millis - ms);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant plusSeconds(long seconds) {
        return new Instant(this.millis + seconds * 1000L);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant minusSeconds(long seconds) {
        return new Instant(this.millis - seconds * 1000L);
    }

    // --- generic TemporalField / TemporalUnit accessors: dispatch on the (now-modeled) ChronoField /
    //     ChronoUnit over the epoch-millis backing. The sub-millisecond field/unit surface (NANOS/MICROS
    //     resolution) is declined LOUD — the millis backing genuinely cannot carry it. The Temporal
    //     interface makes the Instant an instanceof it (proof-site checkcast). ---

    /** Milli-of-second, FLOORED so it is non-negative even for negative instants (matches getEpochSecond). */
    private int milliOfSecond() {
        return (int) (millis - getEpochSecond() * 1000L);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            return false;
        }
        switch ((ChronoField) field) {
            case INSTANT_SECONDS:
            case MILLI_OF_SECOND:
            case MICRO_OF_SECOND:
            case NANO_OF_SECOND:
                return true;
            default:
                return false;
        }
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            ChronoUnit cu = (ChronoUnit) unit;
            return cu.isTimeBased() || cu == ChronoUnit.DAYS;
        }
        return unit != null && unit.isSupportedBy(this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case INSTANT_SECONDS:
                    return getEpochSecond();
                case MILLI_OF_SECOND:
                    return milliOfSecond();
                case MICRO_OF_SECOND:
                    // the millis backing carries no sub-milli, so micro-of-second is exactly milli*1000.
                    return milliOfSecond() * 1_000L;
                case NANO_OF_SECOND:
                    return milliOfSecond() * 1_000_000L;
                default:
                    break;
            }
        }
        throw fail("bmc4j: unmodelled member java.time.Instant.getLong(java.time.temporal.TemporalField) — only INSTANT_SECONDS / MILLI|MICRO|NANO_OF_SECOND are modeled on the epoch-millis backing");
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public int get(TemporalField field) {
        // INSTANT_SECONDS exceeds int — the JDK throws there; getLong serves those callers.
        return range(field).checkValidIntValue(getLong(field), field);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField && isSupported(field)) {
            return ((ChronoField) field).range();
        }
        throw fail("bmc4j: unmodelled member java.time.Instant.range(java.time.temporal.TemporalField) — only INSTANT_SECONDS / MILLI|MICRO|NANO_OF_SECOND are modeled on the epoch-millis backing");
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public Temporal with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField cf = (ChronoField) field;
            switch (cf) {
                case INSTANT_SECONDS:
                    cf.checkValidValue(newValue);
                    return new Instant(Math.addExact(Math.multiplyExact(newValue, 1000L), milliOfSecond()));
                case MILLI_OF_SECOND:
                    cf.checkValidValue(newValue);
                    return new Instant(getEpochSecond() * 1000L + newValue);
                default:
                    break;
            }
        }
        throw fail("bmc4j: unmodelled member java.time.Instant.with(java.time.temporal.TemporalField,long) — only INSTANT_SECONDS / MILLI_OF_SECOND are settable on the epoch-millis backing (sub-milli MICRO/NANO not representable)");
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public Temporal plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case MILLIS:
                    return plusMillis(amountToAdd);
                case SECONDS:
                    return plusSeconds(amountToAdd);
                case MINUTES:
                    return new Instant(millis + Math.multiplyExact(amountToAdd, 60_000L));
                case HOURS:
                    return new Instant(millis + Math.multiplyExact(amountToAdd, 3_600_000L));
                case HALF_DAYS:
                    return new Instant(millis + Math.multiplyExact(amountToAdd, 43_200_000L));
                case DAYS:
                    return new Instant(millis + Math.multiplyExact(amountToAdd, 86_400_000L));
                default:
                    break;
            }
        }
        throw fail("bmc4j: unmodelled member java.time.Instant.plus(long,java.time.temporal.TemporalUnit) — sub-millisecond units (NANOS/MICROS) and date units past DAYS are not modeled on the epoch-millis backing");
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public Temporal minus(long amountToSubtract, TemporalUnit unit) {
        return amountToSubtract == Long.MIN_VALUE
            ? ((Instant) plus(Long.MAX_VALUE, unit)).plus(1, unit)
            : plus(-amountToSubtract, unit);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        if (!(endExclusive instanceof Instant)) {
            throw fail("bmc4j: unmodelled member java.time.Instant.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only Instant endpoints are modeled");
        }
        long deltaMillis = ((Instant) endExclusive).millis - this.millis;
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case MILLIS:
                    return deltaMillis;
                case SECONDS:
                    return deltaMillis / 1000L;
                case MINUTES:
                    return deltaMillis / 60_000L;
                case HOURS:
                    return deltaMillis / 3_600_000L;
                case HALF_DAYS:
                    return deltaMillis / 43_200_000L;
                case DAYS:
                    return deltaMillis / 86_400_000L;
                default:
                    break;
            }
        }
        throw fail("bmc4j: unmodelled member java.time.Instant.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — sub-millisecond units (NANOS/MICROS) are not modeled on the epoch-millis backing");
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        return (o instanceof Instant) && ((Instant) o).millis == this.millis;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return (int) (millis ^ (millis >>> 32));
    }
}
