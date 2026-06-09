package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.ValueRange;
import java.time.zone.ZoneRules;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.ZoneOffset} as a single total-seconds {@code int} (the real backing),
 * so offset logic reduces to integer arithmetic JBMC reasons about precisely. {@code extends ZoneId}
 * (the abstract base model) so a ZoneOffset is an {@code instanceof ZoneId} where the JDK-compiled proof
 * bytecode expects one.
 *
 * <p>Modeled surface: the {@code ofHours}/{@code ofHoursMinutes}/{@code ofTotalSeconds} factories (each
 * validating against the JDK's ±18:00 maximum-offset bound, loud {@link DateTimeException} out of range),
 * {@code getTotalSeconds}, {@code getId} (the canonical {@code "Z"} / {@code ±HH:MM[:SS]} text), and
 * {@code equals}/{@code hashCode}/{@code compareTo} (ordered by descending total-seconds, like the JDK).
 * All are validated bit-for-bit by the differential suite vs the real JDK.
 *
 * <p>It {@code implements java.time.temporal.TemporalAccessor, java.time.temporal.TemporalAdjuster}
 * (the real JDK interfaces) so a ZoneOffset survives the {@code checkcast} the proof bytecode emits on an
 * interface-typed parameter. Those interface abstract methods, {@code getRules}, and the named-region
 * remainder are NOT modeled — each is a LOUD stub, a NAMED UNKNOWN if reached.
 *
 * <p>The whole real {@code ZoneOffset} surface is accounted per-member (modeled or LOUD-stubbed), so
 * there is NO class-level {@code @BmcModelTail}: nothing falls through.
 */
public final class ZoneOffset extends ZoneId implements TemporalAccessor, TemporalAdjuster, Comparable<ZoneOffset> {

    /** The JDK's maximum absolute offset: ±18 hours. */
    private static final int MAX_SECONDS = 18 * 3600;

    public static final ZoneOffset UTC = new ZoneOffset(0);
    public static final ZoneOffset MIN = new ZoneOffset(-MAX_SECONDS);
    public static final ZoneOffset MAX = new ZoneOffset(MAX_SECONDS);

    private final int totalSeconds;

    private ZoneOffset(int totalSeconds) {
        this.totalSeconds = totalSeconds;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static ZoneOffset ofTotalSeconds(int totalSeconds) {
        if (totalSeconds < -MAX_SECONDS || totalSeconds > MAX_SECONDS) {
            throw new DateTimeException("Zone offset not in valid range: -18:00 to +18:00");
        }
        return new ZoneOffset(totalSeconds);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static ZoneOffset ofHours(int hours) {
        return ofTotalSeconds(hours * 3600);
    }

    /**
     * Build from hours + minutes; the JDK requires hours and minutes to have the SAME sign (or one be
     * zero) and minutes in [-59, 59], throwing {@link DateTimeException} otherwise — replicated here.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static ZoneOffset ofHoursMinutes(int hours, int minutes) {
        if (hours > 18 || hours < -18) {
            throw new DateTimeException("Zone offset hours not in valid range: value " + hours + " is not in the range -18 to 18");
        }
        if (minutes < -59 || minutes > 59) {
            throw new DateTimeException("Zone offset minutes not in valid range: value " + minutes + " is not in the range -59 to 59");
        }
        if ((hours > 0 && minutes < 0) || (hours < 0 && minutes > 0)) {
            throw new DateTimeException("Zone offset minutes and seconds must have the same sign as hours");
        }
        return ofTotalSeconds(hours * 3600 + minutes * 60);
    }

    /**
     * Build from hours + minutes + seconds; the JDK requires all parts to have the SAME sign (or be
     * zero), minutes/seconds in [-59, 59], and the resulting offset within ±18:00 — replicated here as
     * pure integer comparisons/arithmetic, loud {@link DateTimeException} out of range.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public static ZoneOffset ofHoursMinutesSeconds(int hours, int minutes, int seconds) {
        if (hours > 18 || hours < -18) {
            throw new DateTimeException("Zone offset hours not in valid range: value " + hours + " is not in the range -18 to 18");
        }
        if (minutes < -59 || minutes > 59) {
            throw new DateTimeException("Zone offset minutes not in valid range: value " + minutes + " is not in the range -59 to 59");
        }
        if (seconds < -59 || seconds > 59) {
            throw new DateTimeException("Zone offset seconds not in valid range: value " + seconds + " is not in the range -59 to 59");
        }
        if ((Math.abs(hours) > 0 && (signMismatch(hours, minutes) || signMismatch(hours, seconds)))
            || signMismatch(minutes, seconds)) {
            throw new DateTimeException("Zone offset minutes and seconds must have the same sign as hours");
        }
        return ofTotalSeconds(hours * 3600 + minutes * 60 + seconds);
    }

    /** True if a and b are both non-zero with opposite signs (the JDK's same-sign rule for offset parts). */
    private static boolean signMismatch(int a, int b) {
        return (a > 0 && b < 0) || (a < 0 && b > 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getTotalSeconds() {
        return totalSeconds;
    }

    /**
     * Canonical id text: {@code "Z"} for UTC, else {@code ±HH:MM} (and {@code :SS} when the offset has a
     * non-zero seconds part), exactly like the JDK's {@code buildId}.
     */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public String getId() {
        if (totalSeconds == 0) {
            return "Z";
        }
        int absTotal = Math.abs(totalSeconds);
        StringBuilder buf = new StringBuilder();
        int absHours = absTotal / 3600;
        int absMinutes = (absTotal / 60) % 60;
        buf.append(totalSeconds < 0 ? "-" : "+")
            .append(absHours < 10 ? "0" : "").append(absHours)
            .append(absMinutes < 10 ? ":0" : ":").append(absMinutes);
        int absSeconds = absTotal % 60;
        if (absSeconds != 0) {
            buf.append(absSeconds < 10 ? ":0" : ":").append(absSeconds);
        }
        return buf.toString();
    }

    /** A ZoneOffset is already a fully-resolved offset, so {@code normalized()} returns {@code this}. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public ZoneOffset normalized() {
        return this;
    }

    /** Ordered by DESCENDING total-seconds, matching the JDK (east-of-UTC sorts before west-of-UTC). */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public int compareTo(ZoneOffset other) {
        return other.totalSeconds - this.totalSeconds;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        return (o instanceof ZoneOffset) && ((ZoneOffset) o).totalSeconds == this.totalSeconds;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return totalSeconds;
    }

    // --- ZoneId / TemporalAccessor / TemporalAdjuster abstract surface: implemented ONLY to satisfy the
    //     hierarchy and make a ZoneOffset an instanceof those types (so the proof-site checkcast passes);
    //     each is LOUD, never modeled. ---

    @BmcUnmodelable(reason = "the fixed-offset ZoneRules object is DST-rule machinery out of scope for the total-seconds offset model")
    @Override
    public ZoneRules getRules() {
        throw fail("bmc4j: unmodelled member java.time.ZoneOffset.getRules() — the fixed-offset ZoneRules object is DST-rule machinery out of scope for the total-seconds offset model");
    }

    /** A ZoneOffset supports exactly the OFFSET_SECONDS field, like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public boolean isSupported(TemporalField field) {
        return field == ChronoField.OFFSET_SECONDS;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public long getLong(TemporalField field) {
        if (field == ChronoField.OFFSET_SECONDS) {
            return totalSeconds;
        }
        throw fail("bmc4j: unmodelled member java.time.ZoneOffset.getLong(java.time.temporal.TemporalField) — only OFFSET_SECONDS is modeled for the total-seconds offset");
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public int get(TemporalField field) {
        if (field == ChronoField.OFFSET_SECONDS) {
            return totalSeconds;
        }
        throw fail("bmc4j: unmodelled member java.time.ZoneOffset.get(java.time.temporal.TemporalField) — only OFFSET_SECONDS is modeled for the total-seconds offset");
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    @Override
    public ValueRange range(TemporalField field) {
        if (field == ChronoField.OFFSET_SECONDS) {
            return field.range();
        }
        throw fail("bmc4j: unmodelled member java.time.ZoneOffset.range(java.time.temporal.TemporalField) — only OFFSET_SECONDS is modeled for the total-seconds offset");
    }

    @BmcUnmodelable(reason = "applying a ZoneOffset to a Temporal (adjustInto) is out of scope for the total-seconds offset model")
    @Override
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.ZoneOffset.adjustInto(java.time.temporal.Temporal) — applying a ZoneOffset to a Temporal is out of scope for the total-seconds offset model");
    }

    @BmcUnmodelable(reason = "the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor")
    @Override
    public <R> R query(TemporalQuery<R> query) {
        throw fail("bmc4j: unmodelled member java.time.ZoneOffset.query(java.time.temporal.TemporalQuery) — the open-ended TemporalQuery lambda surface can run arbitrary unmodeled extraction over the accessor");
    }
}
