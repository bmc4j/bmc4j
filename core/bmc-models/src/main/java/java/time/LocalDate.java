package java.time;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

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
@BmcModelConforms("epoch-day LocalDate with proleptic-Gregorian field decode — differential (TimeConformanceTest) + @BmcProof (proofs.time)")
@BmcModelTail(reason = "the wide ChronoLocalDate/Temporal surface (with*/getDayOfWeek/getDayOfYear/lengthOfMonth/isLeapYear/until/atStartOfDay/atTime/format/datesUntil/range/query/get(TemporalField)/plus(TemporalAmount)/isAfter-Before-Equal/the of(y,m,d) and parse factories) is out of scope for this epoch-day model; all loud under JBMC")
public final class LocalDate {

    // DAYS from year 0000-01-01 (proleptic) to 1970-01-01.
    private static final long DAYS_0000_TO_1970 = (146097L * 5L) - (30L * 365L + 7L);

    final long epochDay;

    private LocalDate(long epochDay) {
        this.epochDay = epochDay;
    }

    public static LocalDate ofEpochDay(long epochDay) {
        return new LocalDate(epochDay);
    }

    public long toEpochDay() {
        return epochDay;
    }

    public boolean isBefore(LocalDate other) {
        return this.epochDay < other.epochDay;
    }

    public boolean isAfter(LocalDate other) {
        return this.epochDay > other.epochDay;
    }

    public int compareTo(LocalDate other) {
        return this.epochDay < other.epochDay ? -1 : (this.epochDay == other.epochDay ? 0 : 1);
    }

    public LocalDate plusDays(long days) {
        return new LocalDate(this.epochDay + days);
    }

    public LocalDate minusDays(long days) {
        return new LocalDate(this.epochDay - days);
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

    public int getYear() {
        return ymd()[0];
    }

    public int getMonthValue() {
        return ymd()[1];
    }

    public int getDayOfMonth() {
        return ymd()[2];
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

    public LocalDate plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        int[] f = ymd();
        int year = f[0], month = f[1], day = f[2];
        int newYear = (int) (year + yearsToAdd);
        return resolvePreviousValid(newYear, month, day);
    }

    public LocalDate minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

    public LocalDate minusYears(long yearsToSubtract) {
        return plusYears(-yearsToSubtract);
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

    @Override
    public boolean equals(Object o) {
        return (o instanceof LocalDate) && ((LocalDate) o).epochDay == this.epochDay;
    }

    @Override
    public int hashCode() {
        return (int) (epochDay ^ (epochDay >>> 32));
    }
}
