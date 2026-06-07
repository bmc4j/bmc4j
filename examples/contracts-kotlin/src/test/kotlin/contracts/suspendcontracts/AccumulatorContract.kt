package contracts.suspendcontracts

import example.suspendcontracts.Accumulator
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.Requires

/**
 * A contract on the impure `suspend` [Accumulator.add]. Its `@Ensures` happens to hold, so the
 * enforce-proof alone would PASS — the false green the purity audit refuses. Because `add` mutates the
 * receiver (`this.total`) underneath the coroutine plumbing, the audit rejects this contract at proof
 * time with a `ContractPurityError` naming the `PUTFIELD` — an UNCONDITIONAL build failure (no
 * `@ExpectEnforce` can bless an impure target), proving the coroutine allowance list did NOT blanket-
 * allow real impurity inside a suspend body.
 *
 * The generated `enforce__add` is therefore EXCLUDED from this module's `test` task (see
 * `build.gradle.kts`); `proofs.suspendcontracts.SuspendPurityAuditDemoTest` documents the rejection
 * deterministically. Removing that exclusion is itself the regression check.
 */
@BmcContractsFor(Accumulator::class)
interface AccumulatorContract {

    @Requires("nonNeg")
    @Ensures("grew")
    suspend fun add(amount: Int): Int

    companion object {
        @JvmStatic fun nonNeg(self: Accumulator, amount: Int): Boolean = amount in 0..1000
        @JvmStatic fun grew(result: Int, self: Accumulator, amount: Int): Boolean = result >= self.total
    }
}
