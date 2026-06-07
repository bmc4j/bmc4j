package proofs.instance;

import example.instance.Account;
import example.instance.AccountNaive;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Pure instance-method contracts, the caller side. Alongside these hand-written proofs the
 * processor auto-generates {@code AccountContract__BmcEnforce.enforce__project} — which discharges
 * {@code @Ensures} against the REAL loop body with a symbolic receiver ({@code self} nondet, so the
 * postcondition must hold for every balance) and shows up green in the same report. The receiver is
 * threaded through the contract exactly like an ordinary symbolic input.
 */
class InstanceProofs {

    /**
     * PASSES at a tiny bound. {@code unwind = 2} is far too small to inline {@code project}'s loop
     * (amount up to 8) — this only verifies because the instance call is redirected to the contract
     * summary, so the caller relies on {@code @Ensures result >= self.balance()} instead of
     * re-analyzing the loop. The redirect rewrites {@code a.project(amount)} (invokevirtual) to the
     * static stub with the receiver prepended.
     */
    @BmcProof(unwind = 2)
    void caller_reuses_the_instance_contract_at_a_tiny_bound() {
        int balance = Bmc.anyInt(0, 1000);
        int amount = Bmc.anyInt(0, 8);
        Account a = new Account(balance);
        int projected = a.project(amount);          // redirected to AccountContract__BmcStubs.project__stub
        Bmc.check(projected >= a.balance());
    }

    /**
     * UNDECIDED at the same bound. Identical loop, but {@code AccountNaive} has no contract, so the
     * real loop is inlined and overruns {@code unwind = 2} — the bound truncates exploration, which
     * is incompleteness (UNKNOWN), not a counterexample. Same code, same bound, provable only with
     * the summary.
     */
    // Expected verdict: UNKNOWN - without the contract the instance call exceeds the unwind bound
    // (bound-too-small is incompleteness, never REFUTED: nothing was proven wrong).
    @BmcProof(unwind = 2, expect = Verdict.UNKNOWN)
    void without_a_contract_the_same_bound_is_too_small() {
        int balance = Bmc.anyInt(0, 1000);
        int amount = Bmc.anyInt(0, 8);
        AccountNaive a = new AccountNaive(balance);
        Bmc.check(a.project(amount) >= a.balance());
    }
}
