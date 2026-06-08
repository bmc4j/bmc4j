package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalField;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.DayOfWeek} — the seven-constant day-of-week enum, in ISO-8601 order
 * (MONDAY=1 .. SUNDAY=7). The modeled surface is the integer arithmetic over the 1-based value:
 * {@code getValue}, the {@code of(int)} factory (loud {@link DateTimeException} out of [1,7]), and the
 * modular {@code plus(long)}/{@code minus(long)} day rotation (Math.floorMod-free: an explicit
 * non-negative remainder keeps it sound under JBMC, unlike the unmodeled Math.floorMod intrinsic). The
 * constants and arithmetic are validated bit-for-bit by the differential suite vs the real JDK.
 *
 * <p>It {@code implements java.time.temporal.TemporalAccessor, java.time.temporal.TemporalAdjuster} —
 * the real JDK interfaces — ONLY so a DayOfWeek survives the {@code checkcast} the JDK-compiled proof
 * bytecode emits when one is passed to an interface-typed parameter (the now-fixed "Dynamic cast check"
 * artifact). The interface abstract methods are NOT modeled — each is a LOUD stub so reaching it is a
 * NAMED UNKNOWN, never a silent nondet: implementing the interface buys only {@code instanceof}.
 *
 * <p>NB: NO class-level {@code @BmcModelTail} here. An enum's real surface includes {@code java.lang.Enum}'s
 * FINAL members ({@code name}/{@code ordinal}/{@code compareTo}/{@code getDeclaringClass}) and the synthetic
 * {@code values}/{@code valueOf}; the build's tail loud-body synthesis would emit overriding bodies for them
 * and break the enum ({@code IncompatibleClassChangeError: overrides final Enum.name()}). The
 * TemporalAccessor/TemporalAdjuster remainder ({@code get}/{@code query}/{@code range} are interface
 * defaults; {@code from}/{@code getDisplayName} are out of scope) needs no synthesized loud body — this
 * is a class-level COVERED enum, not a per-member-enforced model, so the only required interface abstracts
 * ({@code isSupported}/{@code getLong}/{@code adjustInto}) are the hand-written loud stubs below.
 */
public enum DayOfWeek implements TemporalAccessor, TemporalAdjuster {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    /** 1-based ISO value: MONDAY=1 .. SUNDAY=7 (ordinal + 1). */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getValue() {
        return ordinal() + 1;
    }

    /** Factory from the 1-based value; loud {@link DateTimeException} out of [1,7], like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static DayOfWeek of(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new DateTimeException("Invalid value for DayOfWeek: " + dayOfWeek);
        }
        return values()[dayOfWeek - 1];
    }

    /**
     * Rotate forward by {@code days}, wrapping modulo 7. The JDK uses {@code Math.floorMod}; we inline a
     * non-negative remainder ({@code (amount % 7 + 7) % 7}) so the rotation stays JBMC-sound (floorMod is
     * an unmodeled intrinsic). Result is bit-identical to the JDK across negative and large counts.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public DayOfWeek plus(long days) {
        int amount = (int) (days % 7);
        return values()[(ordinal() + (amount + 7)) % 7];
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public DayOfWeek minus(long days) {
        return plus(-(days % 7));
    }

    // --- TemporalAccessor / TemporalAdjuster abstract surface: implemented ONLY to make a DayOfWeek an
    //     instanceof those interfaces (so the proof-site checkcast passes); each is LOUD, never modeled. ---

    @BmcUnmodelable(reason = "the TemporalField query plumbing (isSupported) is out of scope for this 1-based-value enum model")
    @Override
    public boolean isSupported(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.DayOfWeek.isSupported(java.time.temporal.TemporalField) — the TemporalField query plumbing is out of scope for this 1-based-value enum model");
    }

    @BmcUnmodelable(reason = "the TemporalField accessor (getLong) is out of scope for this 1-based-value enum model")
    @Override
    public long getLong(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.DayOfWeek.getLong(java.time.temporal.TemporalField) — the TemporalField accessor is out of scope for this 1-based-value enum model");
    }

    @BmcUnmodelable(reason = "applying a DayOfWeek to a Temporal (adjustInto) is out of scope for this 1-based-value enum model")
    @Override
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.DayOfWeek.adjustInto(java.time.temporal.Temporal) — applying a DayOfWeek to a Temporal is out of scope for this 1-based-value enum model");
    }
}
