package proofs.stacking;

import example.stacking.Chain;
import example.stacking.ChainNaive;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Stacking contracts. Three recursive functions chained f -> g -> h, each to
 * depth 10, at a module bound of 5. The processor auto-generates one enforce proof per level
 * ({@code enforce__f}, {@code enforce__g}, {@code enforce__h}), each discharged using the
 * contract of the level below — so all three pass at the low bound. A caller then reuses the
 * top of the stack for free; the no-contract baseline has to inline all three and can't.
 */
class StackingProofs {

    /**
     * PASSES at unwind 3. Calling {@code f} is a single contract lookup — the entire
     * f -> g -> h stack is summarized, so depth and chaining are irrelevant to the caller.
     */
    @BmcProof(unwind = 3)
    void caller_reuses_the_whole_stack() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(Chain.f(n) == n * (n + 1) * (n + 2) * (n + 3) / 24);
    }

    /**
     * FAILS at the same bound. No contracts -> the caller inlines f, which inlines g, which
     * inlines h, each recursing to depth 10 — far past unwind 3.
     */
    // Expected verdict: REFUTED - the uncontracted call stack blows the bound.
    @BmcProof(unwind = 3, expect = Verdict.REFUTED)
    void without_contracts_the_stack_is_intractable() {
        int n = Bmc.anyInt(0, 10);
        Bmc.check(ChainNaive.f(n) == n * (n + 1) * (n + 2) * (n + 3) / 24);
    }
}
