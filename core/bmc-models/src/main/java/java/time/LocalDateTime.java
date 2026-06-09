package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.chrono.ChronoLocalDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.chrono.Chronology;
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
 * JBMC model of {@link java.time.LocalDateTime} backed by an epoch-day {@code long} (the date) plus
 * a nano-of-day {@code long} (the time), so ordering and arithmetic reduce to integer arithmetic
 * JBMC reasons about precisely.
 *
 * <p>The y/m/d to epoch-day conversion mirrors the JDK's exact proleptic-Gregorian algorithm
 * (validated by the differential suite vs the real JDK). {@code of} validates fields loudly
 * ({@link DateTimeException}) like the JDK. Day/time arithmetic ({@code plusDays}/{@code plusHours}/
 * {@code plusMinutes}/{@code plusSeconds}) is exact and sound. Calendar-month arithmetic
 * ({@code plusMonths}/{@code plusYears} + minus) applies the JDK's exact month-carry + day-of-month
 * CLAMP rule to the date part (2024-01-31 plusMonths 1 -> 2024-02-29), leaving the time part
 * unchanged; it is validated bit-for-bit by the differential suite vs the real JDK. Zones, formatters
 * and sub-nano precision are out of scope; {@code now()} is not modeled.
 *
 * <p>The whole real {@code LocalDateTime} surface is now accounted per-member: the modeled
 * (epoch-day, nano-of-day) core (including {@code getDayOfWeek}/{@code getMonth} over the now-modeled
 * enums, the {@code of(..., Month, ...)} factories, and the offset-explicit
 * {@code ofEpochSecond}/{@code toEpochSecond(ZoneOffset)} conversions) plus a LOUD
 * {@link BmcUnmodelable} stub for every genuinely-unmodelable member (zone projection, the
 * millis-Instant {@code toInstant}, text format/parse, external clock, sub-precision {@code truncatedTo}
 * and the open-ended TemporalAmount/Adjuster/Query plumbing). There is NO class-level
 * {@code @BmcModelTail}: nothing falls through.
 */
public final class LocalDateTime implements ChronoLocalDateTime<LocalDate> {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60L * NANOS_PER_MINUTE;
    private static final long NANOS_PER_DAY = 24L * NANOS_PER_HOUR;

    // Public range constants, mirroring the JDK's LocalDateTime.MIN/MAX = LocalDate.MIN/MAX at
    // LocalTime.MIN/MAX. MIN = -999999999-01-01T00:00 (epoch-day -365243219162, nano-of-day 0);
    // MAX = +999999999-12-31T23:59:59.999999999 (epoch-day 365241780471, last nano of the day). The
    // audit gate/docs track methods, not fields, so these carry no @Bmc* annotation; the differential
    // suite pins their (epoch-day, nano-of-day) pair bit-for-bit against the JDK.
    public static final LocalDateTime MIN = new LocalDateTime(-365_243_219_162L, 0L);
    public static final LocalDateTime MAX = new LocalDateTime(365_241_780_471L, NANOS_PER_DAY - 1L);

    final long epochDay;     // date as days from 1970-01-01 (proleptic Gregorian)
    final long nanoOfDay;    // time within the day, [0, NANOS_PER_DAY)

    private LocalDateTime(long epochDay, long nanoOfDay) {
        this.epochDay = epochDay;
        this.nanoOfDay = nanoOfDay;
    }

    @BmcUnmodelable(reason = "wall-clock read is non-deterministic external state — pass LocalDateTimes as symbolic proof parameters")
    public static LocalDateTime now() {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.now() — wall-clock read is non-deterministic external state — pass LocalDateTimes as symbolic proof parameters");
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute) {
        return of(year, month, dayOfMonth, hour, minute, 0, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute, int second) {
        return of(year, month, dayOfMonth, hour, minute, second, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalDateTime of(int year, int month, int dayOfMonth,
                                   int hour, int minute, int second, int nanoOfSecond) {
        long ed = toEpochDay(year, month, dayOfMonth);
        long nod = timeToNanoOfDay(hour, minute, second, nanoOfSecond);
        return new LocalDateTime(ed, nod);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static LocalDateTime of(LocalDate date, LocalTime time) {
        return new LocalDateTime(date.toEpochDay(), time.toNanoOfDay());
    }

    /** Build from year + the modeled {@link Month} enum + day + time, delegating to the int {@code of}. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDateTime of(int year, Month month, int dayOfMonth, int hour, int minute) {
        return of(year, month.getValue(), dayOfMonth, hour, minute, 0, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDateTime of(int year, Month month, int dayOfMonth, int hour, int minute, int second) {
        return of(year, month.getValue(), dayOfMonth, hour, minute, second, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDateTime of(int year, Month month, int dayOfMonth,
                                   int hour, int minute, int second, int nanoOfSecond) {
        return of(year, month.getValue(), dayOfMonth, hour, minute, second, nanoOfSecond);
    }

    /**
     * Build from an epoch-second + nano-of-second at the given offset — pure integer arithmetic, no zone
     * DB (the offset is explicit). {@code localSecond = epochSecond + offset.getTotalSeconds()}, then floor
     * into (epoch-day, second-of-day) and recompose the nano-of-day, exactly like the JDK. floorDiv/floorMod
     * by 86400 are inlined with explicit sign handling (JBMC has no floorDiv).
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public static LocalDateTime ofEpochSecond(long epochSecond, int nanoOfSecond, ZoneOffset offset) {
        if (nanoOfSecond < 0 || nanoOfSecond > 999_999_999) {
            throw new DateTimeException("Invalid value for NanoOfSecond: " + nanoOfSecond);
        }
        long localSecond = Math.addExact(epochSecond, offset.getTotalSeconds());
        long localEpochDay = localSecond >= 0 ? localSecond / 86400L : -((-localSecond + 86399L) / 86400L);
        int secsOfDay = (int) (localSecond - localEpochDay * 86400L);   // floorMod, always [0, 86399]
        long nod = secsOfDay * NANOS_PER_SECOND + nanoOfSecond;
        return new LocalDateTime(localEpochDay, nod);
    }

    // --- field validation + calendar conversion, mirroring the JDK exactly ---

    private static long timeToNanoOfDay(int hour, int minute, int second, int nanoOfSecond) {
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
        return hour * NANOS_PER_HOUR
                + minute * NANOS_PER_MINUTE
                + second * NANOS_PER_SECOND
                + nanoOfSecond;
    }

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

    /**
     * y/m/d to epoch-day, copied from the JDK's {@code LocalDate.toEpochDay()} proleptic-Gregorian
     * algorithm so the differential suite holds bit-for-bit. Validates ranges loudly first.
     */
    private static long toEpochDay(int year, int month, int dayOfMonth) {
        if (year < -999_999_999 || year > 999_999_999) {
            throw new DateTimeException("Invalid value for Year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new DateTimeException("Invalid value for MonthOfYear: " + month);
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new DateTimeException("Invalid value for DayOfMonth: " + dayOfMonth);
        }
        int dom = lengthOfMonth(year, month);
        if (dayOfMonth > dom) {
            throw new DateTimeException("Invalid date '" + month + " " + dayOfMonth + "'");
        }

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
        return total - /* DAYS_0000_TO_1970 */ (146097L * 5L - (30L * 365L + 7L));
    }

    // --- accessors: decode epoch-day back to y/m/d, mirroring the JDK's ofEpochDay ---

    private int[] ymd() {
        long zeroDay = epochDay + /* DAYS_0000_TO_1970 */ (146097L * 5L - (30L * 365L + 7L));
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

    /** 1-based day-of-year of the date part — delegates to the epoch-day LocalDate model. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int getDayOfYear() {
        return LocalDate.ofEpochDay(epochDay).getDayOfYear();
    }

    /** Day-of-week of the date part as the modeled {@link DayOfWeek} enum — delegates to the LocalDate model. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public DayOfWeek getDayOfWeek() {
        return LocalDate.ofEpochDay(epochDay).getDayOfWeek();
    }

    /** Month of the date part as the modeled {@link Month} enum — Jan=1..Dec=12. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public Month getMonth() {
        return Month.of(getMonthValue());
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
    public LocalDate toLocalDate() {
        return LocalDate.ofEpochDay(epochDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalTime toLocalTime() {
        return LocalTime.ofNanoOfDay(nanoOfDay);
    }

    // --- day/time arithmetic: exact, sound (no calendar-month rule involved) ---

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime plusDays(long days) {
        return new LocalDateTime(epochDay + days, nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime minusDays(long days) {
        return new LocalDateTime(epochDay - days, nanoOfDay);
    }

    // A week is exactly 7 days; route the *7 through a checked multiply (like the JDK) so a week count
    // past the long/7 bound fails LOUDLY rather than silently wrapping.
    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime plusWeeks(long weeks) {
        return new LocalDateTime(epochDay + Math.multiplyExact(weeks, 7L), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime minusWeeks(long weeks) {
        return new LocalDateTime(epochDay - Math.multiplyExact(weeks, 7L), nanoOfDay);
    }

    // --- calendar-month arithmetic on the date part; time part (nanoOfDay) unchanged ---
    //
    // Inlines floor division by 12 with explicit sign handling (Math.floorDiv/floorMod are unmodeled
    // by JBMC). floorDiv(a,12) = a>=0 ? a/12 : -((-a+11)/12); floorMod is the [0,11] remainder. The
    // day-of-month CLAMP ("resolvePreviousValid") matches the JDK exactly (differential-verified).

    private static long floorDiv12(long a) {
        if (a >= 0) {
            return a / 12;
        }
        return -((-a + 11) / 12);
    }

    private static int floorMod12(long a) {
        return (int) (a - 12L * floorDiv12(a));
    }

    private LocalDateTime resolveDate(int year, int month, int day) {
        int last = lengthOfMonth(year, month);
        int d = day < last ? day : last;
        return new LocalDateTime(toEpochDay(year, month, d), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        int[] f = ymd();
        int year = f[0], month = f[1], day = f[2];
        long calcMonths = (year * 12L + (month - 1)) + monthsToAdd;
        int newYear = (int) floorDiv12(calcMonths);
        int newMonth = floorMod12(calcMonths) + 1;
        return resolveDate(newYear, newMonth, day);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        int[] f = ymd();
        int newYear = (int) (f[0] + yearsToAdd);
        return resolveDate(newYear, f[1], f[2]);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime minusYears(long yearsToSubtract) {
        return plusYears(-yearsToSubtract);
    }

    /**
     * Advance by {@code dayCarry} whole days plus {@code nanos} within (-DAY, DAY). The two-part
     * shape (whole-day carry kept separate from the sub-day nanos) avoids overflowing the nano
     * counter for large hour/minute/second deltas while staying exact.
     */
    private LocalDateTime plusDayCarryAndNanos(long dayCarry, long nanos) {
        long total = nanoOfDay + nanos;                 // nanos in (-DAY, DAY), nanoOfDay in [0, DAY)
        long days = Math.floorDiv(total, NANOS_PER_DAY);
        long newNod = Math.floorMod(total, NANOS_PER_DAY);
        return new LocalDateTime(epochDay + dayCarry + days, newNod);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime plusHours(long hours) {
        return plusDayCarryAndNanos(hours / 24, (hours % 24) * NANOS_PER_HOUR);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime plusMinutes(long minutes) {
        return plusDayCarryAndNanos(minutes / (24 * 60), (minutes % (24 * 60)) * NANOS_PER_MINUTE);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime plusSeconds(long seconds) {
        return plusDayCarryAndNanos(seconds / (24 * 60 * 60), (seconds % (24 * 60 * 60)) * NANOS_PER_SECOND);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime plusNanos(long nanos) {
        // Split into whole-day carry + a sub-day remainder in (-DAY, DAY) so the nano counter never
        // overflows, then reuse the exact day-carry helper. Differential-axis only: it mods by the wide
        // NANOS_PER_DAY constant (the constant-divisor SAT-pathology, the LocalTime.plusNanos precedent).
        return plusDayCarryAndNanos(nanos / NANOS_PER_DAY, nanos % NANOS_PER_DAY);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime minusNanos(long nanos) {
        // Negate WITHOUT losing the whole-day carry (so minusNanos(N*DAY) shifts the date), and without
        // -Long.MIN_VALUE overflow: negate the day-count and the sub-day remainder separately.
        return plusDayCarryAndNanos(-(nanos / NANOS_PER_DAY), -(nanos % NANOS_PER_DAY));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime minusHours(long hours) {
        return plusHours(-hours);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime minusMinutes(long minutes) {
        return plusMinutes(-minutes);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public LocalDateTime minusSeconds(long seconds) {
        return plusSeconds(-seconds);
    }

    // --- with* field setters: the date-part setters delegate to the epoch-day LocalDate model (whose
    //     withYear/withMonth CLAMP the day and withDayOfMonth/withDayOfYear validate strictly), the
    //     time-part setters to the nano-of-day LocalTime model; the other part is carried untouched.

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withYear(int year) {
        return new LocalDateTime(LocalDate.ofEpochDay(epochDay).withYear(year).toEpochDay(), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withMonth(int month) {
        return new LocalDateTime(LocalDate.ofEpochDay(epochDay).withMonth(month).toEpochDay(), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withDayOfMonth(int dayOfMonth) {
        return new LocalDateTime(LocalDate.ofEpochDay(epochDay).withDayOfMonth(dayOfMonth).toEpochDay(), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withDayOfYear(int dayOfYear) {
        return new LocalDateTime(LocalDate.ofEpochDay(epochDay).withDayOfYear(dayOfYear).toEpochDay(), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withHour(int hour) {
        return new LocalDateTime(epochDay, LocalTime.ofNanoOfDay(nanoOfDay).withHour(hour).toNanoOfDay());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withMinute(int minute) {
        return new LocalDateTime(epochDay, LocalTime.ofNanoOfDay(nanoOfDay).withMinute(minute).toNanoOfDay());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withSecond(int second) {
        return new LocalDateTime(epochDay, LocalTime.ofNanoOfDay(nanoOfDay).withSecond(second).toNanoOfDay());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public LocalDateTime withNano(int nanoOfSecond) {
        return new LocalDateTime(epochDay, LocalTime.ofNanoOfDay(nanoOfDay).withNano(nanoOfSecond).toNanoOfDay());
    }

    // --- ordering: lexicographic (date, then time). The real signatures take ChronoLocalDateTime<?>,
    //     so JDK-compiled proof bytecode checkcasts the arg to that interface and resolves the interface-
    //     typed overload — the model mirrors the descriptor and casts back to the LocalDateTime model
    //     (now an instanceof ChronoLocalDateTime). A non-LocalDateTime arg is out of scope — LOUD. ---

    private LocalDateTime asModel(ChronoLocalDateTime<?> other) {
        if (!(other instanceof LocalDateTime)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime comparison against a non-ISO ChronoLocalDateTime — only LocalDateTime endpoints are modeled");
        }
        return (LocalDateTime) other;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isEqual(ChronoLocalDateTime<?> other) {
        LocalDateTime o = asModel(other);
        return this.epochDay == o.epochDay && this.nanoOfDay == o.nanoOfDay;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isBefore(ChronoLocalDateTime<?> other) {
        return compareTo(other) < 0;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isAfter(ChronoLocalDateTime<?> other) {
        return compareTo(other) > 0;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public int compareTo(ChronoLocalDateTime<?> other) {
        LocalDateTime o = asModel(other);
        if (this.epochDay < o.epochDay) {
            return -1;
        }
        if (this.epochDay > o.epochDay) {
            return 1;
        }
        return this.nanoOfDay < o.nanoOfDay ? -1 : (this.nanoOfDay == o.nanoOfDay ? 0 : 1);
    }

    // --- generic TemporalField / TemporalUnit accessors: split time-based fields/units onto the
    //     nano-of-day (LocalTime) part and date-based ones onto the epoch-day (LocalDate) part, reusing
    //     each part-model's own modeled accessors. A non-Chrono field/unit is declined LOUD. The
    //     ChronoLocalDateTime interface makes a LocalDateTime an instanceof it (proof-site checkcast). ---

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalField field) {
        return (field instanceof ChronoField)
            && (((ChronoField) field).isDateBased() || ((ChronoField) field).isTimeBased());
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            return unit != ChronoUnit.FOREVER;
        }
        return unit != null && unit.isSupportedBy(this);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long getLong(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.getLong(java.time.temporal.TemporalField) — only ChronoField is dispatched by this date+time model");
        }
        return ((ChronoField) field).isTimeBased()
            ? toLocalTime().getLong(field)
            : toLocalDate().getLong(field);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public int get(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.get(java.time.temporal.TemporalField) — only ChronoField is dispatched by this date+time model");
        }
        return ((ChronoField) field).isTimeBased()
            ? toLocalTime().get(field)
            : toLocalDate().get(field);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange range(TemporalField field) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.range(java.time.temporal.TemporalField) — only ChronoField is dispatched by this date+time model");
        }
        return ((ChronoField) field).isTimeBased()
            ? toLocalTime().range(field)
            : toLocalDate().range(field);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ChronoLocalDateTime<LocalDate> with(TemporalField field, long newValue) {
        if (!(field instanceof ChronoField)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.with(java.time.temporal.TemporalField,long) — only ChronoField is dispatched by this date+time model");
        }
        if (((ChronoField) field).isTimeBased()) {
            return new LocalDateTime(epochDay, toLocalTime().with(field, newValue).toNanoOfDay());
        }
        LocalDate d = (LocalDate) toLocalDate().with(field, newValue);
        return new LocalDateTime(d.toEpochDay(), nanoOfDay);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ChronoLocalDateTime<LocalDate> plus(long amountToAdd, TemporalUnit unit) {
        if (!(unit instanceof ChronoUnit)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.plus(long,java.time.temporal.TemporalUnit) — only ChronoUnit is dispatched by this date+time model");
        }
        switch ((ChronoUnit) unit) {
            case NANOS:
                return plusNanos(amountToAdd);
            case MICROS:
                return plusNanos(Math.multiplyExact(amountToAdd, 1_000L));
            case MILLIS:
                return plusNanos(Math.multiplyExact(amountToAdd, 1_000_000L));
            case SECONDS:
                return plusSeconds(amountToAdd);
            case MINUTES:
                return plusMinutes(amountToAdd);
            case HOURS:
                return plusHours(amountToAdd);
            case HALF_DAYS:
                return plusHours(Math.multiplyExact(amountToAdd, 12L));
            case DAYS:
                return plusDays(amountToAdd);
            case WEEKS:
                return plusWeeks(amountToAdd);
            case MONTHS:
                return plusMonths(amountToAdd);
            case YEARS:
                return plusYears(amountToAdd);
            case DECADES:
                return plusYears(Math.multiplyExact(amountToAdd, 10L));
            case CENTURIES:
                return plusYears(Math.multiplyExact(amountToAdd, 100L));
            case MILLENNIA:
                return plusYears(Math.multiplyExact(amountToAdd, 1000L));
            default:
                throw fail("bmc4j: unmodelled member java.time.LocalDateTime.plus(long,java.time.temporal.TemporalUnit) — the unit " + unit + " (ERAS / FOREVER) is not supported by this date+time model");
        }
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ChronoLocalDateTime<LocalDate> minus(long amountToSubtract, TemporalUnit unit) {
        return amountToSubtract == Long.MIN_VALUE
            ? plus(Long.MAX_VALUE, unit).plus(1, unit)
            : plus(-amountToSubtract, unit);
    }

    /**
     * The amount in {@code unit} from this date-time to {@code endExclusive}, matching the JDK: time-based
     * units count the total nanosecond delta (whole-day carry included), date-based units the calendar
     * difference adjusted by whether the time-of-day has yet reached the start's. The end must be a modeled
     * LocalDateTime; an unsupported unit is declined LOUD.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        if (!(endExclusive instanceof LocalDateTime)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only LocalDateTime endpoints are modeled");
        }
        if (!(unit instanceof ChronoUnit)) {
            throw fail("bmc4j: unmodelled member java.time.LocalDateTime.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — only ChronoUnit is dispatched by this date+time model");
        }
        LocalDateTime end = (LocalDateTime) endExclusive;
        ChronoUnit cu = (ChronoUnit) unit;
        if (cu.isTimeBased()) {
            // Total nanosecond delta (whole-day carry folded in), THEN divide by the unit — exactly the
            // JDK's order. Computing days*24 + subDayNanos/HOUR separately would diverge (integer division
            // does not distribute over a mixed-sign sum: days=1,timeNanos=-1 is 23 hours, not 24). The
            // *NANOS_PER_DAY scale routes through a checked multiply so a day count past the long-nanos
            // bound fails LOUDLY rather than silently wrapping (the real LocalDateTime overflows here too).
            long totalNanos = Math.addExact(
                Math.multiplyExact(end.epochDay - this.epochDay, NANOS_PER_DAY),
                end.nanoOfDay - this.nanoOfDay);
            switch (cu) {
                case NANOS:
                    return totalNanos;
                case MICROS:
                    return totalNanos / 1_000L;
                case MILLIS:
                    return totalNanos / 1_000_000L;
                case SECONDS:
                    return totalNanos / NANOS_PER_SECOND;
                case MINUTES:
                    return totalNanos / NANOS_PER_MINUTE;
                case HOURS:
                    return totalNanos / NANOS_PER_HOUR;
                case HALF_DAYS:
                    return totalNanos / (12L * NANOS_PER_HOUR);
                default:
                    break;
            }
        }
        // Date-based units: the calendar day difference, decremented by one if the end's time-of-day has
        // not yet caught up to this one's (so the elapsed whole period excludes the partial day), then
        // the LocalDate calendar arithmetic gives months/years/etc. (mirrors the JDK's adjustment).
        long endEpochDay = end.epochDay;
        if (endEpochDay > this.epochDay && end.nanoOfDay < this.nanoOfDay) {
            endEpochDay--;
        } else if (endEpochDay < this.epochDay && end.nanoOfDay > this.nanoOfDay) {
            endEpochDay++;
        }
        return LocalDate.ofEpochDay(this.epochDay).until(LocalDate.ofEpochDay(endEpochDay), unit);
    }

    @BmcUnmodelable(reason = "time zones (atZone) are out of scope for this local date+time model")
    @Override
    public ChronoZonedDateTime<LocalDate> atZone(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.atZone(java.time.ZoneId) — time zones are out of scope for this local date+time model");
    }

    /**
     * Epoch-second at the given offset — pure integer arithmetic over the modeled pieces (this date's
     * epoch-day, the time's second-of-day, the offset's total seconds), no zone DB.
     * {@code epochDay*86400 + secondOfDay - offset.getTotalSeconds()}, exactly like the JDK; loud
     * {@code Math.*Exact} overflow.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public long toEpochSecond(ZoneOffset offset) {
        long secs = epochDay * 86400L + nanoOfDay / NANOS_PER_SECOND;
        return Math.subtractExact(secs, offset.getTotalSeconds());
    }

    // --- genuinely-unmodelable surface: external clock, zone projection, the millis-Instant toInstant,
    //     text format/parse, sub-precision truncation and the open-ended TemporalAmount/Adjuster/Query
    //     plumbing. Each is a LOUD stub. ---

    @BmcUnmodelable(reason = "a Clock is non-deterministic external state — pass LocalDateTimes as symbolic proof parameters")
    public static LocalDateTime now(Clock clock) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.now(java.time.Clock) — a Clock is non-deterministic external state — pass LocalDateTimes as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "the current date-time in a named zone is non-deterministic external state plus zone-rules projection — pass LocalDateTimes as symbolic proof parameters")
    public static LocalDateTime now(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.now(java.time.ZoneId) — the current date-time in a named zone is non-deterministic external state plus zone-rules projection — pass LocalDateTimes as symbolic proof parameters");
    }

    @BmcUnmodelable(reason = "deriving the local date-time of an Instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits")
    public static LocalDateTime ofInstant(Instant instant, ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.ofInstant(java.time.Instant,java.time.ZoneId) — deriving the local date-time of an Instant in a named zone needs the zone-rules/offset DB the offset-only zone model deliberately omits");
    }

    @BmcUnmodelable(reason = "ISO-8601 date-time text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)")
    public static LocalDateTime parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.parse(java.lang.CharSequence) — ISO-8601 date-time text parsing routes through DateTimeFormatter — out of scope for a bounded model (no text parsing)");
    }

    @BmcUnmodelable(reason = "formatter-driven date-time text parsing — out of scope for a bounded model (no text parsing/locale)")
    public static LocalDateTime parse(CharSequence text, DateTimeFormatter formatter) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.parse(java.lang.CharSequence,java.time.format.DateTimeFormatter) — formatter-driven date-time text parsing — out of scope for a bounded model (no text parsing/locale)");
    }

    @BmcUnmodelable(reason = "extracting a LocalDateTime from an arbitrary TemporalAccessor needs its open-ended field surface; build via LocalDateTime.of/ofEpochSecond")
    public static LocalDateTime from(TemporalAccessor temporal) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.from(java.time.temporal.TemporalAccessor) — extracting a LocalDateTime from an arbitrary TemporalAccessor needs its open-ended field surface; build via LocalDateTime.of/ofEpochSecond");
    }

    @BmcUnmodelable(reason = "pairing a date-time with an offset builds an OffsetDateTime the (epoch-day, nano-of-day) model doesn't carry as a distinct type")
    public OffsetDateTime atOffset(ZoneOffset offset) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.atOffset(java.time.ZoneOffset) — pairing a date-time with an offset builds an OffsetDateTime the (epoch-day, nano-of-day) model doesn't carry as a distinct type");
    }

    @BmcUnmodelable(reason = "Instant is millis-bounded here, so an Instant carrying this date-time's sub-millisecond nanos can't be represented — use toEpochSecond(offset)")
    public Instant toInstant(ZoneOffset offset) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.toInstant(java.time.ZoneOffset) — Instant is millis-bounded here, so an Instant carrying this date-time's sub-millisecond nanos can't be represented — use toEpochSecond(offset)");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalUnit truncation surface is out of scope; use the typed with*/plus* on the (epoch-day, nano-of-day) backing")
    public LocalDateTime truncatedTo(TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.truncatedTo(java.time.temporal.TemporalUnit) — the open-ended TemporalUnit truncation surface is out of scope; use the typed with*/plus* on the (epoch-day, nano-of-day) backing");
    }

    @BmcUnmodelable(reason = "formatter-driven date-time text rendering routes through DateTimeFormatter (dtoa/locale) — out of scope for a bounded model")
    @Override
    public String format(DateTimeFormatter formatter) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.format(java.time.format.DateTimeFormatter) — formatter-driven date-time text rendering routes through DateTimeFormatter (dtoa/locale) — out of scope for a bounded model");
    }

    @BmcUnmodelable(reason = "the Chronology accessor (getChronology) is out of scope for this date+time model")
    @Override
    public Chronology getChronology() {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.getChronology() — the Chronology accessor is out of scope for this date+time model");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed with*/plus* on the (epoch-day, nano-of-day) backing")
    public LocalDateTime with(TemporalAdjuster adjuster) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.with(java.time.temporal.TemporalAdjuster) — the open-ended TemporalAdjuster lambda surface can run arbitrary unmodeled adjustment; use the typed with*/plus* on the (epoch-day, nano-of-day) backing");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plus* or plus(long,TemporalUnit)")
    public LocalDateTime plus(TemporalAmount amountToAdd) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.plus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed add needs its open-ended getUnits/get(unit) surface; use the typed plus* or plus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minus* or minus(long,TemporalUnit)")
    public LocalDateTime minus(TemporalAmount amountToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.minus(java.time.temporal.TemporalAmount) — the TemporalAmount-typed subtract needs its open-ended getUnits/get(unit) surface; use the typed minus* or minus(long,TemporalUnit)");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor")
    public <R> R query(TemporalQuery<R> query) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.query(java.time.temporal.TemporalQuery) — the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor");
    }

    @BmcUnmodelable(reason = "adjusting an arbitrary Temporal with this date-time's EPOCH_DAY/NANO_OF_DAY needs that Temporal's unmodeled field surface")
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.adjustInto(java.time.temporal.Temporal) — adjusting an arbitrary Temporal with this date-time's EPOCH_DAY/NANO_OF_DAY needs that Temporal's unmodeled field surface");
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        if (!(o instanceof LocalDateTime)) {
            return false;
        }
        LocalDateTime other = (LocalDateTime) o;
        return this.epochDay == other.epochDay && this.nanoOfDay == other.nanoOfDay;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        long h = epochDay * 31 + nanoOfDay;
        return (int) (h ^ (h >>> 32));
    }
}
