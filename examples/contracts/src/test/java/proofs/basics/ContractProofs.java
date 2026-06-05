package proofs.basics;

import example.basics.Triangle;
import example.basics.TriangleNaive;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Method contracts, the basic shape. Alongside these hand-written proofs, the
 * processor auto-generates {@code TriangleContract__BmcEnforce.enforce__triangle} — which discharges
 * {@code @Ensures} against the REAL loop body and shows up green in the same report. Annotating
 * is therefore not the same as asserting: the contract carries its own proof obligation.
 */
class ContractProofs {

    /**
     * PASSES at a tiny bound. {@code unwind = 2} is far too small to inline triangle's loop
     * (n up to 8) — this only verifies because the call is redirected to the contract summary,
     * so the caller relies on {@code @Ensures result >= 0} instead of re-analyzing the loop.
     */
    @BmcProof(unwind = 2)
    void caller_reuses_the_contract_at_a_tiny_bound() {
        int n = Bmc.anyInt(0, 8);
        int t = Triangle.triangle(n);   // redirected to TriangleContract__BmcStubs.triangle__stub
        Bmc.check(t >= 0);
    }

    /**
     * FAILS at the same bound. Identical loop, but {@code TriangleNaive} has no contract, so
     * the real loop is inlined and overruns {@code unwind = 2}. This is the baseline the
     * contract improves on — same code, same bound, but provable only with the summary.
     */
    // Expected verdict: REFUTED - without the contract the callee exceeds the unwind bound.
    @BmcProof(unwind = 2, expect = Verdict.REFUTED)
    void without_a_contract_the_same_bound_is_too_small() {
        int n = Bmc.anyInt(0, 8);
        Bmc.check(TriangleNaive.triangle(n) >= 0);
    }
}
