package example.jakarta;

import java.math.BigDecimal;

/** Business logic over a {@link Money}. */
public final class Prices {

    private Prices() {
    }

    private static final BigDecimal MAX = new BigDecimal("1000.00");

    /**
     * BUG: claims the amount is always strictly UNDER the cap. But {@code @DecimalMax("1000.00")} is
     * inclusive, so exactly 1000.00 is valid — a boundary value this rejects. Refuted through the
     * generated assume.
     */
    public static boolean amountUnderCap(Money m) {
        return m.amount == null || m.amount.compareTo(MAX) < 0;
    }

    /** The fix: the cap is inclusive. Provably true for every valid Money. */
    public static boolean amountAtMostCap(Money m) {
        return m.amount == null || m.amount.compareTo(MAX) <= 0;
    }

    /**
     * BUG: claims the fee is always non-negative-or-zero meaning {@code >= 0}; true, but the demo
     * claims the STRICTER {@code fee != 0} matters elsewhere. We pin that {@code @DecimalMin(0,
     * inclusive=false)} rules 0 OUT: this returns true for every valid fee (strictly positive).
     */
    public static boolean feeIsStrictlyPositive(Money m) {
        return m.fee == null || m.fee.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * BUG: claims the price's scale (fraction digits) is at most 1. But {@code @Digits(fraction = 2)}
     * admits 2 fraction digits, so a valid price like 1.99 has scale 2 — refuting this.
     */
    public static boolean priceHasAtMostOneFractionDigit(Money m) {
        return m.price == null || m.price.scale() <= 1;
    }

    /**
     * BUG: claims the price's integer part is at most 4 digits (< 10000). But
     * {@code @Digits(integer = 5)} admits up to 5 integer digits, so 99999.99 is valid and refutes
     * this. Pins the integer bound independently of the fraction bound.
     */
    public static boolean priceIntegerPartUnder10000(Money m) {
        return m.price == null
                || m.price.toBigInteger().abs().compareTo(java.math.BigInteger.valueOf(10000L)) < 0;
    }
}
