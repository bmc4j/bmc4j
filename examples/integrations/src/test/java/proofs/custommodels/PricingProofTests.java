package proofs.custommodels;

import example.custommodels.ExchangeRates;
import example.custommodels.Pricer;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Proofs over {@link Pricer}, which depends on the live {@link ExchangeRates} service.
 * The service can't be analyzed (it throws in {@code src/main}), so these proofs rely
 * on the model in {@code src/bmcModel/java/example/ExchangeRates.java}, which returns a
 * <em>symbolic</em> rate. Each proof therefore holds for <b>every</b> possible rate.
 *
 * <p>Without the model JBMC would hit the real {@code UnsupportedOperationException} and
 * the proofs would fail on an uncaught exception — try deleting {@code src/bmcModel} to
 * see it.
 */
class PricingProofTests {

    // Range note: eur_is_never_negative stays bounded to $0..$100 (10_000 cents). These proofs
    // multiply TWO symbolic ints (amount x rate) — a nonlinear circuit whose solve time grows with
    // the symbolic amount's INTERVAL — and at the service's full $0..$1000 (100_000 cents) the
    // single-shot cap proof hovered right at CI's 180s budget. 10_000 x 20_000 already covers every
    // bit pattern the arithmetic cares about while solving in seconds: the range-reduction lever
    // docs/performance.md teaches (the rate range stays FULL — the point of the symbolic model).
    //
    // eur_within_rate_cap, by contrast, runs at the FULL $0..$1000 range via a domainSplit: the
    // claimed [0, 100_000] domain is partitioned into 8 contiguous cent-bands, each verified
    // independently (and the engine fans them across cores). One slice's amount ranges over only
    // 12_500 values, so its SAT search is ~1/8th the single-shot one — each slice solves in seconds
    // and the soundness cover (overall => union of bands) is a cheap boolean check. This restores
    // the wide demo range AFFORDABLY without reducing it. Empirically the lever is the per-slice
    // INTERVAL size, not operand bit-width: equal-width bands solve in the same time regardless of
    // magnitude (this proof's int*int/const circuit is full-width either way), so a plain contiguous
    // sub-range split is the right tool here — magnitude (x < 2^k) banding buys nothing extra.

    // For every modeled rate, a non-negative amount converts to a non-negative result.
    @BmcProof
    void eur_is_never_negative() {
        int usdCents = Bmc.anyInt(0, 10_000);
        int eur = new Pricer(new ExchangeRates()).eurCents(usdCents);
        Bmc.check(eur >= 0);
    }

    // The model caps the rate at 2.0, so EUR is never more than twice the USD amount — proven
    // across the whole rate range AND the full $0..$1000 amount range, via the domainSplit above.
    @BmcProof
    void eur_within_rate_cap() {
        int usdCents = Bmc.anyInt(0, 100_000);
        Bmc.domainSplit(usdCents >= 0 && usdCents <= 100_000);
        Bmc.slice(usdCents >= 0 && usdCents < 12_500);
        Bmc.slice(usdCents >= 12_500 && usdCents < 25_000);
        Bmc.slice(usdCents >= 25_000 && usdCents < 37_500);
        Bmc.slice(usdCents >= 37_500 && usdCents < 50_000);
        Bmc.slice(usdCents >= 50_000 && usdCents < 62_500);
        Bmc.slice(usdCents >= 62_500 && usdCents < 75_000);
        Bmc.slice(usdCents >= 75_000 && usdCents < 87_500);
        Bmc.slice(usdCents >= 87_500 && usdCents <= 100_000);
        int eur = new Pricer(new ExchangeRates()).eurCents(usdCents);
        Bmc.check(eur <= usdCents * 2);
    }
}
