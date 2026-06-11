package proofs.jakarta;

import example.jakarta.Money;
import example.jakarta.MoneyConstraints;
import example.jakarta.Prices;
import java.math.BigDecimal;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * {@code @DecimalMin}/{@code @DecimalMax}/{@code @Digits} lower to cheap {@code BigDecimal.compareTo}
 * / {@code scale} / {@code toBigInteger} comparisons over the modeled BigDecimal — pruning the
 * search space, not the heavy setScale arithmetic.
 *
 * <p>Idiom: the BigDecimal model is an unscaled-long + scale; a FULLY symbolic BigDecimal (unbounded
 * scale) overflows the model's loud ~18-digit guard. So the demo builds each amount from a bounded
 * symbolic unscaled value at a FIXED scale (the money shape — 2 fraction digits), which keeps the
 * model in-bounds while still ranging over the boundary values the constraints care about.
 */
class DecimalProofTests {

    /** amount in 0.00..2000.00 (scale 2): straddles the @DecimalMin 0.01 and @DecimalMax 1000.00. */
    private static Money withAmount() {
        Money m = new Money();
        m.amount = BigDecimal.valueOf(Bmc.anyLong(0, 200_000), 2);
        return m;
    }

    /** fee in 0.00..10.00 (scale 2): straddles the strict @DecimalMin "0" boundary. */
    private static Money withFee() {
        Money m = new Money();
        m.fee = BigDecimal.valueOf(Bmc.anyLong(0, 1_000), 2);
        return m;
    }

    /** price in 0.00..199999.99 (scale 2): straddles the @Digits(integer=5, fraction=2) bounds. */
    private static Money withPrice() {
        Money m = new Money();
        m.price = BigDecimal.valueOf(Bmc.anyLong(0, 19_999_999), 2);
        return m;
    }

    /**
     * REFUTED: claims the amount is strictly UNDER the cap, but @DecimalMax("1000.00") is INCLUSIVE,
     * so exactly 1000.00 is a valid boundary value this rejects.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void inclusive_max_admits_the_boundary() {
        Money m = withAmount();
        MoneyConstraints.assumeValid(m);
        Bmc.check(Prices.amountUnderCap(m));
    }

    /** PASSES: the cap is inclusive — every valid amount is at most 1000.00. */
    @BmcProof(unwind = 8)
    void amount_is_at_most_the_inclusive_cap() {
        Money m = withAmount();
        MoneyConstraints.assumeValid(m);
        Bmc.check(Prices.amountAtMostCap(m));
    }

    /**
     * PASSES: @DecimalMin(value="0", inclusive=false) makes the fee STRICTLY positive — 0 is ruled
     * out. Pins the strict (inclusive=false) translation.
     */
    @BmcProof(unwind = 8)
    void strict_min_rules_out_the_boundary() {
        Money m = withFee();
        MoneyConstraints.assumeValid(m);
        Bmc.check(Prices.feeIsStrictlyPositive(m));
    }

    /** REFUTED: @Digits(fraction=2) admits scale 2, so "price.scale() <= 1" is violable. */
    @BmcProof(expect = Verdict.REFUTED)
    void digits_fraction_bound_is_individually_violable() {
        Money m = withPrice();
        MoneyConstraints.assumeValid(m);
        Bmc.check(Prices.priceHasAtMostOneFractionDigit(m));
    }

    /** REFUTED: @Digits(integer=5) admits up to 5 integer digits, so "< 10000" is violable. */
    @BmcProof(expect = Verdict.REFUTED)
    void digits_integer_bound_is_individually_violable() {
        Money m = withPrice();
        MoneyConstraints.assumeValid(m);
        Bmc.check(Prices.priceIntegerPartUnder10000(m));
    }

    /** PASSES: a null BigDecimal field passes every decimal constraint (only @NotNull rejects null). */
    @BmcProof
    void null_decimal_field_is_valid() {
        Money m = new Money();   // all fields null
        MoneyConstraints.assumeValid(m);
        Bmc.check(m.amount == null && m.fee == null && m.price == null);
    }
}
