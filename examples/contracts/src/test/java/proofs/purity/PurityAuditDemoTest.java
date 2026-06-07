package proofs.purity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import example.purity.Ledger;
import org.junit.jupiter.api.Test;

/**
 * The purity-audit demo, at the production-truth level (no JBMC — deterministic, like
 * {@code proofs.whenexpr.GradeGapReplayReproductionTest}). It pins, in plain Java, the
 * unsoundness the audit closes: a contract on {@code Ledger.record} would redirect callers to a
 * stub that returns a nondet value constrained only by {@code @Ensures} and <em>never touches</em>
 * {@code Ledger.total} — so the mutation a real caller observes is silently dropped.
 *
 * <p>The contract {@code contracts.purity.LedgerContract} is rejected by bmc4j's purity audit at
 * proof time with a {@code ContractPurityError} naming the {@code PUTSTATIC Ledger.total}
 * instruction; its generated enforce-proof is excluded from this module's {@code test} task
 * accordingly (see {@code build.gradle.kts}). This test documents <em>why</em> that rejection is
 * sound: it shows the side effect is real and caller-observable, exactly what a contract's
 * replace-stub would erase.
 */
class PurityAuditDemoTest {

    /**
     * {@code Ledger.record} has a caller-observable side effect: it mutates the pre-existing static
     * {@code Ledger.total}. A contract that summarized only its return value would drop this — the
     * false green the purity audit refuses to allow. Here we observe the effect directly.
     */
    @Test
    void record_mutates_preexisting_state_which_a_contract_stub_would_silently_drop() {
        Ledger.total = 0;

        int returnedFirst = Ledger.record(5);
        // The real method both returns the new total AND mutated the global — an effect a
        // return-value-only summary cannot represent.
        assertEquals(5, returnedFirst, "real body returns the running total");
        assertEquals(5, Ledger.total, "real body mutated pre-existing global state (the dropped effect)");

        int returnedSecond = Ledger.record(3);
        assertEquals(8, returnedSecond, "the result depends on prior mutations, not just the input");
        assertEquals(8, Ledger.total, "the side effect accumulates — provably not a function of inputs");

        // Same input, different result: the method is not pure, so it is not a legal contract target.
        // (A stubbed caller would see neither this state change nor this input-history dependence.)
        Ledger.total = 100;
        assertNotEquals(returnedFirst, Ledger.record(5),
                "record(5) returns 105 now, not 5 — its output depends on mutable global state");
    }
}
