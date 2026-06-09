package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.IsoEra;
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
 * JBMC model of {@link java.time.LocalDate} as an epoch-day {@code long}. Ordering
 * and day arithmetic are exact. Calendar fields (year/month/day) and calendar-month
 * arithmetic (plusMonths/plusYears + minus) are modeled by decoding the epoch-day to a
 * proleptic-Gregorian y/m/d, applying the JDK's exact month-carry + day-of-month CLAMP
 * rule ("resolvePreviousValid": 2024-01-31 plusMonths 1 -> 2024-02-29), and recomposing.
 * The y/m/d <-> epoch-day machinery mirrors the JDK bit-for-bit (validated by the
 * differential suite vs the real JDK). {@code of(y, m, d)} is NOT a factory here; build
 * dates via {@link #ofEpochDay} (or via LocalDateTime). Formatters/zones are out of scope.
 *
 * <p>The whole real {@code LocalDate} surface is now accounted per-member: the modeled epoch-day core
 * (including the calendar-enum accessors {@code getDayOfWeek}/{@code getMonth}/{@code getEra} over the
 * now-modeled DayOfWeek/Month/IsoEra enums, the {@code of(int, Month, int)} factory and the
 * {@code toEpochSecond(LocalTime, ZoneOffset)} conversion) plus a LOUD {@link BmcUnmodelable} stub for
 * every genuinely-unmodelable member (zone projection, text format/parse, external clock, the Stream
 * {@code datesUntil} surface, and the open-ended TemporalAmount/Adjuster/Query plumbing). There is NO
 * class-level {@code @BmcModelTail}: nothing falls through.
 */
public final class LocalDate implements ChronoLocalDate {

    // DAYS from year 0000-01-01 (proleptic) to 1970-01-01.
    private static final long DAYS_0000_TO_1970 = (146097L * 5L) - (30L * 365L + 7L);

    // Public range constants + the epoch, mirroring the JDK's LocalDate.MIN/MAX/EPOCH. Backed by the
    // exact epoch-day of each (MIN = -999999999-01-01, MAX = +999999999-12-31, EPOCH = 1970-01-01),
    // built through the raw epoch-day constructor so the static init never runs the wide-constant
    // toEpochDay decode. The audit gate/docs track methods, not fields, so these carry no @Bmc*
    // annotation; the differential suite pins their epoch-days bit-for-bit against the JDK.
    public static final LocalDate MIN = new LocalDate(-365_243_219_162L);
    public static final LocalDate MAX = new LocalDate(365_241_780_471L);
    public static final LocalDate EPOCH = new LocalDate(0L);

    final long epochDay;

    private LocalDate(long epochDay) {
        this.epochDay = epochDay;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalDate ofEpochDay(long epochDay) {
        return new LocalDate(epochDay);
    }

    /**
     * Build a date from year/month/day, validating each field LOUDLY like the JDK
     * ({@link DateTimeException} for an out-of-range year/month or a day past that month's length, incl.
     * the Feb-29 leap rule) then recomposing through the exact {@link #toEpochDay} machinery. Unlike the
     * with-field / plus-field CLAMP path, {@code of} is STRICT: it rejects an invalid day rather than
     * shifting it.
     * Validated on the differential axis (TimeConformanceTest): the toEpochDay decode divides/mods by
     * the wide proleptic-Gregorian constants (the constant-divisor SAT-pathology), so no @BmcProof.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDate of(int year, int month, int dayOfMonth) {
        if (year < -999_999_999 || year > 999_999_999) {
            throw new DateTimeException("Invalid value for Year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new DateTimeException("Invalid value for MonthOfYear: " + month);
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new DateTimeException("Invalid value for DayOfMonth: " + dayOfMonth);
        }
        if (dayOfMonth > lengthOfMonth(year, month)) {
            throw new DateTimeException("Invalid date '" + month + " " + dayOfMonth + "'");
        }
        return new LocalDate(toEpochDay(year, month, dayOfMonth));
    }

    /** Build from year + the modeled {@link Month} enum + day, delegating to the int {@code of}. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDate of(int year, Month month, int dayOfMonth) {
        return of(year, month.getValue(), dayOfMonth);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toEpochDay() {
        return epochDay;
    }

    /**
     * Build a date from a year and a 1-based day-of-year, exactly like the JDK: validate the year and
     * day ranges loudly ({@link DateTimeException}), reject day 366 in a non-leap year, then decompose
     * the day-of-year into (month, day) and recompose through {@link #toEpochDay}. Mirrors the JDK's
     * {@code LocalDate.ofYearDay} month-table walk.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDate ofYearDay(int year, int dayOfYear) {
        if (year < -999_999_999 || year > 999_999_999) {
            throw new DateTimeException("Invalid value for Year: " + year);
        }
        if (dayOfYear < 1 || dayOfYear > 366) {
            throw new DateTimeException("Invalid value for DayOfYear: " + dayOfYear);
        }
        boolean leap = isLeapYear(year);
        if (dayOfYear == 366 && !leap) {
            throw new DateTimeException("Invalid value for DayOfYear (not a leap year): 366");
        }
        // Find the month whose cumulative day count first reaches dayOfYear (JDK's Month.of walk).
        int month = 1;
        int remaining = dayOfYear;
        while (true) {
            int len = lengthOfMonth(year, month);
            if (remaining <= len) {
                break;
            }
            remaining -= len;
            month++;
        }
        return new LocalDate(toEpochDay(year, month, remaining));
    }

    // isBefore/isAfter/isEqual/compareTo take ChronoLocalDate, mirroring the real signatures so JDK-
    // compiled proof bytecode (which checkcasts the arg to ChronoLocalDate and resolves the interface-
    // typed overload) finds the model body. The epoch-day model only compares LocalDate endpoints; the
    // arg is cast back to the LocalDate model (now an instanceof ChronoLocalDate). A non-LocalDate
    // ChronoLocalDate (another chronology) is out of scope — declined LOUD, never a wrong answer.
    private long otherEpochDay(ChronoLocalDate other) {
        if (!(other instanceof LocalDate)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate comparison against a non-ISO ChronoLocalDate — only LocalDate endpoints are modeled");
        }
        return ((LocalDate) other).epochDay;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isBefore(ChronoLocalDate other) {
        return this.epochDay < otherEpochDay(other);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isAfter(ChronoLocalDate other) {
        return this.epochDay > otherEpochDay(other);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isEqual(ChronoLocalDate other) {
        return this.epochDay == otherEpochDay(other);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public int compareTo(ChronoLocalDate other) {
        long o = otherEpochDay(other);
        return this.epochDay < o ? -1 : (this.epochDay == o ? 0 : 1);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate plusDays(long days) {
        return new LocalDate(this.epochDay + days);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate minusDays(long days) {
        return new LocalDate(this.epochDay - days);
    }

    // A week is exactly 7 epoch-days; route the *7 through a checked multiply so a week count past the
    // long/7 bound fails LOUDLY (the JDK uses Math.multiplyExact too) rather than silently wrapping.
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate plusWeeks(long weeksToAdd) {
        return new LocalDate(this.epochDay + Math.multiplyExact(weeksToAdd, 7L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate minusWeeks(long weeksToSubtract) {
        return new LocalDate(this.epochDay - Math.multiplyExact(weeksToSubtract, 7L));
    }

    // --- calendar fields: decode epoch-day to proleptic-Gregorian y/m/d (mirrors the JDK ofEpochDay) ---

    private static boolean isLeapYear(long year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    private static int lengthOfMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /** {year, month, day}, decoded from the epoch-day exactly like the JDK's LocalDate.ofEpochDay. */
    private int[] ymd() {
        long zeroDay = epochDay + DAYS_0000_TO_1970;
        zeroDay -= 60;  // adjust to 0000-03-01 so leap day is end of four-year cycle
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / 146097 - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * 146097;
        }
        long yearEst = (400 * zeroDay + 591) / 146097;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;
        int marchDoy0 = (int) doyEst;
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        yearEst += marchMonth0 / 10;
        return new int[]{(int) yearEst, month, dom};
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getYear() {
        return ymd()[0];
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getMonthValue() {
        return ymd()[1];
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getDayOfMonth() {
        return ymd()[2];
    }

    /**
     * 1-based day-of-year = (epoch-day for this date) - (epoch-day for Jan 1 of this year) + 1. Reusing
     * the exact toEpochDay machinery keeps it bit-identical to the JDK across leap years and negatives.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int getDayOfYear() {
        int[] f = ymd();
        return (int) (epochDay - toEpochDay(f[0], 1, 1) + 1);
    }

    /** Day-of-week as the modeled {@link DayOfWeek} enum, decoded from the epoch-day (Mon=1..Sun=7). */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(dayOfWeekValue());
    }

    /** Month as the modeled {@link Month} enum (Jan=1..Dec=12), decoded from the epoch-day. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public Month getMonth() {
        return Month.of(getMonthValue());
    }

    /** ISO era as the modeled {@link IsoEra} enum: CE for year &gt;= 1, else BCE — exactly like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public IsoEra getEra() {
        return ymd()[0] >= 1 ? IsoEra.CE : IsoEra.BCE;
    }

    /**
     * Epoch-second of this date at the given time and offset — pure integer arithmetic over the modeled
     * pieces (this date's epoch-day, the time's second-of-day, the offset's total seconds), no zone DB.
     * {@code epochDay*86400 + time.toSecondOfDay() - offset.getTotalSeconds()}, exactly like the JDK;
     * loud {@code Math.*Exact} overflow.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public long toEpochSecond(LocalTime time, ZoneOffset offset) {
        long secs = epochDay * 86400L + time.toSecondOfDay();
        return Math.subtractExact(secs, offset.getTotalSeconds());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public int lengthOfMonth() {
        int[] f = ymd();
        return lengthOfMonth(f[0], f[1]);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public int lengthOfYear() {
        return isLeapYear(ymd()[0]) ? 366 : 365;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public boolean isLeapYear() {
        return isLeapYear(ymd()[0]);
    }

    /**
     * y/m/d to epoch-day, copied from the JDK's {@code LocalDate.toEpochDay()} proleptic-Gregorian
     * algorithm so the differential suite holds bit-for-bit. Inputs are assumed already-valid
     * calendar fields (produced by {@link #ymd()} + the clamp), so no range validation here.
     */
    private static long toEpochDay(int year, int month, int dayOfMonth) {
        long y = year;
        long m = month;
        long total = 0;
        total += 365 * y;
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        } else {
            total -= y / -4 - y / -100 + y / -400;
        }
        total += ((367 * m - 362) / 12);
        total += (dayOfMonth - 1);
        if (m > 2) {
            total--;
            if (!isLeapYear(year)) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    /**
     * The JDK's {@code resolvePreviousValid}: build the date (year, month, day) but clamp the
     * day down to the target month's length (Feb 31 -> Feb 28/29, Apr 31 -> Apr 30). This is the
     * exact rule behind 2024-01-31 plusMonths 1 == 2024-02-29 and 2024-02-29 plusYears 1 ==
     * 2025-02-28.
     */
    private static LocalDate resolvePreviousValid(int year, int month, int day) {
        int last = lengthOfMonth(year, month);
        int d = day < last ? day : last;
        return new LocalDate(toEpochDay(year, month, d));
    }

    // --- calendar-month arithmetic: month-carry + day clamp, matching the JDK exactly ---
    //
    // The JDK uses Math.floorDiv/floorMod on (year*12 + month-1 + monthsToAdd) to find the new
    // year/month. floorDiv/floorMod are UNMODELED by JBMC, so we inline floor division by 12 with
    // explicit sign handling (12 > 0): floorDiv(a,12) = a>=0 ? a/12 : -((-a + 11)/12), and
    // floorMod(a,12) = a - 12*floorDiv(a,12), which is always in [0,11]. This keeps the proof axis
    // usable and is bit-identical to the JDK (proven by the differential suite).

    private static long floorDiv12(long a) {
        if (a >= 0) {
            return a / 12;
        }
        return -((-a + 11) / 12);
    }

    private static int floorMod12(long a) {
        return (int) (a - 12L * floorDiv12(a));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        int[] f = ymd();
        int year = f[0], month = f[1], day = f[2];
        long monthCount = year * 12L + (month - 1);
        long calcMonths = monthCount + monthsToAdd;
        int newYear = (int) floorDiv12(calcMonths);
        int newMonth = floorMod12(calcMonths) + 1;
        return resolvePreviousValid(newYear, newMonth, day);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        int[] f = ymd();
        int year = f[0], month = f[1], day = f[2];
        int newYear = (int) (year + yearsToAdd);
        return resolvePreviousValid(newYear, month, day);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDate minusYears(long yearsToSubtract) {
        return plusYears(-yearsToSubtract);
    }

    // --- with* field setters: the JDK CLAMPs the day for withYear/withMonth (resolvePreviousValid)
    //     but validates STRICTLY for withDayOfMonth/withDayOfYear (loud DateTimeException on a bad day).

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDate withYear(int year) {
        int[] f = ymd();
        if (f[0] == year) {
            return this;
        }
        if (year < -999_999_999 || year > 999_999_999) {
            throw new DateTimeException("Invalid value for Year: " + year);
        }
        return resolvePreviousValid(year, f[1], f[2]);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDate withMonth(int month) {
        int[] f = ymd();
        if (f[1] == month) {
            return this;
        }
        if (month < 1 || month > 12) {
            throw new DateTimeException("Invalid value for MonthOfYear: " + month);
        }
        return resolvePreviousValid(f[0], month, f[2]);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDate withDayOfMonth(int dayOfMonth) {
        int[] f = ymd();
        if (f[2] == dayOfMonth) {
            return this;
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new DateTimeException("Invalid value for DayOfMonth: " + dayOfMonth);
        }
        if (dayOfMonth > lengthOfMonth(f[0], f[1])) {
            throw new DateTimeException("Invalid date '" + f[1] + " " + dayOfMonth + "'");
        }
        return new LocalDate(toEpochDay(f[0], f[1], dayOfMonth));
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDate withDayOfYear(int dayOfYear) {
        if (getDayOfYear() == dayOfYear) {
            return this;
        }
        return ofYearDay(ymd()[0], dayOfYear);
    }

    // --- composition with the time + date+time models (atTime/atStartOfDay), and until -> Period ---

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime atStartOfDay() {
        return LocalDateTime.of(this, LocalTime.ofNanoOfDay(0));
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime atTime(LocalTime time) {
        return LocalDateTime.of(this, time);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime atTime(int hour, int minute) {
        return LocalDateTime.of(this, LocalTime.of(hour, minute));
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime atTime(int hour, int minute, int second) {
        return LocalDateTime.of(this, LocalTime.of(hour, minute, second));
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime atTime(int hour, int minute, int second, int nanoOfSecond) {
        return LocalDateTime.of(this, LocalTime.of(hour, minute, second, nanoOfSecond));
    }

    /**
     * Period from this date to {@code endExclusive}, delegating to the JDK-faithful Period.between. The
     * real signature is {@code until(ChronoLocalDate)} returning {@code Period} (covariant over
     * ChronoPeriod), which Period now implements; the arg is cast back to the LocalDate model.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public Period until(ChronoLocalDate endExclusive) {
        if (!(endExclusive instanceof LocalDate)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.until(java.time.chrono.ChronoLocalDate) — only LocalDate endpoints are modeled");
        }
        return Period.between(this, (LocalDate) endExclusive);
    }

    // --- helpers for Period.between (mirror the JDK's LocalDate.until decomposition) ---

    /** Proleptic month count = year*12 + (month-1), as the JDK uses in until(). */
    long getProlepticMonth() {
        int[] f = ymd();
        return f[0] * 12L + (f[1] - 1);
    }

    int getLengthOfMonth() {
        int[] f = ymd();
        return lengthOfMonth(f[0], f[1]);
    }

    // --- generic TemporalField / TemporalUnit accessors: dispatch on the (now-modeled) ChronoField /
    //     ChronoUnit and read/recompose the epoch-day backing. A non-Chrono field/unit, or one this
    //     date-only model can't carry, is declined LOUD — a NAMED UNKNOWN, never a wrong value. The
    //     ChronoLocalDate interface makes a LocalDate an instanceof it so the proof-site checkcast passes;
    //     lengthOfMonth() and until(ChronoLocalDate) above already satisfy the interface too. ---

    /** Non-negative remainder of {@code (epochDay + 3)} mod 7 — Math.floorMod-free (JBMC-sound). */
    private int dayOfWeekValue() {
        return (int) (((epochDay + 3) % 7 + 7) % 7) + 1;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalField field) {
        return (field instanceof ChronoField) && ((ChronoField) field).isDateBased();
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            return ((ChronoUnit) unit).isDateBased();
        }
        return unit != null && unit.isSupportedBy(this);
    }

    /**
     * The {@code long} value of a date-based {@link ChronoField}, decoded from the epoch-day exactly like
     * the JDK. The whole supported date-field set is covered (weekday/aligned/month/year/era/proleptic);
     * a non-ChronoField or a time-based field is declined LOUD ({@link UnsupportedTemporalTypeException}-
     * shaped via the loud sentinel) — never a silent value.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long getLong(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.getLong(java.time.temporal.TemporalField) — only ChronoField is dispatched by this epoch-day model");
        }
        int[] f = ymd();
        int year = f[0], month = f[1], dom = f[2];
        switch ((ChronoField) field) {
            case DAY_OF_WEEK:
                return dayOfWeekValue();
            case ALIGNED_DAY_OF_WEEK_IN_MONTH:
                return (dom - 1) % 7 + 1;
            case ALIGNED_DAY_OF_WEEK_IN_YEAR:
                return (getDayOfYear() - 1) % 7 + 1;
            case DAY_OF_MONTH:
                return dom;
            case DAY_OF_YEAR:
                return getDayOfYear();
            case EPOCH_DAY:
                return epochDay;
            case ALIGNED_WEEK_OF_MONTH:
                return (dom - 1) / 7 + 1;
            case ALIGNED_WEEK_OF_YEAR:
                return (getDayOfYear() - 1) / 7 + 1;
            case MONTH_OF_YEAR:
                return month;
            case PROLEPTIC_MONTH:
                return year * 12L + (month - 1);
            case YEAR_OF_ERA:
                return year < 1 ? 1L - year : year;
            case YEAR:
                return year;
            case ERA:
                return year < 1 ? 0 : 1;
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalDate.getLong(java.time.temporal.TemporalField) — the time-based field " + field + " is not supported by the date-only epoch-day model");
        }
    }

    /**
     * The {@code int} value of a date-based field — the JDK's {@code get} (range-checked {@code getLong},
     * loud on a field whose range exceeds int, e.g. EPOCH_DAY/PROLEPTIC_MONTH). Delegates to the modeled
     * {@link #getLong} and the field's own {@link ValueRange#checkValidIntValue}.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public int get(TemporalField field) {
        return range(field).checkValidIntValue(getLong(field), field);
    }

    /**
     * The valid-value range of a date-based field FOR THIS DATE — the JDK refines DAY_OF_MONTH to this
     * month's length, DAY_OF_YEAR to this year's length, ALIGNED_WEEK_OF_MONTH to this month, and
     * YEAR_OF_ERA to the era's max; every other date field uses the field's constant range.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange range(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.range(java.time.temporal.TemporalField) — only ChronoField is dispatched by this epoch-day model");
        }
        ChronoField cf = (ChronoField) field;
        if (!cf.isDateBased()) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.range(java.time.temporal.TemporalField) — the time-based field " + field + " is not supported by the date-only epoch-day model");
        }
        int[] f = ymd();
        switch (cf) {
            case DAY_OF_MONTH:
                return ValueRange.of(1, lengthOfMonth(f[0], f[1]));
            case DAY_OF_YEAR:
                return ValueRange.of(1, isLeapYear(f[0]) ? 366 : 365);
            case ALIGNED_WEEK_OF_MONTH:
                return ValueRange.of(1, lengthOfMonth(f[0], f[1]) == 28 ? 4 : 5);
            case YEAR_OF_ERA:
                return f[0] <= 0 ? ValueRange.of(1, 1_000_000_000L) : ValueRange.of(1, 999_999_999L);
            default:
                return cf.range();
        }
    }

    /**
     * A copy of this date with a date-based field set, dispatching to the typed {@code withYear/withMonth/
     * withDayOf*} setters (whose clamp/strict-validation already mirror the JDK). EPOCH_DAY/PROLEPTIC_MONTH/
     * DAY_OF_WEEK and the aligned-week fields recompose through the exact day/month arithmetic. The field's
     * value is range-checked LOUDLY first ({@link ChronoField#checkValidValue}); an unsupported field is
     * declined LOUD.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public LocalDate with(TemporalField field, long newValue) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.with(java.time.temporal.TemporalField,long) — only ChronoField is dispatched by this epoch-day model");
        }
        ChronoField cf = (ChronoField) field;
        switch (cf) {
            case EPOCH_DAY:
                return new LocalDate(newValue);
            case DAY_OF_MONTH:
                return withDayOfMonth((int) cf.checkValidValue(newValue));
            case DAY_OF_YEAR:
                return withDayOfYear((int) cf.checkValidValue(newValue));
            case MONTH_OF_YEAR:
                return withMonth((int) cf.checkValidValue(newValue));
            case YEAR:
                return withYear((int) cf.checkValidValue(newValue));
            case DAY_OF_WEEK:
                return plusDays(cf.checkValidValue(newValue) - dayOfWeekValue());
            case ALIGNED_DAY_OF_WEEK_IN_MONTH:
                return plusDays(cf.checkValidValue(newValue) - getLong(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
            case ALIGNED_DAY_OF_WEEK_IN_YEAR:
                return plusDays(cf.checkValidValue(newValue) - getLong(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
            case PROLEPTIC_MONTH:
                return plusMonths(cf.checkValidValue(newValue) - getProlepticMonth());
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalDate.with(java.time.temporal.TemporalField,long) — the field " + field + " (era flip / aligned-week recompose) is out of scope for this epoch-day model");
        }
    }

    /** A copy with {@code amountToAdd} of {@code unit} added — dispatch on ChronoUnit to the typed adders. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public LocalDate plus(long amountToAdd, TemporalUnit unit) {
        if (!(unit instanceof ChronoUnit)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.plus(long,java.time.temporal.TemporalUnit) — only ChronoUnit is dispatched by this epoch-day model");
        }
        switch ((ChronoUnit) unit) {
            case DAYS:
                return plusDays(amountToAdd);
            case WEEKS:
                return plusWeeks(amountToAdd);
            case MONTHS:
                return plusMonths(amountToAdd);
            case YEARS:
                return plusYears(amountToAdd);
            case DECADES:
                return plusYears(Math.multiplyExact(amountToAdd, 10));
            case CENTURIES:
                return plusYears(Math.multiplyExact(amountToAdd, 100));
            case MILLENNIA:
                return plusYears(Math.multiplyExact(amountToAdd, 1000));
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalDate.plus(long,java.time.temporal.TemporalUnit) — the unit " + unit + " (ERAS / sub-day units) is not supported by the date-only epoch-day model");
        }
    }

    /** A copy with {@code amountToSubtract} of {@code unit} removed — negate and reuse {@link #plus}. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public LocalDate minus(long amountToSubtract, TemporalUnit unit) {
        return amountToSubtract == Long.MIN_VALUE
            ? plus(Long.MAX_VALUE, unit).plus(1, unit)
            : plus(-amountToSubtract, unit);
    }

    /**
     * The amount of time from this date to {@code endExclusive} in {@code unit}, for the date-based units —
     * matching the JDK's day-count then unit scaling (months via the proleptic-month difference, weeks via
     * the day count / 7, years/decades/centuries/millennia via the truncating month/12 split). The end
     * must be a modeled LocalDate; an unsupported unit is declined LOUD.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        if (!(endExclusive instanceof LocalDate)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only LocalDate endpoints are modeled");
        }
        if (!(unit instanceof ChronoUnit)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDate.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only ChronoUnit is dispatched by this epoch-day model");
        }
        LocalDate end = (LocalDate) endExclusive;
        long days = end.epochDay - this.epochDay;
        switch ((ChronoUnit) unit) {
            case DAYS:
                return days;
            case WEEKS:
                return days / 7;
            case MONTHS:
                return monthsUntil(end);
            case YEARS:
                return monthsUntil(end) / 12;
            case DECADES:
                return monthsUntil(end) / 120;
            case CENTURIES:
                return monthsUntil(end) / 1200;
            case MILLENNIA:
                return monthsUntil(end) / 12000;
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalDate.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — the unit " + unit + " (ERAS / sub-day units) is not supported by the date-only epoch-day model");
        }
    }

    /** Whole-month difference exactly as the JDK's {@code monthsUntil}: proleptic-month delta, then a
     *  -1 carry when the end's day-of-month is still short of this one's (the partial-month adjustment). */
    private long monthsUntil(LocalDate end) {
        long packed1 = this.getProlepticMonth() * 32L + this.getDayOfMonth();
        long packed2 = end.getProlepticMonth() * 32L + end.getDayOfMonth();
        return (packed2 - packed1) / 32;
    }

    @BmcUnmodelable(reason = "the Chronology accessor (getChronology) is out of scope for this epoch-day model")
    @Override
    public Chronology getChronology() {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.getChronology() — the Chronology accessor is out of scope for this epoch-day model");
    }

    // --- genuinely-unmodelable surface: external clock, zone projection, text format/parse, the Stream
    //     datesUntil view and the open-ended TemporalAmount/Adjuster/Query plumbing. Each is a LOUD stub. ---

    @BmcUnmodelable(reason = "wall-clock read is non-deterministic external state — pass LocalDates as symbolic proof parameters")
    public static LocalDate now() {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.now() — wall-clock read is non-deterministic external state — pass LocalDates as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "a Clock is non-deterministic external state — pass LocalDates as symbolic proof parameters")
    public static LocalDate now(Clock clock) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.now(java.time.Clock) — a Clock is non-deterministic external state — pass LocalDates as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "the current date in a named zone is non-deterministic external state plus zone-rules projection — pass LocalDates as symbolic proof parameters")
    public static LocalDate now(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.now(java.time.ZoneId) — the current date in a named zone is non-deterministic external state plus zone-rules projection — pass LocalDates as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "deriving the local date of an Instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits")
    public static LocalDate ofInstant(Instant instant, ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.ofInstant(java.time.Instant,java.time.ZoneId) — deriving the local date of an Instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits");
    }

    @BmcUnmodelable(reason = "ISO-8601 date text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)")
    public static LocalDate parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.parse(java.lang.CharSequence) — ISO-8601 date text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)");
    }

    @BmcUnmodelable(reason = "formatter-driven date text parsing — out of scope for a bounded model (no text parsing/locale)")
    public static LocalDate parse(CharSequence text, DateTimeFormatter formatter) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.parse(java.lang.CharSequence,java.time.format.DateTimeFormatter) — formatter-driven date text parsing — out of scope for a bounded model (no text parsing/locale)");
    }

    @BmcUnmodelable(reason = "extracting a LocalDate from an arbitrary TemporalAccessor needs its open-ended field surface; build via LocalDate.of/ofEpochDay")
    public static LocalDate from(TemporalAccessor temporal) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.from(java.time.temporal.TemporalAccessor) — extracting a LocalDate from an arbitrary TemporalAccessor needs its open-ended field surface; build via LocalDate.of/ofEpochDay");
    }

    @BmcUnmodelable(reason = "the start-of-day instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits")
    public ZonedDateTime atStartOfDay(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.atStartOfDay(java.time.ZoneId) — the start-of-day instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits");
    }

    @BmcUnmodelable(reason = "pairing a date with an OffsetTime builds an OffsetDateTime the epoch-day+nano model doesn't carry")
    public OffsetDateTime atTime(OffsetTime time) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.atTime(java.time.OffsetTime) — pairing a date with an OffsetTime builds an OffsetDateTime the epoch-day+nano model doesn't carry");
    }

    @BmcUnmodelable(reason = "datesUntil returns a Stream<LocalDate>; the unbounded lazy-stream surface is out of scope for a bounded model")
    public java.util.stream.Stream<LocalDate> datesUntil(LocalDate endExclusive) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.datesUntil(java.time.LocalDate) — datesUntil returns a Stream<LocalDate>; the unbounded lazy-stream surface is out of scope for a bounded model");
    }

    @BmcUnmodelable(reason = "datesUntil returns a Stream<LocalDate>; the unbounded lazy-stream surface is out of scope for a bounded model")
    public java.util.stream.Stream<LocalDate> datesUntil(LocalDate endExclusive, Period step) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.datesUntil(java.time.LocalDate,java.time.Period) — datesUntil returns a Stream<LocalDate>; the unbounded lazy-stream surface is out of scope for a bounded model");
    }

    @BmcUnmodelable(reason = "formatter-driven date text rendering routes through DateTimeFormatter (locale) — out of scope for a bounded model")
    @Override
    public String format(DateTimeFormatter formatter) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.format(java.time.format.DateTimeFormatter) — formatter-driven date text rendering routes through DateTimeFormatter (locale) — out of scope for a bounded model");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed with*/plus* on the epoch-day backing")
    public LocalDate with(TemporalAdjuster adjuster) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.with(java.time.temporal.TemporalAdjuster) — the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed with*/plus* on the epoch-day backing");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plusDays/plusMonths/plusYears or plus(long,TemporalUnit)")
    public LocalDate plus(TemporalAmount amountToAdd) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.plus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plusDays/plusMonths/plusYears or plus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minusDays/minusMonths/minusYears or minus(long,TemporalUnit)")
    public LocalDate minus(TemporalAmount amountToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.minus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minusDays/minusMonths/minusYears or minus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor")
    public <R> R query(TemporalQuery<R> query) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.query(java.time.temporal.TemporalQuery) — the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor");
    }

    @BmcUnmodelable(reason = "adjusting an arbitrary Temporal with this date's EPOCH_DAY needs that Temporal's unmodeled field surface")
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.adjustInto(java.time.temporal.Temporal) — adjusting an arbitrary Temporal with this date's EPOCH_DAY needs that Temporal's unmodeled field surface");
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        return (o instanceof LocalDate) && ((LocalDate) o).epochDay == this.epochDay;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return (int) (epochDay ^ (epochDay >>> 32));
    }
}
