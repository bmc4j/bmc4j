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

    // Range note (same as the Java twin in proofs.custommodels): the cap proof multiplies TWO
    // symbolic ints (amount x rate). eur_is_never_negative stays bounded to $0..$100 (range
    // reduction, see docs/performance.md). eur_within_rate_cap runs at the FULL $0..$1000 range
    // via a domainSplit instead: the [0, 100_000] domain is partitioned into 8 cent-bands, each
    // ~1/8th the SAT search and fanned across cores, so the wide range stays affordable. The lever
    // is per-slice INTERVAL size, not bit-width, so a plain sub-range split is the right tool.

    @BmcProof
    fun eur_is_never_negative() {
        val usdCents = Bmc.anyInt(0, 10_000)
        val eur = Pricer(ExchangeRates()).eurCents(usdCents)
        Bmc.check(eur >= 0)
    }

    @BmcProof
    fun eur_within_rate_cap() {
        val usdCents = Bmc.anyInt(0, 100_000)
        Bmc.domainSplit(usdCents in 0..100_000)
        Bmc.slice(usdCents in 0..12_499)
        Bmc.slice(usdCents in 12_500..24_999)
        Bmc.slice(usdCents in 25_000..37_499)
        Bmc.slice(usdCents in 37_500..49_999)
        Bmc.slice(usdCents in 50_000..62_499)
        Bmc.slice(usdCents in 62_500..74_999)
        Bmc.slice(usdCents in 75_000..87_499)
        Bmc.slice(usdCents in 87_500..100_000)
        val eur = Pricer(ExchangeRates()).eurCents(usdCents)
        Bmc.check(eur <= usdCents * 2)
    }
}
