package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalField;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.Month} — the twelve-constant month enum, in calendar order
 * (JANUARY=1 .. DECEMBER=12). The modeled surface is the integer arithmetic over the 1-based value:
 * {@code getValue}, the {@code of(int)} factory (loud {@link DateTimeException} out of [1,12]), the
 * modular {@code plus(long)}/{@code minus(long)} month rotation (an explicit non-negative remainder
 * keeps it JBMC-sound, unlike the unmodeled Math.floorMod intrinsic), {@code length(boolean)} (with the
 * Feb leap branch), {@code minLength}/{@code maxLength}, and {@code firstMonthOfQuarter}. All are
 * validated bit-for-bit by the differential suite vs the real JDK.
 *
 * <p>It {@code implements java.time.temporal.TemporalAccessor, java.time.temporal.TemporalAdjuster} —
 * the real JDK interfaces — ONLY so a Month survives the {@code checkcast} the JDK-compiled proof
 * bytecode emits when one is passed to an interface-typed parameter (the now-fixed "Dynamic cast check"
 * artifact). The interface abstract methods are NOT modeled — each is a LOUD stub so reaching it is a
 * NAMED UNKNOWN, never a silent nondet: implementing the interface buys only {@code instanceof}.
 *
 * <p>NB: NO class-level {@code @BmcModelTail} here (same reason as {@link DayOfWeek}): an enum's real
 * surface includes {@code java.lang.Enum}'s FINAL members + the synthetic {@code values}/{@code valueOf},
 * which the tail loud-body synthesis would override and break the enum. The remaining
 * TemporalAccessor/TemporalAdjuster surface ({@code get}/{@code query}/{@code range} are interface defaults;
 * {@code from}/{@code firstDayOfYear}/{@code getDisplayName} are out of scope) needs no synthesized body —
 * this is a class-level COVERED enum, and the required interface abstracts are the loud stubs below.
 */
public enum Month implements TemporalAccessor, TemporalAdjuster {
    JANUARY,
    FEBRUARY,
    MARCH,
    APRIL,
    MAY,
    JUNE,
    JULY,
    AUGUST,
    SEPTEMBER,
    OCTOBER,
    NOVEMBER,
    DECEMBER;

    /** 1-based value: JANUARY=1 .. DECEMBER=12 (ordinal + 1). */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getValue() {
        return ordinal() + 1;
    }

    /** Factory from the 1-based value; loud {@link DateTimeException} out of [1,12], like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Month of(int month) {
        if (month < 1 || month > 12) {
            throw new DateTimeException("Invalid value for MonthOfYear: " + month);
        }
        return values()[month - 1];
    }

    /**
     * Rotate forward by {@code months}, wrapping modulo 12. The JDK uses {@code Math.floorMod}; we inline
     * a non-negative remainder so the rotation stays JBMC-sound. Bit-identical to the JDK across negative
     * and large counts.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Month plus(long months) {
        int amount = (int) (months % 12);
        return values()[(ordinal() + (amount + 12)) % 12];
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Month minus(long months) {
        return plus(-(months % 12));
    }

    /** Days in this month; February is 29 in a leap year else 28, per the JDK's {@code length}. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int length(boolean leapYear) {
        switch (this) {
            case FEBRUARY:
                return leapYear ? 29 : 28;
            case APRIL:
            case JUNE:
            case SEPTEMBER:
            case NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    /** Minimum length in days (February is 28). */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int minLength() {
        switch (this) {
            case FEBRUARY:
                return 28;
            case APRIL:
            case JUNE:
            case SEPTEMBER:
            case NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    /** Maximum length in days (February is 29). */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int maxLength() {
        switch (this) {
            case FEBRUARY:
                return 29;
            case APRIL:
            case JUNE:
            case SEPTEMBER:
            case NOVEMBER:
                return 30;
            default:
                return 31;
        }
    }

    /** The first month of this month's calendar quarter (Jan/Apr/Jul/Oct), per the JDK's table. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Month firstMonthOfQuarter() {
        return values()[(ordinal() / 3) * 3];
    }

    // --- TemporalAccessor / TemporalAdjuster abstract surface: implemented ONLY to make a Month an
    //     instanceof those interfaces (so the proof-site checkcast passes); each is LOUD, never modeled. ---

    @BmcUnmodelable(reason = "the TemporalField query plumbing (isSupported) is out of scope for this 1-based-value enum model")
    @Override
    public boolean isSupported(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.Month.isSupported(java.time.temporal.TemporalField) — the TemporalField query plumbing is out of scope for this 1-based-value enum model");
    }

    @BmcUnmodelable(reason = "the TemporalField accessor (getLong) is out of scope for this 1-based-value enum model")
    @Override
    public long getLong(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.Month.getLong(java.time.temporal.TemporalField) — the TemporalField accessor is out of scope for this 1-based-value enum model");
    }

    @BmcUnmodelable(reason = "applying a Month to a Temporal (adjustInto) is out of scope for this 1-based-value enum model")
    @Override
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.Month.adjustInto(java.time.temporal.Temporal) — applying a Month to a Temporal is out of scope for this 1-based-value enum model");
    }
}
