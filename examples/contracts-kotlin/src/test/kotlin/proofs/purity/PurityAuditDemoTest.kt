package proofs.purity

import example.purity.Ledger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * The purity-audit demo at the production-truth level (no JBMC — deterministic). It pins, in plain
 * Kotlin, the unsoundness the audit closes: a contract on [Ledger.record] would redirect callers to a
 * stub that returns a nondet value constrained only by `@Ensures` and never touches the receiver's
 * `total` — so the mutation a real caller observes is silently dropped.
 *
 * The contract `contracts.purity.LedgerContract` is rejected by bmc4j's purity audit at proof time
 * with a `ContractPurityError` naming the `PUTFIELD` on the receiver; its generated enforce-proof is
 * excluded from this module's `test` task accordingly (see `build.gradle.kts`). This test documents
 * *why* that rejection is sound: the side effect is real and caller-observable, exactly what a
 * contract's replace-stub would erase.
 */
class PurityAuditDemoTest {

    @Test
    fun `record mutates the receiver which a contract stub would silently drop`() {
        val ledger = Ledger()

        val first = ledger.record(5)
        // The real method both returns the new total AND mutated the receiver — an effect a
        // return-value-only summary cannot represent.
        assertEquals(5, first, "real body returns the running total")
        assertEquals(5, ledger.total, "real body mutated the receiver (the dropped effect)")

        val second = ledger.record(3)
        assertEquals(8, second, "the result depends on prior mutations, not just the input")
        assertEquals(8, ledger.total, "the side effect accumulates — provably not a function of inputs")

        // Same input, different result: not pure, so not a legal contract target.
        ledger.total = 100
        assertNotEquals(first, ledger.record(5),
            "record(5) returns 105 now, not 5 — its output depends on mutable receiver state")
    }
}
