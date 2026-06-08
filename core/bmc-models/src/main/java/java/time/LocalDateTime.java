package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.chrono.ChronoLocalDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

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
 */
@BmcModelTail(reason = "the remaining LocalDateTime/Temporal surface (with(TemporalField/Adjuster)/truncatedTo/until/atZone/atOffset/format/range/query/get(TemporalField)/plus(TemporalAmount)/getDayOfWeek/getMonth and the of(...,Month,...)/parse factories) is out of scope for this date+time model; all loud under JBMC")
public final class LocalDateTime implements ChronoLocalDateTime<LocalDate> {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60L * NANOS_PER_MINUTE;
    private static final long NANOS_PER_DAY = 24L * NANOS_PER_HOUR;

    final long epochDay;     // date as days from 1970-01-01 (proleptic Gregorian)
    final long nanoOfDay;    // time within the day, [0, NANOS_PER_DAY)

    private LocalDateTime(long epochDay, long nanoOfDay) {
        this.epochDay = epochDay;
        this.nanoOfDay = nanoOfDay;
    }

    @BmcNotModelled(reason = "wall-clock read is non-deterministic external state — pass LocalDateTimes as symbolic proof parameters")
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

    // --- ChronoLocalDateTime / Temporal abstract surface: implemented ONLY to make the LocalDateTime an
    //     instanceof ChronoLocalDateTime (so the proof-site checkcast passes); each is LOUD, never
    //     modeled. toLocalDate()/toLocalTime() above already satisfy the interface. ---

    @BmcNotModelled(reason = "the TemporalField query plumbing (isSupported) is out of scope for this date+time model")
    @Override
    public boolean isSupported(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.isSupported(java.time.temporal.TemporalField) — the TemporalField query plumbing is out of scope for this date+time model");
    }

    @BmcNotModelled(reason = "the TemporalUnit query plumbing (isSupported) is out of scope for this date+time model")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.isSupported(java.time.temporal.TemporalUnit) — the TemporalUnit query plumbing is out of scope for this date+time model");
    }

    @BmcNotModelled(reason = "the TemporalField accessor (getLong) is out of scope for this date+time model")
    @Override
    public long getLong(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.getLong(java.time.temporal.TemporalField) — the TemporalField accessor is out of scope for this date+time model");
    }

    @BmcNotModelled(reason = "the generic TemporalField setter (with) is out of scope; use withYear/withMonth/withHour/etc.")
    @Override
    public ChronoLocalDateTime<LocalDate> with(TemporalField field, long newValue) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.with(java.time.temporal.TemporalField,long) — the generic TemporalField setter is out of scope; use withYear/withMonth/withHour/etc.");
    }

    @BmcNotModelled(reason = "the generic TemporalUnit add (plus) is out of scope; use plusDays/plusHours/plusMonths/etc.")
    @Override
    public ChronoLocalDateTime<LocalDate> plus(long amountToAdd, TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.plus(long,java.time.temporal.TemporalUnit) — the generic TemporalUnit add is out of scope; use plusDays/plusHours/plusMonths/etc.");
    }

    @BmcNotModelled(reason = "the generic TemporalUnit difference (until) is out of scope for this date+time model")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — the generic TemporalUnit difference is out of scope for this date+time model");
    }

    @BmcNotModelled(reason = "time zones (atZone) are out of scope for this local date+time model")
    @Override
    public ChronoZonedDateTime<LocalDate> atZone(ZoneId zone) {
        throw fail("bmc4j: unmodelled member java.time.LocalDateTime.atZone(java.time.ZoneId) — time zones are out of scope for this local date+time model");
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
