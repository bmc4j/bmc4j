package contracts.purity;

import example.purity.Ledger;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.Requires;

/**
 * A contract on an <b>impure</b> method, for the purity-audit demo. The {@code @Ensures} here is
 * actually <em>true</em> of the return value ({@code Ledger.record} does return a value
 * {@code >= the amount} for non-negative inputs), so a naive enforce-proof would go GREEN —
 * which is precisely the trap: the contract says nothing about, and the replace-stub silently
 * drops, the method's mutation of {@code Ledger.total}.
 *
 * <p>The <b>purity audit</b> rejects this contract before any proof can reuse it: certifying
 * {@code Ledger.record} reaches a {@code PUTSTATIC} of {@code Ledger.total} (a heap write to
 * pre-existing state), so it fails with a {@code ContractPurityError} naming that instruction.
 * That rejection is what {@code proofs.purity.PurityAuditDemoTest} asserts.
 *
 * <p>The generated {@code LedgerContract__BmcEnforce} enforce-proof is therefore <em>expected to be
 * rejected</em>, not run as an ordinary proof — the module's {@code test} task excludes it (see
 * {@code build.gradle.kts}); the hand-written demo test drives the rejection deterministically.
 */
@BmcContractsFor(Ledger.class)
interface LedgerContract {

    @Requires("nonNegative")
    @Ensures("atLeastAmount")
    int record(int amount);

    static boolean nonNegative(int amount) {
        return amount >= 0 && amount <= 1_000;
    }

    static boolean atLeastAmount(int result, int amount) {
        // True of the RETURN value alone — yet the method is impure, which the audit catches.
        return result >= amount;
    }
}
