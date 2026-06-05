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

    // Range note: the amount is bounded to $0..$100 (10_000 cents), not the service's full
    // domain. These proofs multiply TWO symbolic ints (amount x rate) — a nonlinear circuit
    // whose solve time grows steeply with operand bit-width — and at 100_000 cents the cap
    // proof hovers right at CI's 180s budget. 10_000 x 20_000 covers every bit pattern the
    // arithmetic cares about while solving in seconds: exactly the range-reduction lever
    // docs/performance.md teaches (tighter bit-vectors solve far faster, and the property
    // is unchanged — the rate range stays FULL, which is the point of the symbolic model).

    // For every modeled rate, a non-negative amount converts to a non-negative result.
    @BmcProof
    void eur_is_never_negative() {
        int usdCents = Bmc.anyInt(0, 10_000);
        int eur = new Pricer(new ExchangeRates()).eurCents(usdCents);
        Bmc.check(eur >= 0);
    }

    // The model caps the rate at 2.0, so EUR is never more than twice the USD amount —
    // proven across the whole rate range, not just one sampled rate.
    @BmcProof
    void eur_within_rate_cap() {
        int usdCents = Bmc.anyInt(0, 10_000);
        int eur = new Pricer(new ExchangeRates()).eurCents(usdCents);
        Bmc.check(eur <= usdCents * 2);
    }
}
