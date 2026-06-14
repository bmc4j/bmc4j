package proofs.branchdecompose;

import org.bmc4j.Bmc;
import org.bmc4j.BmcBranchDecompose;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * End-to-end demos of {@code @BmcBranchDecompose}: AUTOMATIC, SOUND branch decomposition. bmc4j
 * DISCOVERS the first top-level value branch in the proof method by CFG analysis (you mark nothing),
 * EXTRACTS it into a separately-proven synthetic method, proves it against an automatically-derived
 * SUMMARY of its input/output relation (the LEAF run), and discharges that summary back into the
 * PARENT run at the call site - so the parent never re-explores the branch's control flow.
 *
 * <p>Leaf + parent are an assume-guarantee pair proven concurrently; the proof passes iff BOTH
 * VERIFIED. The summary is the branch's EXACT relation, so the LEAF certifies the summary is sound and
 * the PARENT proves the property given it - as precise as inlining. A wrong branch VALUE flows through
 * the exact summary into the parent's check and is caught there; a wrong remainder is caught there too.
 */
class BranchDecomposeProofTests {

    /**
     * PASSES via decomposition. {@code clamp(x) in -10..10} holds for every {@code x}. bmc4j extracts
     * the {@code ?:} clamp branch, the leaf certifies its summary, and the parent proves the trailing
     * check given that summary. Both VERIFIED over all of {@code int}: a full-domain proof.
     */
    @BmcProof
    @BmcBranchDecompose
    void clamp_stays_in_range_branch_extracted_and_discharged() {
        int x = Bmc.anyInt();
        int r = (x < -10) ? -10 : (x > 10) ? 10 : x; // discovered + extracted branch
        Bmc.check(r >= -10 && r <= 10);
    }

    /**
     * SOUNDNESS GUARD: a wrong VALUE in the extracted branch must surface. The branch mis-handles
     * {@code x == 0} (returns 1, should be 0); the parent, assuming the EXACT summary, finds the
     * trailing equality FALSE at {@code x == 0}, so the aggregate REFUTES. The decomposition can never
     * hide a counterexample that lives in the extracted branch.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    void a_wrong_value_in_the_extracted_branch_is_refuted() {
        int x = Bmc.anyInt();
        int r = (x < 0) ? -1 : (x == 0) ? 1 : 1;        // wrong at x == 0
        int expected = (x < 0) ? -1 : (x == 0) ? 0 : 1;
        Bmc.check(r == expected);                        // FALSE at x == 0; surfaces via the summary
    }

    /**
     * SOUNDNESS GUARD for the PARENT: a bug in the NON-branch remainder is caught by the parent. The
     * clamp branch is correct, but the trailing check is wrong (claims {@code r in -5..5}), so the
     * parent - which proves the remainder given the branch's summary - REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    void a_bug_in_the_parent_remainder_is_refuted() {
        int x = Bmc.anyInt(-1000, 1000);
        int r = (x < -10) ? -10 : (x > 10) ? 10 : x;    // correct clamp branch
        Bmc.check(r >= -5 && r <= 5);                    // FALSE: clamp can return 8
    }
}
