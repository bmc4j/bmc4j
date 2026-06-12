package contracts.purity

import example.purity.Ledger
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.Requires

/**
 * A contract on the impure [Ledger.record], in the standard **object-host** shape (a plain `object`
 * with ordinary member `fun` predicates). Its `@Ensures` happens to be true (the new total is at least
 * the old, for non-negative input), so the enforce-proof alone would PASS — which is exactly the false
 * green the purity audit refuses to allow. Because `record` mutates the receiver (`this.total`), the
 * audit rejects this contract at proof time with a `ContractPurityError` naming the `PUTFIELD` — an
 * UNCONDITIONAL build failure (no `@ExpectEnforce` can bless an impure target).
 *
 * The generated `enforce__record` is therefore EXCLUDED from this module's `test` task (see
 * `build.gradle.kts`); `proofs.purity.PurityAuditDemoTest` documents the rejection deterministically.
 * Removing that exclusion is itself the regression check — the build then goes red with the audit's
 * message.
 */
@BmcContractsFor(Ledger::class)
object LedgerContract {

    @Requires("nonNeg")
    @Ensures("grew")
    fun record(amount: Int): Int = error("mirror")

    // Plain member predicates — no companion, no @JvmStatic.
    fun nonNeg(self: Ledger, amount: Int): Boolean = amount in 0..1000
    fun grew(result: Int, self: Ledger, amount: Int): Boolean = result >= self.total
}
