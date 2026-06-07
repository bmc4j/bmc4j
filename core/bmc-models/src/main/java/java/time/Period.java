package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;

/**
 * JBMC model of {@link java.time.Period} as an amount of time in (years, months, days), each an
 * {@code int} stored exactly as supplied (the JDK does NOT auto-normalize: {@code Period.of(0, 13, 0)}
 * keeps {@code months == 13} until {@code normalized()} is called).
 *
 * <p>Field accessors, the factories, {@code plus*}/{@code minus*} and {@code normalized()} mirror the
 * JDK exactly (validated by the differential suite). {@code Period.between(LocalDate, LocalDate)} is
 * modeled by replicating the JDK's {@code LocalDate.until} y/m/d decomposition exactly (proleptic
 * month difference, then leftover days using the end month's day count for the cross-boundary cases),
 * leaning on the now-exposed calendar fields of the epoch-day {@code LocalDate} model; it is validated
 * bit-for-bit by the differential suite vs the real JDK across month-ends, leap days and negatives.
 * Arithmetic uses {@code Math.addExact}/{@code multiplyExact} so int overflow is LOUD, like the JDK.
 */
@BmcModelTail(reason = "the TemporalAmount/Chrono plumbing (addTo/subtractFrom/get(TemporalUnit)/getUnits/getChronology/from), plus/minus(TemporalAmount) and toString are out of scope; all loud under JBMC")
public final class Period {

    public static final Period ZERO = new Period(0, 0, 0);

    private final int years;
    private final int months;
    private final int days;

    private Period(int years, int months, int days) {
        this.years = years;
        this.months = months;
        this.days = days;
    }

    @BmcNotModelled(reason = "ISO-8601 text parsing — out of scope for a bounded model (no text parsing)")
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period parse(CharSequence text) {
        throw fail("bmc4j: unmodelled member java.time.Period.parse(java.lang.CharSequence) — ISO-8601 text parsing — out of scope for a bounded model (no text parsing)");
    }

    private static Period create(int years, int months, int days) {
        if ((years | months | days) == 0) {
            return ZERO;
        }
        return new Period(years, months, days);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period of(int years, int months, int days) {
        return create(years, months, days);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period ofYears(int years) {
        return create(years, 0, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period ofMonths(int months) {
        return create(0, months, 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period ofWeeks(int weeks) {
        return create(0, 0, Math.multiplyExact(weeks, 7));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period ofDays(int days) {
        return create(0, 0, days);
    }

    /**
     * The amount of time between two dates as a {@code Period}, replicating the JDK's
     * {@code LocalDate.until(end)} y/m/d decomposition exactly: take the proleptic-month difference,
     * then the raw day-of-month difference; if those disagree in sign across a month boundary, carry
     * one month (subtracting the END month's length when the period is negative, or recomputing the
     * remaining days from the carried date when positive). Years/months are the truncating split of
     * the total months (matching the JDK), and the leftover days complete the result. The day-of-month
     * CLAMP that {@code plusMonths} applies is what makes the positive-carry branch land exactly.
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Period between(LocalDate start, LocalDate end) {
        long totalMonths = end.getProlepticMonth() - start.getProlepticMonth();
        int days = end.getDayOfMonth() - start.getDayOfMonth();
        if (totalMonths > 0 && days < 0) {
            totalMonths--;
            LocalDate calcDate = start.plusMonths(totalMonths);
            days = (int) (end.toEpochDay() - calcDate.toEpochDay());
        } else if (totalMonths < 0 && days > 0) {
            totalMonths++;
            days -= end.getLengthOfMonth();
        }
        long years = totalMonths / 12;
        int months = (int) (totalMonths % 12);
        return create(Math.toIntExact(years), months, days);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getYears() {
        return years;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getMonths() {
        return months;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int getDays() {
        return days;
    }

    /** Total months = years*12 + months (used by normalized()), with loud overflow. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toTotalMonths() {
        return years * 12L + months;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isZero() {
        return this == ZERO || (years == 0 && months == 0 && days == 0);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isNegative() {
        return years < 0 || months < 0 || days < 0;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        return create(Math.toIntExact(Math.addExact(years, yearsToAdd)), months, days);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        return create(years, Math.toIntExact(Math.addExact(months, monthsToAdd)), days);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period plusDays(long daysToAdd) {
        if (daysToAdd == 0) {
            return this;
        }
        return create(years, months, Math.toIntExact(Math.addExact(days, daysToAdd)));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period minusYears(long yearsToSubtract) {
        return plusYears(-yearsToSubtract);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period minusMonths(long monthsToSubtract) {
        return plusMonths(-monthsToSubtract);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period minusDays(long daysToSubtract) {
        return plusDays(-daysToSubtract);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public Period withYears(int years) {
        if (years == this.years) {
            return this;
        }
        return create(years, months, days);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public Period withMonths(int months) {
        if (months == this.months) {
            return this;
        }
        return create(years, months, days);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public Period withDays(int days) {
        if (days == this.days) {
            return this;
        }
        return create(years, months, days);
    }

    /** Each field scaled by {@code scalar}, with LOUD int overflow (Math.multiplyExact), like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest)")
    public Period multipliedBy(int scalar) {
        if (this == ZERO || scalar == 1) {
            return this;
        }
        return create(
            Math.multiplyExact(years, scalar),
            Math.multiplyExact(months, scalar),
            Math.multiplyExact(days, scalar));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period negated() {
        return multipliedBy(-1);
    }

    /**
     * Years and months normalized so months is in [-11, 11] with the same sign rules as the JDK:
     * the total months (years*12 + months) is split into whole years + remainder months. Days are
     * left untouched (the JDK can't normalize days without a calendar).
     */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Period normalized() {
        long totalMonths = toTotalMonths();
        long splitYears = totalMonths / 12;
        int splitMonths = (int) (totalMonths % 12);
        if (splitYears == years && splitMonths == months) {
            return this;
        }
        return create(Math.toIntExact(splitYears), splitMonths, days);
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        if (!(o instanceof Period)) {
            return false;
        }
        Period other = (Period) o;
        return years == other.years && months == other.months && days == other.days;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return years + Integer.rotateLeft(months, 8) + Integer.rotateLeft(days, 16);
    }
}
