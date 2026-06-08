package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

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
@BmcModelTail(reason = "the remaining LocalTime/Temporal surface (with(TemporalField/Adjuster)/truncatedTo/until/atOffset/format/range/query/get(TemporalField)/plus(TemporalAmount)/toEpochSecond/parse) is out of scope for the nano-of-day model; all loud under JBMC")
public final class LocalTime {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60L * NANOS_PER_MINUTE;
    private static final long NANOS_PER_DAY = 24L * NANOS_PER_HOUR;

    final long nanoOfDay;

    private LocalTime(long nanoOfDay) {
        this.nanoOfDay = nanoOfDay;
    }

    @BmcNotModelled(reason = "wall-clock read is non-deterministic external state — pass LocalTimes as symbolic proof parameters")
    public static LocalTime now() {
        throw fail("bmc4j: unmodelled member java.time.LocalTime.now() — wall-clock read is non-deterministic external state — pass LocalTimes as symbolic proof parameters");
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
