package proofs.recursion;

import example.recursion.Recursive;
import example.recursion.RecursiveNaive;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Recursion via contracts. The module bound is 6, but the recursion goes to
 * depth 12. The auto-generated {@code RecursiveContract__BmcEnforce.enforce__sumTo} proves the
 * inductive step (the recursive call is summarized by the contract), and callers then reuse
 * the closed form for free. The no-contract baseline can't: it must unroll all 12 levels.
 */
class RecursionProofs {

    /**
     * PASSES at unwind 4. The recursive {@code sumTo} is replaced by its contract, so the
     * caller gets {@code result == n*(n+1)/2} without unrolling 12 levels of recursion.
     */
    @BmcProof(unwind = 4)
    void caller_reuses_the_recursive_contract() {
        int n = Bmc.anyInt(0, 12);
        Bmc.check(Recursive.sumTo(n) == n * (n + 1) / 2);
    }

    /**
     * UNDECIDED at the same bound. No contract -> the real recursion is inlined to depth 12,
     * which overruns unwind 4 (a "recursion unwinding assertion" — the recursion flavour of
     * bound-too-small, UNKNOWN like the loop one). Same property, provable only with the contract.
     */
    // Expected verdict: UNKNOWN - uncontracted recursion exceeds the unwind bound
    // (truncated exploration is incompleteness, never REFUTED).
    @BmcProof(unwind = 4, expect = Verdict.UNKNOWN)
    void without_a_contract_recursion_exceeds_the_bound() {
        int n = Bmc.anyInt(0, 12);
        Bmc.check(RecursiveNaive.sumTo(n) == n * (n + 1) / 2);
    }
}
