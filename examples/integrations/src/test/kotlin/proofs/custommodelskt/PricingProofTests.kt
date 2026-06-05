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

    @BmcProof
    fun eur_is_never_negative() {
        val usdCents = Bmc.anyInt(0, 100_000)
        val eur = Pricer(ExchangeRates()).eurCents(usdCents)
        Bmc.check(eur >= 0)
    }

    @BmcProof
    fun eur_within_rate_cap() {
        val usdCents = Bmc.anyInt(0, 100_000)
        val eur = Pricer(ExchangeRates()).eurCents(usdCents)
        Bmc.check(eur <= usdCents * 2)
    }
}
