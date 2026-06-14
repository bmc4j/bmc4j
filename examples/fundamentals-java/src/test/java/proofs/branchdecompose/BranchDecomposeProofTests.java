package proofs.branchdecompose;

import org.bmc4j.Bmc;
import org.bmc4j.BmcBranchDecompose;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * End-to-end demos of {@code @BmcBranchDecompose}: AUTOMATIC, SOUND branch decomposition. bmc4j
 * DISCOVERS value branches by CFG analysis (you mark nothing) - in the proof method AND in the callees
 * the engine inlines, at any depth - EXTRACTS each into a separately-proven synthetic method, proves it
 * against an automatically-derived SUMMARY of its input/output relation (one LEAF run per branch), and
 * discharges each summary back into the caller at the call site - so no caller re-explores any branch's
 * control flow.
 *
 * <p>The N leaves + the parent are an assume-guarantee fan-out proven concurrently; the proof passes
 * iff ALL VERIFIED. The summary is the branch's EXACT relation, so each LEAF certifies its summary is
 * sound and the PARENT proves the property given them - as precise as inlining. A wrong branch VALUE
 * flows through the exact summary into the parent's check (or fails its own leaf); a wrong remainder is
 * caught in the parent.
 */
class BranchDecomposeProofTests {

    // ---- callee under test (the branches live HERE, one+ levels below the proof harness) ----

    /** clamp(x) to [-10, 10] - a top-level value branch in a CALLEE (one level below the proof). */
    static int clamp(int x) {
        return (x < -10) ? -10 : (x > 10) ? 10 : x;
    }

    /** Reached only when {@code x >= 0} (the early-return guard is the branch's PATH CONDITION). The
     *  inner value branch then computes a result the decomposition extracts under that path condition. */
    static int cappedNonNegative(int x) {
        if (x < 0) {
            return 0; // early-return guard: the branch below is reached only when x >= 0
        }
        return (x < 10) ? x : 10; // value branch under path condition x >= 0; result in [0, 10]
    }

    /** A value branch TWO levels below the proof (proof -> sign -> magnitudeSign). */
    static int magnitudeSign(int x) {
        return (x < 0) ? -1 : (x > 0) ? 1 : 0;
    }

    static int sign(int x) {
        return magnitudeSign(x); // a thin forwarder, so the branch is at depth 2 from the proof
    }

    // ---- proofs whose ENTRY only calls the SUT; the branch is decomposed in the callee ----

    /**
     * PASSES via decomposition of a CALLEE branch. The proof harness only CALLS {@code clamp}; the value
     * branch lives one level down. bmc4j walks the call graph, extracts the callee branch, the leaf
     * certifies its summary, and the parent proves the trailing check given that summary. Full-domain
     * VERIFIES - the branch is decomposed where it actually lives, not in the thin harness.
     */
    @BmcProof
    @BmcBranchDecompose
    void callee_clamp_branch_extracted_and_discharged() {
        int x = Bmc.anyInt();
        int r = clamp(x); // the decomposed branch is in clamp(), one level down
        Bmc.check(r >= -10 && r <= 10);
    }

    /**
     * PASSES via decomposition of a callee branch UNDER A PATH CONDITION. {@code cappedNonNegative}
     * reaches its value branch only when {@code x >= 0}; the leaf assumes that path condition (so it
     * certifies the summary over exactly the inputs the parent invokes the stub under), and the parent
     * proves the property. Full-domain VERIFIES.
     */
    @BmcProof
    @BmcBranchDecompose
    void callee_branch_under_a_path_condition_verifies() {
        int x = Bmc.anyInt();
        int r = cappedNonNegative(x);
        Bmc.check(r >= 0 && r <= 10);
    }

    /**
     * PASSES via decomposition of a branch TWO levels below the proof (proof -> sign -> magnitudeSign).
     * Confirms discovery recurses through the call graph to ANY depth.
     */
    @BmcProof
    @BmcBranchDecompose
    void branch_two_levels_down_verifies() {
        int x = Bmc.anyInt();
        int r = sign(x);
        Bmc.check(r >= -1 && r <= 1);
    }

    /**
     * MULTIPLE branches sliced. The proof reaches TWO callee branches (clamp + sign), each extracted and
     * proven by its own leaf, all discharged into the parent. The aggregate VERIFIES iff the parent and
     * BOTH leaves verify.
     */
    @BmcProof
    @BmcBranchDecompose
    void multiple_callee_branches_all_sliced_verify() {
        int x = Bmc.anyInt();
        int a = clamp(x);   // branch #0 (in clamp)
        int b = sign(x);    // branch #1 (in magnitudeSign, via sign)
        Bmc.check(a >= -10 && a <= 10 && b >= -1 && b <= 1);
    }

    // ---- soundness guards: a wrong value at depth must be caught REFUTED ----

    /** A buggy clamp (returns 11 above range) used to prove a wrong callee branch value is caught. */
    static int badClamp(int x) {
        return (x < -10) ? -10 : (x > 10) ? 11 : x; // wrong: 11 is out of [-10, 10]
    }

    /**
     * SOUNDNESS GUARD: a wrong VALUE in a CALLEE branch must surface, never be assumed away. The parent,
     * assuming the EXACT summary of {@code badClamp}, finds the trailing check FALSE at {@code x > 10}
     * (where the result is 11), so the aggregate REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    void a_wrong_value_in_a_callee_branch_is_refuted() {
        int x = Bmc.anyInt();
        int r = badClamp(x);
        Bmc.check(r >= -10 && r <= 10); // FALSE at x > 10; surfaces via the exact summary
    }

    /** A buggy nested branch (wrong at the deepest, path-condition-guarded arm). */
    static int badCappedNonNegative(int x) {
        if (x < 0) {
            return 0;
        }
        return (x < 10) ? x : 11; // wrong: the capped value should be 10, not 11
    }

    /**
     * SOUNDNESS GUARD at a PATH-CONDITION depth: a wrong value in the guarded callee branch is caught.
     * The branch is reached only when {@code x >= 0}; its {@code x >= 10} arm wrongly returns 11, so the
     * parent (assuming the exact summary under the path condition) REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    void a_wrong_value_under_a_path_condition_is_refuted() {
        int x = Bmc.anyInt();
        int r = badCappedNonNegative(x);
        Bmc.check(r >= 0 && r <= 10); // FALSE at x >= 10; surfaces via the exact summary
    }

    /**
     * SOUNDNESS GUARD for the PARENT remainder: a bug OUTSIDE the branch is caught by the parent. The
     * callee clamp is correct, but the trailing check is wrong (claims {@code r in -5..5}), so the
     * parent - proving the remainder given the branch's summary - REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    void a_bug_in_the_parent_remainder_is_refuted() {
        int x = Bmc.anyInt(-1000, 1000);
        int r = clamp(x);
        Bmc.check(r >= -5 && r <= 5); // FALSE: clamp can return 8
    }

    // ---- the original in-method (level 0) demos still hold ----

    /**
     * PASSES via decomposition of a branch in the PROOF METHOD itself (level 0). {@code clamp(x) in
     * -10..10} for every {@code x}. Confirms the in-method case is unchanged by the call-graph extension.
     */
    @BmcProof
    @BmcBranchDecompose
    void in_method_clamp_branch_extracted_and_discharged() {
        int x = Bmc.anyInt();
        int r = (x < -10) ? -10 : (x > 10) ? 10 : x; // discovered + extracted in the proof method
        Bmc.check(r >= -10 && r <= 10);
    }
}
