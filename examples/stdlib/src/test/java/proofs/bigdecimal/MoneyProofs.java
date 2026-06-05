package proofs.bigdecimal;

import example.bigdecimal.Money;
import java.math.BigDecimal;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proving money arithmetic with {@code BigDecimal}. JBMC stubs BigDecimal to nondet; bmc4j models it
 * soundly as an unscaled {@code long} + scale, so *decimal* exactness holds (unlike {@code double}).
 */
class MoneyProofs {

    // PASS: decimal addition is exact — 0.10 + 0.20 is exactly 0.30 (double gets this wrong).
    @BmcProof
    void exact_decimal_addition() {
        BigDecimal r = new BigDecimal("0.10").add(new BigDecimal("0.20"));
        Bmc.check(r.compareTo(new BigDecimal("0.30")) == 0);
    }

    // PASS: 8% tax on $12.50 is exactly $13.50.
    @BmcProof
    void tax_is_exact() {
        BigDecimal r = Money.withTax(new BigDecimal("12.50"), new BigDecimal("0.08"));
        Bmc.check(r.compareTo(new BigDecimal("13.50")) == 0);
    }

    // FAIL (the classic penny bug): splitting $10.00 three ways gives $3.33 each, and 3 × $3.33 is
    // $9.99 — a penny short. BMC finds that the shares don't recombine to the total.
    // Expected verdict: REFUTED - the seeded split loses a remainder cent.
    @BmcProof(expect = Verdict.REFUTED)
    void split_recombines_to_total() {
        BigDecimal total = new BigDecimal("10.00");
        BigDecimal share = Money.shareOf(total, 3);
        BigDecimal recombined = share.multiply(BigDecimal.valueOf(3));
        Bmc.check(recombined.compareTo(total) == 0);
    }
}
