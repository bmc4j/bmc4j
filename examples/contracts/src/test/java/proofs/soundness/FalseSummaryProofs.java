package proofs.soundness;

import example.soundness.Deltas;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * End-to-end guard that a <b>non-VERIFIED contract is never reused as a trusted summary</b>.
 *
 * <p>{@code contracts.soundness.DeltasContract} marks {@code delta}'s {@code @Ensures("neverNegative")}
 * as {@code @ExpectEnforce(REFUTED)} — the framework KNOWS it is false ({@code a - b} is negative
 * whenever {@code a < b}). This caller calls {@code Deltas.delta} and asserts exactly that false
 * postcondition ({@code result >= 0}), a property that is true ONLY under the bogus summary.
 *
 * <ul>
 *   <li><b>If the bug were present</b> (a redirect published for the REFUTED contract), the call would
 *       be rewritten to {@code delta__stub}, which {@code assume(result >= 0)} — so the assertion
 *       would VERIFY against the false summary. With {@code expect = REFUTED} that mismatch is a loud
 *       FAIL: a false green caught.</li>
 *   <li><b>With the fix</b>, no redirect is published, so the real body {@code a - b} is analysed and
 *       the assertion is correctly REFUTED ({@code a < b} is a counterexample) — matching
 *       {@code expect = REFUTED}, so this proof is green.</li>
 * </ul>
 *
 * <p>Inputs stay inside the contract's {@code @Requires bounded} domain so the comparison is fair to
 * both directions (the stub's precondition {@code assert} is satisfiable; the real body still has a
 * counterexample in-range).
 */
class FalseSummaryProofs {

    /**
     * A non-VERIFIED contract must NOT weaken a caller. The real {@code delta} can return a negative
     * value, so asserting {@code >= 0} is correctly REFUTED. Were the REFUTED contract reusable as a
     * stub, the {@code assume(result >= 0)} summary would make this VERIFY — a false green this
     * {@code expect = REFUTED} would then catch.
     */
    // Expected verdict: REFUTED - delta's @Ensures is deliberately false, so its summary must not be
    // reusable; the real body (a - b) refutes result >= 0 for a < b.
    @BmcProof(expect = Verdict.REFUTED)
    void a_refuted_contract_must_not_weaken_a_caller() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        Bmc.check(Deltas.delta(a, b) >= 0);
    }
}
