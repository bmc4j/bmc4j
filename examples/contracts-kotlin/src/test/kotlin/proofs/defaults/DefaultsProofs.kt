package proofs.defaults

import example.defaults.Discount
import org.bmc4j.Bmc
import org.bmc4j.Bmc.anyInt
import org.bmc4j.BmcProof

/**
 * Default-parameter contracts, the caller side. Two callers exercise the two JVM methods Kotlin
 * emits: one passes the defaulted argument explicitly (hits the real `price`), one omits it (hits
 * `price$default`, which fills in the default and calls the real `price`). BOTH pass at `unwind = 2`,
 * proving the redirect summarizes the contracted call on either path — the synthetic `$default`
 * wrapper's internal call to `price` is redirected too, so the loop is never inlined.
 */
class DefaultsProofs {

    /** Explicit argument: the call is a direct `invokevirtual price(qty, rebate)`. */
    @BmcProof(unwind = 2)
    fun `explicit-arg caller reuses the contract`() {
        val base = anyInt(0, 1000)
        val qty = anyInt(0, 8)
        val d = Discount(base)
        val p = d.price(qty, 0)
        Bmc.check(p >= base)
    }

    /** Omitted argument: Kotlin routes through `price$default`, whose internal call to the real
     *  `price` is itself redirected — so this also passes at the tiny bound. */
    @BmcProof(unwind = 2)
    fun `omitted-default caller reuses the contract through the synthetic`() {
        val base = anyInt(0, 1000)
        val qty = anyInt(0, 8)
        val d = Discount(base)
        val p = d.price(qty) // rebate defaulted -> price$default
        Bmc.check(p >= base)
    }
}
