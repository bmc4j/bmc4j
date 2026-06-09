package java.time.temporal;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.temporal.ChronoField} — the standard set of date/time fields, each
 * carrying its base/range {@link ChronoUnit} and its valid-value {@link ValueRange} (the exact bounds the
 * real JDK constants carry, mirrored bit-for-bit). The modeled surface is the field metadata
 * ({@code range}/{@code getBaseUnit}/{@code getRangeUnit}/{@code isDateBased}/{@code isTimeBased}), the
 * loud {@code checkValidValue}/{@code checkValidIntValue} bound checks, and the {@code getFrom}/
 * {@code isSupportedBy}/{@code adjustInto}/{@code rangeRefinedBy} plumbing — which the JDK defines purely
 * by DELEGATING to the temporal ({@code temporal.getLong(this)} / {@code temporal.isSupported(this)} /
 * {@code temporal.with(this, value)} / {@code temporal.range(this)}); this model dispatches to the
 * (now-modeled) temporal accessors the same way, so a field read is exactly as sound as the temporal's
 * own modeled {@code getLong}/{@code with}/{@code range}. The metadata is validated bit-for-bit by the
 * differential suite vs the real JDK.
 *
 * <p>NB: NO class-level {@code @BmcModelTail} (the enum-tail lesson, like {@link java.time.DayOfWeek}):
 * the tail loud-body synthesis would override {@code java.lang.Enum}'s FINAL members + the synthetic
 * {@code values}/{@code valueOf} and break the enum. This is a class-level COVERED enum; the required
 * {@code TemporalField} abstracts are the per-member-annotated bodies below.
 *
 * <p>{@code getDisplayName}/{@code resolve} (locale text / parse-resolution) are out of scope — declined
 * LOUD, a NAMED UNKNOWN if reached.
 */
public enum ChronoField implements TemporalField {

    NANO_OF_SECOND(ChronoUnit.NANOS, ChronoUnit.SECONDS, ValueRange.of(0, 999_999_999), false, true),
    NANO_OF_DAY(ChronoUnit.NANOS, ChronoUnit.DAYS, ValueRange.of(0, 86_400L * 1_000_000_000L - 1L), false, true),
    MICRO_OF_SECOND(ChronoUnit.MICROS, ChronoUnit.SECONDS, ValueRange.of(0, 999_999), false, true),
    MICRO_OF_DAY(ChronoUnit.MICROS, ChronoUnit.DAYS, ValueRange.of(0, 86_400L * 1_000_000L - 1L), false, true),
    MILLI_OF_SECOND(ChronoUnit.MILLIS, ChronoUnit.SECONDS, ValueRange.of(0, 999), false, true),
    MILLI_OF_DAY(ChronoUnit.MILLIS, ChronoUnit.DAYS, ValueRange.of(0, 86_400L * 1_000L - 1L), false, true),
    SECOND_OF_MINUTE(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ValueRange.of(0, 59), false, true),
    SECOND_OF_DAY(ChronoUnit.SECONDS, ChronoUnit.DAYS, ValueRange.of(0, 86_399), false, true),
    MINUTE_OF_HOUR(ChronoUnit.MINUTES, ChronoUnit.HOURS, ValueRange.of(0, 59), false, true),
    MINUTE_OF_DAY(ChronoUnit.MINUTES, ChronoUnit.DAYS, ValueRange.of(0, 1439), false, true),
    HOUR_OF_AMPM(ChronoUnit.HOURS, ChronoUnit.HALF_DAYS, ValueRange.of(0, 11), false, true),
    CLOCK_HOUR_OF_AMPM(ChronoUnit.HOURS, ChronoUnit.HALF_DAYS, ValueRange.of(1, 12), false, true),
    HOUR_OF_DAY(ChronoUnit.HOURS, ChronoUnit.DAYS, ValueRange.of(0, 23), false, true),
    CLOCK_HOUR_OF_DAY(ChronoUnit.HOURS, ChronoUnit.DAYS, ValueRange.of(1, 24), false, true),
    AMPM_OF_DAY(ChronoUnit.HALF_DAYS, ChronoUnit.DAYS, ValueRange.of(0, 1), false, true),
    DAY_OF_WEEK(ChronoUnit.DAYS, ChronoUnit.WEEKS, ValueRange.of(1, 7), true, false),
    ALIGNED_DAY_OF_WEEK_IN_MONTH(ChronoUnit.DAYS, ChronoUnit.WEEKS, ValueRange.of(1, 7), true, false),
    ALIGNED_DAY_OF_WEEK_IN_YEAR(ChronoUnit.DAYS, ChronoUnit.WEEKS, ValueRange.of(1, 7), true, false),
    DAY_OF_MONTH(ChronoUnit.DAYS, ChronoUnit.MONTHS, ValueRange.of(1, 28, 31), true, false),
    DAY_OF_YEAR(ChronoUnit.DAYS, ChronoUnit.YEARS, ValueRange.of(1, 365, 366), true, false),
    EPOCH_DAY(ChronoUnit.DAYS, ChronoUnit.FOREVER, ValueRange.of(-365_243_219_162L, 365_241_780_471L), true, false),
    ALIGNED_WEEK_OF_MONTH(ChronoUnit.WEEKS, ChronoUnit.MONTHS, ValueRange.of(1, 4, 5), true, false),
    ALIGNED_WEEK_OF_YEAR(ChronoUnit.WEEKS, ChronoUnit.YEARS, ValueRange.of(1, 53), true, false),
    MONTH_OF_YEAR(ChronoUnit.MONTHS, ChronoUnit.YEARS, ValueRange.of(1, 12), true, false),
    PROLEPTIC_MONTH(ChronoUnit.MONTHS, ChronoUnit.FOREVER, ValueRange.of(-999_999_999L * 12L, 999_999_999L * 12L + 11L), true, false),
    YEAR_OF_ERA(ChronoUnit.YEARS, ChronoUnit.FOREVER, ValueRange.of(1, 999_999_999, 1_000_000_000), true, false),
    YEAR(ChronoUnit.YEARS, ChronoUnit.FOREVER, ValueRange.of(-999_999_999, 999_999_999), true, false),
    ERA(ChronoUnit.ERAS, ChronoUnit.FOREVER, ValueRange.of(0, 1), true, false),
    INSTANT_SECONDS(ChronoUnit.SECONDS, ChronoUnit.FOREVER, ValueRange.of(Long.MIN_VALUE, Long.MAX_VALUE), false, false),
    OFFSET_SECONDS(ChronoUnit.SECONDS, ChronoUnit.FOREVER, ValueRange.of(-18 * 3600, 18 * 3600), false, false);

    private final TemporalUnit baseUnit;
    private final TemporalUnit rangeUnit;
    private final ValueRange range;
    private final boolean dateBased;
    private final boolean timeBased;

    ChronoField(TemporalUnit baseUnit, TemporalUnit rangeUnit, ValueRange range, boolean dateBased, boolean timeBased) {
        this.baseUnit = baseUnit;
        this.rangeUnit = rangeUnit;
        this.range = range;
        this.dateBased = dateBased;
        this.timeBased = timeBased;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public TemporalUnit getBaseUnit() {
        return baseUnit;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public TemporalUnit getRangeUnit() {
        return rangeUnit;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange range() {
        return range;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isDateBased() {
        return dateBased;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isTimeBased() {
        return timeBased;
    }

    /** Loudly reject an out-of-range value against this field's full range, like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public long checkValidValue(long value) {
        return range.checkValidValue(value, this);
    }

    /** Loudly reject a value that is out of range or not int-sized against this field, like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int checkValidIntValue(long value) {
        return range.checkValidIntValue(value, this);
    }

    // --- TemporalField plumbing: the JDK defines these purely by delegating to the temporal. ---------

    // getFrom/isSupportedBy take the non-generic TemporalAccessor: the concrete temporal erases to the
    // interface at the call site, so JBMC can't recover its dynamic type to back-dispatch getLong/
    // isSupported — it inserts a dynamic-cast check that spuriously refutes on the @BmcProof axis (the
    // interface-erased-ARGUMENT artifact). They are validated bit-for-bit on the differential axis
    // instead. (Contrast ChronoUnit.addTo, whose <R extends Temporal> generic param keeps the concrete
    // type, so addTo IS @BmcProof-clean.)
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long getFrom(TemporalAccessor temporal) {
        return temporal.getLong(this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange rangeRefinedBy(TemporalAccessor temporal) {
        return temporal.range(this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    @SuppressWarnings("unchecked")
    public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        return (R) temporal.with(this, newValue);
    }

    @BmcUnmodelable(reason = "getDisplayName is locale text — out of scope for the field-metadata model")
    @Override
    public String getDisplayName(java.util.Locale locale) {
        throw fail("bmc4j: unmodelled member java.time.temporal.ChronoField.getDisplayName(java.util.Locale) — locale display text is out of scope for the field-metadata model");
    }

    @BmcUnmodelable(reason = "resolve is parse-resolution machinery (a Map of field->value) — out of scope for the field-metadata model")
    @Override
    public TemporalAccessor resolve(java.util.Map<TemporalField, Long> fieldValues,
                                    TemporalAccessor partialTemporal,
                                    java.time.format.ResolverStyle resolverStyle) {
        throw fail("bmc4j: unmodelled member java.time.temporal.ChronoField.resolve(java.util.Map,java.time.temporal.TemporalAccessor,java.time.format.ResolverStyle) — parse-resolution is out of scope for the field-metadata model");
    }
}
