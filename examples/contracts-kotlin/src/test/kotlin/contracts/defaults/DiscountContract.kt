package contracts.defaults

import example.defaults.Discount
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.Requires

/**
 * Contract for [Discount.price], a Kotlin instance method with a default parameter, in the standard
 * **object-host** shape (a plain `object` with ordinary member `fun` predicates). The mirror declares
 * BOTH parameters — it binds to the real `price(Int, Int)`, not the `price$default` synthetic. The
 * processor needs no knowledge of default parameters: it contracts the real method, and the bytecode
 * redirect (applied to every call site, including the one inside `price$default`) does the rest.
 */
@BmcContractsFor(Discount::class)
object DiscountContract {

    @Requires("ok")
    @Ensures("atLeastFloor")
    fun price(qty: Int, rebate: Int): Int = error("mirror")

    // Plain member predicates — no companion, no @JvmStatic.
    fun ok(self: Discount, qty: Int, rebate: Int): Boolean =
            self.base in 0..1000 && qty in 0..8 && rebate in 0..0

    fun atLeastFloor(result: Int, self: Discount, qty: Int, rebate: Int): Boolean =
            result >= self.base - rebate
}
