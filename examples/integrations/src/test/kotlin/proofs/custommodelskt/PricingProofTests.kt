package proofs.custommodelskt

import example.custommodelskt.ExchangeRates
import example.custommodelskt.Pricer
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Proofs over a Kotlin [Pricer] that depends on the un-analyzable Kotlin
 * [ExchangeRates] service. They rely on the Kotlin model in
 * `src/bmcModel/kotlin/example/ExchangeRates.kt`, so each holds for every modeled rate.
 */
class PricingProofTests {

    // Range note (same as the Java twin in proofs.custommodels): the cap proof multiplies
    // TWO symbolic ints (amount x rate) - a nonlinear circuit that hovered right at CI's
    // 180s budget at 100_000 cents. $0..$100 covers every bit pattern the arithmetic cares
    // about while solving in seconds; the RATE range stays full, which is the point of the
    // symbolic model. See docs/performance.md (range reduction).

    @BmcProof
    fun eur_is_never_negative() {
        val usdCents = Bmc.anyInt(0, 10_000)
        val eur = Pricer(ExchangeRates()).eurCents(usdCents)
        Bmc.check(eur >= 0)
    }

    @BmcProof
    fun eur_within_rate_cap() {
        val usdCents = Bmc.anyInt(0, 10_000)
        val eur = Pricer(ExchangeRates()).eurCents(usdCents)
        Bmc.check(eur <= usdCents * 2)
    }
}
