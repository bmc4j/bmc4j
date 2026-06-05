package java.time;

/**
 * JBMC model of {@link java.time.LocalTime} backed by a nano-of-day {@code long} in
 * {@code [0, 86_400_000_000_000)} (24h). Field extraction and time arithmetic reduce to integer
 * arithmetic JBMC reasons about precisely.
 *
 * <p>{@code of} validates its fields exactly like the JDK (loud {@link DateTimeException} out of
 * range). {@code plus*} wraps within the day like the real LocalTime (it has no overflow concept).
 * Zones, formatters and sub-nano precision are out of scope (a model, not a reimplementation);
 * {@code now()} is intentionally not modeled — pass LocalTimes as proof parameters.
 */
public final class LocalTime {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60L * NANOS_PER_MINUTE;
    private static final long NANOS_PER_DAY = 24L * NANOS_PER_HOUR;

    final long nanoOfDay;

    private LocalTime(long nanoOfDay) {
        this.nanoOfDay = nanoOfDay;
    }

    public static LocalTime of(int hour, int minute) {
        return of(hour, minute, 0, 0);
    }

    public static LocalTime of(int hour, int minute, int second) {
        return of(hour, minute, second, 0);
    }

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

    public static LocalTime ofSecondOfDay(long secondOfDay) {
        if (secondOfDay < 0 || secondOfDay > 86399) {
            throw new DateTimeException("Invalid value for SecondOfDay: " + secondOfDay);
        }
        return new LocalTime(secondOfDay * NANOS_PER_SECOND);
    }

    public static LocalTime ofNanoOfDay(long nanoOfDay) {
        if (nanoOfDay < 0 || nanoOfDay > NANOS_PER_DAY - 1) {
            throw new DateTimeException("Invalid value for NanoOfDay: " + nanoOfDay);
        }
        return new LocalTime(nanoOfDay);
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

    public int toSecondOfDay() {
        return (int) (nanoOfDay / NANOS_PER_SECOND);
    }

    public long toNanoOfDay() {
        return nanoOfDay;
    }

    // plus* wrap within the day (mod 24h), exactly like the real LocalTime, which has no overflow.
    private LocalTime plusNanos(long nanos) {
        if (nanos == 0) {
            return this;
        }
        long dayNanos = nanos % NANOS_PER_DAY;             // reduce to (-DAY, DAY)
        long newNod = (nanoOfDay + dayNanos + NANOS_PER_DAY) % NANOS_PER_DAY;
        return new LocalTime(newNod);
    }

    public LocalTime plusHours(long hours) {
        return plusNanos((hours % 24) * NANOS_PER_HOUR);
    }

    public LocalTime plusMinutes(long minutes) {
        return plusNanos((minutes % (24 * 60)) * NANOS_PER_MINUTE);
    }

    public LocalTime plusSeconds(long seconds) {
        return plusNanos((seconds % (24 * 60 * 60)) * NANOS_PER_SECOND);
    }

    public LocalTime minusHours(long hours) {
        return plusHours(-(hours % 24));
    }

    public LocalTime minusMinutes(long minutes) {
        return plusMinutes(-(minutes % (24 * 60)));
    }

    public LocalTime minusSeconds(long seconds) {
        return plusSeconds(-(seconds % (24 * 60 * 60)));
    }

    public boolean isBefore(LocalTime other) {
        return this.nanoOfDay < other.nanoOfDay;
    }

    public boolean isAfter(LocalTime other) {
        return this.nanoOfDay > other.nanoOfDay;
    }

    public int compareTo(LocalTime other) {
        return this.nanoOfDay < other.nanoOfDay ? -1 : (this.nanoOfDay == other.nanoOfDay ? 0 : 1);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof LocalTime) && ((LocalTime) o).nanoOfDay == this.nanoOfDay;
    }

    @Override
    public int hashCode() {
        return (int) (nanoOfDay ^ (nanoOfDay >>> 32));
    }
}
