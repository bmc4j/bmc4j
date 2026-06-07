package proofs.suspendcontracts

import example.suspendcontracts.Accumulator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * The suspend purity-audit demo at the production-truth level (no JBMC — deterministic). It pins, in
 * plain Kotlin, the unsoundness the audit closes for a `suspend` target: a contract on
 * [Accumulator.add] would redirect callers to a stub that returns a nondet value constrained only by
 * `@Ensures` and never touches the receiver's `total` — so the mutation a real caller observes is
 * silently dropped, even though `add` is `suspend`.
 *
 * The contract `contracts.suspendcontracts.AccumulatorContract` is rejected by bmc4j's purity audit at
 * proof time with a `ContractPurityError` naming the `PUTFIELD` on the receiver; its generated
 * enforce-proof is excluded from this module's `test` task accordingly (see `build.gradle.kts`). This
 * test documents *why* that rejection is sound — and that the coroutine allowance list, which lets the
 * benign state-machine plumbing through, did NOT also let a real `this`-mutation through.
 */
class SuspendPurityAuditDemoTest {

    @Test
    fun `add mutates the receiver which a contract stub would silently drop`() = runBlocking {
        val acc = Accumulator()

        val first = acc.add(5)
        // The real method both returns the new total AND mutated the receiver — an effect a
        // return-value-only summary cannot represent.
        assertEquals(5, first, "real body returns the running total")
        assertEquals(5, acc.total, "real body mutated the receiver (the dropped effect)")

        val second = acc.add(3)
        assertEquals(8, second, "the result depends on prior mutations, not just the input")
        assertEquals(8, acc.total, "the side effect accumulates — provably not a function of inputs")

        // Same input, different result: not pure, so not a legal contract target.
        acc.total = 100
        assertNotEquals(first, acc.add(5),
            "add(5) returns 105 now, not 5 — its output depends on mutable receiver state")
    }
}
