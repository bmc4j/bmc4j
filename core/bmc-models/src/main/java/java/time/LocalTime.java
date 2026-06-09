package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.format.DateTimeFormatter;
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
 * JBMC model of {@link java.time.LocalTime} backed by a nano-of-day {@code long} in
 * {@code [0, 86_400_000_000_000)} (24h). Field extraction and time arithmetic reduce to integer
 * arithmetic JBMC reasons about precisely.
 *
 * <p>{@code of} validates its fields exactly like the JDK (loud {@link DateTimeException} out of
 * range). {@code plus*} wraps within the day like the real LocalTime (it has no overflow concept).
 * Zones, formatters and sub-nano precision are out of scope (a model, not a reimplementation);
 * {@code now()} is intentionally not modeled — pass LocalTimes as proof parameters.
 *
 * <p>It {@code implements java.time.temporal.Temporal} so a LocalTime survives the {@code checkcast} the
 * JDK-compiled proof bytecode emits on an interface-typed parameter, AND so the generic TemporalField/
 * TemporalUnit accessors ({@code getLong}/{@code get}/{@code isSupported}/{@code range}/{@code with}/
 * {@code plus}/{@code minus}/{@code until}) can be modeled by dispatching on the (now-modeled) ChronoField/
 * ChronoUnit over the nano-of-day backing. A non-Chrono field/unit, or a date-based one this time-only
 * model can't carry, is declined LOUD — a NAMED UNKNOWN, never a wrong value.
 *
 * <p>The whole real {@code LocalTime} surface is now accounted per-member: the modeled nano-of-day core
 * plus a LOUD {@link BmcUnmodelable} stub for every genuinely-unmodelable member (zone projection,
 * text format/parse, external clock, the open-ended TemporalAmount/Adjuster/Query plumbing, and the
 * sub-precision {@code truncatedTo} surface). There is NO class-level {@code @BmcModelTail}: nothing
 * falls through.
 */
public final class LocalTime implements Temporal {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60L * NANOS_PER_MINUTE;
    private static final long NANOS_PER_DAY = 24L * NANOS_PER_HOUR;

    // Public well-known constants, mirroring the JDK's LocalTime.MIN/MAX/MIDNIGHT/NOON. MIN == MIDNIGHT
    // == 00:00 (nano-of-day 0); NOON == 12:00 (nano-of-day 12h); MAX == 23:59:59.999999999 (the last
    // nano of the day). The audit gate/docs track methods, not fields, so these carry no @Bmc*
    // annotation; the differential suite pins their nano-of-day bit-for-bit against the JDK.
    public static final LocalTime MIN = new LocalTime(0L);
    public static final LocalTime MIDNIGHT = new LocalTime(0L);
    public static final LocalTime NOON = new LocalTime(12L * NANOS_PER_HOUR);
    public static final LocalTime MAX = new LocalTime(NANOS_PER_DAY - 1L);

    final long nanoOfDay;

    private LocalTime(long nanoOfDay) {
        this.nanoOfDay = nanoOfDay;
    }

    @BmcUnmodelable(reason = "wall-clock read is non-deterministic external state — pass LocalTimes as symbolic proof parameters")
    public static LocalTime now() {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.now() — wall-clock read is non-deterministic external state — pass LocalTimes as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "a Clock is non-deterministic external state — pass LocalTimes as symbolic proof parameters")
    public static LocalTime now(Clock clock) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.now(java.time.Clock) — a Clock is non-deterministic external state — pass LocalTimes as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "the current time in a named zone is non-deterministic external state plus zone-rules projection — pass LocalTimes as symbolic proof parameters")
    public static LocalTime now(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.now(java.time.ZoneId) — the current time in a named zone is non-deterministic external state plus zone-rules projection — pass LocalTimes as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "deriving the local time of an Instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits")
    public static LocalTime ofInstant(Instant instant, ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.ofInstant(java.time.Instant,java.time.ZoneId) — deriving the local time of an Instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits");
    }

    @BmcUnmodelable(reason = "ISO-8601 time text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)")
    public static LocalTime parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.parse(java.lang.CharSequence) — ISO-8601 time text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)");
    }

    @BmcUnmodelable(reason = "formatter-driven time text parsing — out of scope for a bounded model (no text parsing/locale)")
    public static LocalTime parse(CharSequence text, DateTimeFormatter formatter) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.parse(java.lang.CharSequence,java.time.format.DateTimeFormatter) — formatter-driven time text parsing — out of scope for a bounded model (no text parsing/locale)");
    }

    @BmcUnmodelable(reason = "extracting a LocalTime from an arbitrary TemporalAccessor needs its open-ended field surface; build via LocalTime.of/ofNanoOfDay")
    public static LocalTime from(TemporalAccessor temporal) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.from(java.time.temporal.TemporalAccessor) — extracting a LocalTime from an arbitrary TemporalAccessor needs its open-ended field surface; build via LocalTime.of/ofNanoOfDay");
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalTime of(int hour, int minute) {
        return of(hour, minute, 0, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalTime of(int hour, int minute, int second) {
        return of(hour, minute, second, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalTime of(int hour, int minute, int second, int nanoOfSecond) {
        if (hour < 0 || hour > 23) {
            throw new DateTimeException("Invalid value for HourOfDay: " + hour);
        }
        if (minute < 0 || minute > 59) {
            throw new DateTimeException("Invalid value for MinuteOfHour: " + minute);
        }
        if (second < 0 || second > 59) {
            throw new DateTimeException("Invalid value for SecondOfMinute: " + second);
        }
        if (nanoOfSecond < 0 || nanoOfSecond > 999_999_999) {
            throw new DateTimeException("Invalid value for NanoOfSecond: " + nanoOfSecond);
        }
        long nod = hour * NANOS_PER_HOUR
                + minute * NANOS_PER_MINUTE
                + second * NANOS_PER_SECOND
                + nanoOfSecond;
        return new LocalTime(nod);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalTime ofSecondOfDay(long secondOfDay) {
        if (secondOfDay < 0 || secondOfDay > 86399) {
            throw new DateTimeException("Invalid value for SecondOfDay: " + secondOfDay);
        }
        return new LocalTime(secondOfDay * NANOS_PER_SECOND);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalTime ofNanoOfDay(long nanoOfDay) {
        if (nanoOfDay < 0 || nanoOfDay > NANOS_PER_DAY - 1) {
            throw new DateTimeException("Invalid value for NanoOfDay: " + nanoOfDay);
        }
        return new LocalTime(nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getHour() {
        return (int) (nanoOfDay / NANOS_PER_HOUR);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getMinute() {
        return (int) ((nanoOfDay / NANOS_PER_MINUTE) % 60);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getSecond() {
        return (int) ((nanoOfDay / NANOS_PER_SECOND) % 60);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getNano() {
        return (int) (nanoOfDay % NANOS_PER_SECOND);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int toSecondOfDay() {
        return (int) (nanoOfDay / NANOS_PER_SECOND);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toNanoOfDay() {
        return nanoOfDay;
    }

    // plus* wrap within the day (mod 24h), exactly like the real LocalTime, which has no overflow.
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime plusNanos(long nanos) {
        if (nanos == 0) {
            return this;
        }
        long dayNanos = nanos % NANOS_PER_DAY;             // reduce to (-DAY, DAY)
        long newNod = (nanoOfDay + dayNanos + NANOS_PER_DAY) % NANOS_PER_DAY;
        return new LocalTime(newNod);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime minusNanos(long nanos) {
        // Subtracting nanos == adding the negation, with the day-wrap; reduce first so -Long.MIN_VALUE
        // can't overflow (the % NANOS_PER_DAY keeps the magnitude well inside the long range).
        return plusNanos(-(nanos % NANOS_PER_DAY));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime plusHours(long hours) {
        return plusNanos((hours % 24) * NANOS_PER_HOUR);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime plusMinutes(long minutes) {
        return plusNanos((minutes % (24 * 60)) * NANOS_PER_MINUTE);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime plusSeconds(long seconds) {
        return plusNanos((seconds % (24 * 60 * 60)) * NANOS_PER_SECOND);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime minusHours(long hours) {
        return plusHours(-(hours % 24));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime minusMinutes(long minutes) {
        return plusMinutes(-(minutes % (24 * 60)));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime minusSeconds(long seconds) {
        return plusSeconds(-(seconds % (24 * 60 * 60)));
    }

    // --- with* field setters: rebuild from the fields, keeping the others, with the JDK's loud
    //     field validation (a fast-path returns this when the field is unchanged).

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalTime withHour(int hour) {
        if (getHour() == hour) {
            return this;
        }
        return of(hour, getMinute(), getSecond(), getNano());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalTime withMinute(int minute) {
        if (getMinute() == minute) {
            return this;
        }
        return of(getHour(), minute, getSecond(), getNano());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalTime withSecond(int second) {
        if (getSecond() == second) {
            return this;
        }
        return of(getHour(), getMinute(), second, getNano());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalTime withNano(int nanoOfSecond) {
        if (getNano() == nanoOfSecond) {
            return this;
        }
        return of(getHour(), getMinute(), getSecond(), nanoOfSecond);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime atDate(LocalDate date) {
        return LocalDateTime.of(date, this);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isBefore(LocalTime other) {
        return this.nanoOfDay < other.nanoOfDay;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isAfter(LocalTime other) {
        return this.nanoOfDay > other.nanoOfDay;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int compareTo(LocalTime other) {
        return this.nanoOfDay < other.nanoOfDay ? -1 : (this.nanoOfDay == other.nanoOfDay ? 0 : 1);
    }

    /**
     * Epoch-second of this time on the given date at the given offset — pure integer arithmetic over the
     * already-modeled pieces (the date's epoch-day, this time's second-of-day, the offset's total
     * seconds), no zone DB. {@code date.toEpochDay()*86400 + toSecondOfDay() - offset.getTotalSeconds()},
     * exactly like the JDK; loud {@code Math.*Exact} overflow.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public long toEpochSecond(LocalDate date, ZoneOffset offset) {
        long epochDay = date.toEpochDay();
        long secs = epochDay * 86400L + toSecondOfDay();
        return Math.subtractExact(secs, offset.getTotalSeconds());
    }

    // --- generic TemporalField / TemporalUnit accessors: dispatch on the (now-modeled) ChronoField /
    //     ChronoUnit over the nano-of-day backing. Date-based fields/units are declined LOUD. ---

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalField field) {
        return (field instanceof ChronoField) && ((ChronoField) field).isTimeBased();
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            return ((ChronoUnit) unit).isTimeBased();
        }
        return unit != null && unit.isSupportedBy(this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long getLong(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.getLong(java.time.temporal.TemporalField) — only ChronoField is dispatched by this nano-of-day model");
        }
        long nod = nanoOfDay;
        switch ((ChronoField) field) {
            case NANO_OF_SECOND:
                return nod % NANOS_PER_SECOND;
            case NANO_OF_DAY:
                return nod;
            case MICRO_OF_SECOND:
                return (nod % NANOS_PER_SECOND) / 1_000L;
            case MICRO_OF_DAY:
                return nod / 1_000L;
            case MILLI_OF_SECOND:
                return (nod % NANOS_PER_SECOND) / 1_000_000L;
            case MILLI_OF_DAY:
                return nod / 1_000_000L;
            case SECOND_OF_MINUTE:
                return getSecond();
            case SECOND_OF_DAY:
                return nod / NANOS_PER_SECOND;
            case MINUTE_OF_HOUR:
                return getMinute();
            case MINUTE_OF_DAY:
                return nod / NANOS_PER_MINUTE;
            case HOUR_OF_AMPM:
                return getHour() % 12;
            case CLOCK_HOUR_OF_AMPM: {
                int hap = getHour() % 12;
                return hap == 0 ? 12 : hap;
            }
            case HOUR_OF_DAY:
                return getHour();
            case CLOCK_HOUR_OF_DAY: {
                int h = getHour();
                return h == 0 ? 24 : h;
            }
            case AMPM_OF_DAY:
                return getHour() / 12;
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalTime.getLong(java.time.temporal.TemporalField) — the date-based field " + field + " is not supported by the time-only nano-of-day model");
        }
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public int get(TemporalField field) {
        // NANO_OF_DAY/MICRO_OF_DAY exceed int — the JDK throws there; getLong covers those callers.
        return range(field).checkValidIntValue(getLong(field), field);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange range(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.range(java.time.temporal.TemporalField) — only ChronoField is dispatched by this nano-of-day model");
        }
        ChronoField cf = (ChronoField) field;
        if (!cf.isTimeBased()) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.range(java.time.temporal.TemporalField) — the date-based field " + field + " is not supported by the time-only nano-of-day model");
        }
        return cf.range();   // time fields have a fixed (date-independent) range
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public LocalTime with(TemporalField field, long newValue) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.with(java.time.temporal.TemporalField,long) — only ChronoField is dispatched by this nano-of-day model");
        }
        ChronoField cf = (ChronoField) field;
        cf.checkValidValue(newValue);
        switch (cf) {
            case NANO_OF_DAY:
                return ofNanoOfDay(newValue);
            case NANO_OF_SECOND:
                return withNano((int) newValue);
            case MILLI_OF_SECOND:
                return withNano((int) (newValue * 1_000_000L));
            case MICRO_OF_SECOND:
                return withNano((int) (newValue * 1_000L));
            case SECOND_OF_MINUTE:
                return withSecond((int) newValue);
            case SECOND_OF_DAY:
                return ofNanoOfDay(newValue * NANOS_PER_SECOND + (nanoOfDay % NANOS_PER_SECOND));
            case MINUTE_OF_HOUR:
                return withMinute((int) newValue);
            case MINUTE_OF_DAY:
                return ofNanoOfDay(newValue * NANOS_PER_MINUTE + (nanoOfDay % NANOS_PER_MINUTE));
            case HOUR_OF_DAY:
                return withHour((int) newValue);
            case CLOCK_HOUR_OF_DAY:
                return withHour((int) (newValue == 24 ? 0 : newValue));
            case HOUR_OF_AMPM:
                return withHour((int) (getHour() / 12 * 12 + newValue));
            case AMPM_OF_DAY:
                return plusHours((newValue - getHour() / 12) * 12);
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalTime.with(java.time.temporal.TemporalField,long) — the field " + field + " (milli/micro-of-day / clock-hour-of-ampm recompose) is out of scope for this nano-of-day model");
        }
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public LocalTime plus(long amountToAdd, TemporalUnit unit) {
        if (!(unit instanceof ChronoUnit)) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.plus(long,java.time.temporal.TemporalUnit) — only ChronoUnit is dispatched by this nano-of-day model");
        }
        switch ((ChronoUnit) unit) {
            case NANOS:
                return plusNanos(amountToAdd);
            case MICROS:
                return plusNanos((amountToAdd % (NANOS_PER_DAY / 1_000L)) * 1_000L);
            case MILLIS:
                return plusNanos((amountToAdd % (NANOS_PER_DAY / 1_000_000L)) * 1_000_000L);
            case SECONDS:
                return plusSeconds(amountToAdd);
            case MINUTES:
                return plusMinutes(amountToAdd);
            case HOURS:
                return plusHours(amountToAdd);
            case HALF_DAYS:
                return plusHours((amountToAdd % 2) * 12);
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalTime.plus(long,java.time.temporal.TemporalUnit) — the date-based unit " + unit + " is not supported by the time-only nano-of-day model");
        }
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public LocalTime minus(long amountToSubtract, TemporalUnit unit) {
        return amountToSubtract == Long.MIN_VALUE
            ? plus(Long.MAX_VALUE, unit).plus(1, unit)
            : plus(-amountToSubtract, unit);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        if (!(endExclusive instanceof LocalTime)) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only LocalTime endpoints are modeled");
        }
        if (!(unit instanceof ChronoUnit)) {
            throw fail("bmc4j: unmodelled member java.time.LocalTime.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only ChronoUnit is dispatched by this nano-of-day model");
        }
        long nanos = ((LocalTime) endExclusive).nanoOfDay - this.nanoOfDay;
        switch ((ChronoUnit) unit) {
            case NANOS:
                return nanos;
            case MICROS:
                return nanos / 1_000L;
            case MILLIS:
                return nanos / 1_000_000L;
            case SECONDS:
                return nanos / NANOS_PER_SECOND;
            case MINUTES:
                return nanos / NANOS_PER_MINUTE;
            case HOURS:
                return nanos / NANOS_PER_HOUR;
            case HALF_DAYS:
                return nanos / (12L * NANOS_PER_HOUR);
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalTime.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — the date-based unit " + unit + " is not supported by the time-only nano-of-day model");
        }
    }

    // --- genuinely-unmodelable instance surface: zone projection, text format, sub-precision truncation
    //     and the open-ended TemporalAmount/Adjuster/Query plumbing. Each is a LOUD stub. ---

    @BmcUnmodelable(reason = "pairing a time with an offset builds an OffsetTime; the offset-only zone model deliberately omits the OffsetTime view")
    public OffsetTime atOffset(ZoneOffset offset) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.atOffset(java.time.ZoneOffset) — pairing a time with an offset builds an OffsetTime; the offset-only zone model deliberately omits the OffsetTime view");
    }

    @BmcUnmodelable(reason = "formatter-driven time text rendering routes through DateTimeFormatter (dtoa/locale) — out of scope for a bounded model")
    public String format(DateTimeFormatter formatter) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.format(java.time.format.DateTimeFormatter) — formatter-driven time text rendering routes through DateTimeFormatter (dtoa/locale) — out of scope for a bounded model");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalUnit truncation surface is out of scope; use the typed with*/plus* on the nano-of-day backing")
    public LocalTime truncatedTo(TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.truncatedTo(java.time.temporal.TemporalUnit) — the open-ended TemporalUnit truncation surface is out of scope; use the typed with*/plus* on the nano-of-day backing");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed with*/plus* on the nano-of-day backing")
    public LocalTime with(TemporalAdjuster adjuster) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.with(java.time.temporal.TemporalAdjuster) — the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed with*/plus* on the nano-of-day backing");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plusHours/plusMinutes/plusSeconds/plusNanos or plus(long,TemporalUnit)")
    public LocalTime plus(TemporalAmount amountToAdd) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.plus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plusHours/plusMinutes/plusSeconds/plusNanos or plus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minusHours/minusMinutes/minusSeconds/minusNanos or minus(long,TemporalUnit)")
    public LocalTime minus(TemporalAmount amountToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.minus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minusHours/minusMinutes/minusSeconds/minusNanos or minus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor")
    public <R> R query(TemporalQuery<R> query) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.query(java.time.temporal.TemporalQuery) — the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor");
    }

    @BmcUnmodelable(reason = "adjusting an arbitrary Temporal with this time's NANO_OF_DAY needs that Temporal's unmodeled field surface")
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.adjustInto(java.time.temporal.Temporal) — adjusting an arbitrary Temporal with this time's NANO_OF_DAY needs that Temporal's unmodeled field surface");
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        return (o instanceof LocalTime) && ((LocalTime) o).nanoOfDay == this.nanoOfDay;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return (int) (nanoOfDay ^ (nanoOfDay >>> 32));
    }
}
