package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * JBMC model of {@link java.time.LocalDate} as an epoch-day {@code long}. Ordering
 * and day arithmetic are exact. Calendar fields (year/month/day) and calendar-month
 * arithmetic (plusMonths/plusYears + minus) are modeled by decoding the epoch-day to a
 * proleptic-Gregorian y/m/d, applying the JDK's exact month-carry + day-of-month CLAMP
 * rule ("resolvePreviousValid": 2024-01-31 plusMonths 1 -> 2024-02-29), and recomposing.
 * The y/m/d <-> epoch-day machinery mirrors the JDK bit-for-bit (validated by the
 * differential suite vs the real JDK). {@code of(y, m, d)} is NOT a factory here; build
 * dates via {@link #ofEpochDay} (or via LocalDateTime). Formatters/zones are out of scope.
 */
@BmcModelTail(reason = "the remaining ChronoLocalDate/Temporal surface (with(TemporalField/Adjuster)/getDayOfWeek/getMonth/getEra/getChronology/datesUntil/format/range/query/get(TemporalField)/plus(TemporalAmount)/the of(y,Month,d) and parse factories) is out of scope for this epoch-day model; all loud under JBMC")
public final class LocalDate implements ChronoLocalDate {

    // DAYS from year 0000-01-01 (proleptic) to 1970-01-01.
    private static final long DAYS_0000_TO_1970 = (146097L * 5L) - (30L * 365L + 7L);

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

    // --- ChronoLocalDate / Temporal abstract surface: implemented ONLY to make the LocalDate an
    //     instanceof ChronoLocalDate (so the proof-site checkcast passes); each is LOUD, never modeled.
    //     lengthOfMonth() and until(ChronoLocalDate) above already satisfy the interface. ---

    @BmcNotModelled(reason = "the Chronology accessor (getChronology) is out of scope for this epoch-day model")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public Chronology getChronology() {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.getChronology() — the Chronology accessor is out of scope for this epoch-day model");
    }

    @BmcNotModelled(reason = "the generic TemporalUnit difference (until) is out of scope for this epoch-day model")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — the generic TemporalUnit difference is out of scope for this epoch-day model");
    }

    @BmcNotModelled(reason = "the TemporalField query plumbing (isSupported) is out of scope for this epoch-day model")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isSupported(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.isSupported(java.time.temporal.TemporalField) — the TemporalField query plumbing is out of scope for this epoch-day model");
    }

    @BmcNotModelled(reason = "the TemporalUnit query plumbing (isSupported) is out of scope for this epoch-day model")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.isSupported(java.time.temporal.TemporalUnit) — the TemporalUnit query plumbing is out of scope for this epoch-day model");
    }

    @BmcNotModelled(reason = "the TemporalField accessor (getLong) is out of scope for this epoch-day model")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public long getLong(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.getLong(java.time.temporal.TemporalField) — the TemporalField accessor is out of scope for this epoch-day model");
    }

    @BmcNotModelled(reason = "the generic TemporalField setter (with) is out of scope; use withYear/withMonth/withDayOf*")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public ChronoLocalDate with(TemporalField field, long newValue) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.with(java.time.temporal.TemporalField,long) — the generic TemporalField setter is out of scope; use withYear/withMonth/withDayOf*");
    }

    @BmcNotModelled(reason = "the generic TemporalUnit add (plus) is out of scope; use plusDays/plusWeeks/plusMonths/plusYears")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public ChronoLocalDate plus(long amountToAdd, TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.LocalDate.plus(long,java.time.temporal.TemporalUnit) — the generic TemporalUnit add is out of scope; use plusDays/plusWeeks/plusMonths/plusYears");
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
