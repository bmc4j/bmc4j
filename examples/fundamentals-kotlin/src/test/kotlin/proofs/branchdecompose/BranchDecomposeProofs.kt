package proofs.branchdecompose

import org.bmc4j.Bmc
import org.bmc4j.BmcBranchDecompose
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * `@BmcBranchDecompose`: AUTOMATIC, SOUND branch decomposition. bmc4j DISCOVERS the first top-level
 * value branch in the proof method by CFG analysis (you mark nothing), EXTRACTS it into a
 * separately-proven synthetic method, proves it against an automatically-derived SUMMARY of its
 * input/output relation (the LEAF run), and discharges that summary back into the PARENT run at the
 * call site - so the parent never re-explores the branch's control flow.
 *
 * Leaf + parent are an assume-guarantee pair proven concurrently on the shared jbmc pool; the proof
 * passes iff BOTH VERIFIED. The summary is the branch's EXACT relation, so the LEAF certifies that the
 * assumed summary is sound (the extracted branch really computes that relation) and the PARENT proves
 * the proof's property given the summary - as precise as inlining. A wrong branch VALUE flows through
 * the exact summary into the parent's check and is caught there; a wrong remainder is caught there too.
 */
class BranchDecomposeProofs {

    /**
     * VERIFIES via decomposition and shows the discharge is SOUND. `clamp(x) in -10..10` holds for
     * every `x`. bmc4j extracts the `if/else if/else` clamp branch into its own method, the leaf
     * certifies the summary against the real branch, and the parent proves the trailing check given
     * that summary. Both VERIFIED over all of `Int`, so the green is a full-domain, full-precision
     * proof - the summary added no unsound assumption.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `clamp stays in range, branch extracted and discharged soundly`() {
        val x = Bmc.anyInt()
        val r = if (x < -10) -10 else if (x > 10) 10 else x // discovered + extracted branch
        Bmc.check(r in -10..10)
    }

    /**
     * SOUNDNESS GUARD: a wrong VALUE in the extracted branch must surface, never be assumed away. The
     * branch mis-handles `x == 0` (returns 1, should be 0); the leaf certifies the summary equals THAT
     * (buggy) branch, and the parent - assuming the exact summary - finds the trailing equality FALSE
     * at `x == 0`, so the aggregate REFUTES. The decomposition can never hide a counterexample that
     * lives in the extracted branch.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a wrong value in the extracted branch is refuted`() {
        val x = Bmc.anyInt()
        // sign(x), but wrong at x == 0: returns 1 instead of 0.
        val r = if (x < 0) -1 else if (x == 0) 1 else 1
        val expected = if (x < 0) -1 else if (x == 0) 0 else 1
        Bmc.check(r == expected) // FALSE at x == 0; surfaces in the parent via the exact summary
    }

    /**
     * SOUNDNESS GUARD for the PARENT remainder: a bug OUTSIDE the branch must be caught by the parent.
     * The clamp branch is correct, but the trailing check is wrong (claims `r in -5..5` when clamp
     * bounds it to `-10..10`), so the parent - proving the remainder given the branch's summary -
     * REFUTES. Confirms the parent really re-proves the remainder rather than trusting the leaf.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a bug in the parent remainder is refuted`() {
        val x = Bmc.anyInt(-1000, 1000)
        val r = if (x < -10) -10 else if (x > 10) 10 else x // correct clamp branch
        Bmc.check(r in -5..5) // FALSE: clamp can return e.g. 8, which is in -10..10 but not -5..5
    }
}
