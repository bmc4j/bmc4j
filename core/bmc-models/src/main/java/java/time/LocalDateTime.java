package java.time;

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
public final class LocalDateTime {

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

    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute) {
        return of(year, month, dayOfMonth, hour, minute, 0, 0);
    }

    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute, int second) {
        return of(year, month, dayOfMonth, hour, minute, second, 0);
    }

    public static LocalDateTime of(int year, int month, int dayOfMonth,
                                   int hour, int minute, int second, int nanoOfSecond) {
        long ed = toEpochDay(year, month, dayOfMonth);
        long nod = timeToNanoOfDay(hour, minute, second, nanoOfSecond);
        return new LocalDateTime(ed, nod);
    }

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

    public int getYear() {
        return ymd()[0];
    }

    public int getMonthValue() {
        return ymd()[1];
    }

    public int getDayOfMonth() {
        return ymd()[2];
    }

    public int getHour() {
        return (int) (nanoOfDay / NANOS_PER_HOUR);
    }

    public int getMinute() {
        return (int) ((nanoOfDay / NANOS_PER_MINUTE) % 60);
    }

    public int getSecond() {
        return (int) ((nanoOfDay / NANOS_PER_SECOND) % 60);
    }

    public int getNano() {
        return (int) (nanoOfDay % NANOS_PER_SECOND);
    }

    public LocalDate toLocalDate() {
        return LocalDate.ofEpochDay(epochDay);
    }

    public LocalTime toLocalTime() {
        return LocalTime.ofNanoOfDay(nanoOfDay);
    }

    // --- day/time arithmetic: exact, sound (no calendar-month rule involved) ---

    public LocalDateTime plusDays(long days) {
        return new LocalDateTime(epochDay + days, nanoOfDay);
    }

    public LocalDateTime minusDays(long days) {
        return new LocalDateTime(epochDay - days, nanoOfDay);
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

    public LocalDateTime plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        int[] f = ymd();
        int newYear = (int) (f[0] + yearsToAdd);
        return resolveDate(newYear, f[1], f[2]);
    }

    public LocalDateTime minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

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

    public LocalDateTime plusHours(long hours) {
        return plusDayCarryAndNanos(hours / 24, (hours % 24) * NANOS_PER_HOUR);
    }

    public LocalDateTime plusMinutes(long minutes) {
        return plusDayCarryAndNanos(minutes / (24 * 60), (minutes % (24 * 60)) * NANOS_PER_MINUTE);
    }

    public LocalDateTime plusSeconds(long seconds) {
        return plusDayCarryAndNanos(seconds / (24 * 60 * 60), (seconds % (24 * 60 * 60)) * NANOS_PER_SECOND);
    }

    public LocalDateTime minusHours(long hours) {
        return plusHours(-hours);
    }

    public LocalDateTime minusMinutes(long minutes) {
        return plusMinutes(-minutes);
    }

    public LocalDateTime minusSeconds(long seconds) {
        return plusSeconds(-seconds);
    }

    // --- ordering: lexicographic (date, then time) ---

    public boolean isBefore(LocalDateTime other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(LocalDateTime other) {
        return compareTo(other) > 0;
    }

    public int compareTo(LocalDateTime other) {
        if (this.epochDay < other.epochDay) {
            return -1;
        }
        if (this.epochDay > other.epochDay) {
            return 1;
        }
        return this.nanoOfDay < other.nanoOfDay ? -1 : (this.nanoOfDay == other.nanoOfDay ? 0 : 1);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalDateTime)) {
            return false;
        }
        LocalDateTime other = (LocalDateTime) o;
        return this.epochDay == other.epochDay && this.nanoOfDay == other.nanoOfDay;
    }

    @Override
    public int hashCode() {
        long h = epochDay * 31 + nanoOfDay;
        return (int) (h ^ (h >>> 32));
    }
}
