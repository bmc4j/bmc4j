package proofs.branchdecompose

import org.bmc4j.Bmc
import org.bmc4j.BmcBranchDecompose
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.coldBranch

/**
 * `@BmcBranchDecompose` + `coldBranch(...)`: SOUND branch decomposition. bmc4j extracts the marked cold
 * branch into a separately-proven LEAF (the body under `assume(cond)` - the branch path-condition as
 * its precondition) and discharges its trivial summary back into the PARENT (the body under
 * `assume(!cond)`). Leaf + parent cover `cond || !cond`, the full domain, so the proof passes iff BOTH
 * VERIFIED - a sound case split, NOT dead-branch pruning. The two runs prove concurrently on the shared
 * jbmc pool, and a localized-cost breakdown is printed naming the hot obligation.
 */
class BranchDecomposeProofs {

    /**
     * VERIFIES via decomposition AND demonstrates the discharge is SOUND. The property
     * `clamp(x) in -10..10` holds for every `x`. With the cold branch `x == Int.MIN_VALUE` marked:
     * - the LEAF re-proves the property under `assume(x == Int.MIN_VALUE)` (the extreme input), and
     * - the PARENT re-proves it under `assume(x != Int.MIN_VALUE)` (every other input).
     * Both VERIFIED, and together they cover all of `Int`, so the green is a full-domain proof - the
     * discharge added no unsound assumption (the leaf actually proved the branch it pruned from the
     * parent).
     */
    @BmcProof
    @BmcBranchDecompose
    fun `clamp stays in range, cold extreme branch extracted and discharged soundly`() {
        val x = Bmc.anyInt()
        coldBranch(x == Int.MIN_VALUE) // the cold, extreme branch
        val r = if (x < -10) -10 else if (x > 10) 10 else x
        Bmc.check(r in -10..10) // holds in the leaf (x==MIN_VALUE) AND the parent (x!=MIN_VALUE)
    }

    /**
     * The SOUNDNESS GUARD for the discharge: if the property were FALSE on the cold branch, the LEAF
     * run (which proves exactly that branch) must REFUTE it - the decomposition can never hide a
     * counterexample that lives in the extracted branch. Here `buggy(x)` mis-handles `x == 0` (the
     * marked cold branch), so the leaf REFUTES at `x == 0` while the parent verifies the rest. The
     * aggregate is REFUTED, proving the leaf genuinely proves its branch rather than assuming it away.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a false property on the extracted branch is refuted by the leaf`() {
        val x = Bmc.anyInt()
        coldBranch(x == 0) // the marked branch where the bug lives
        // buggy(x): returns 1 for x == 0 (wrong - should be 0), correct elsewhere.
        val buggy = if (x == 0) 1 else if (x < 0) -1 else 1
        val expected = if (x < 0) -1 else if (x == 0) 0 else 1
        Bmc.check(buggy == expected) // FALSE at x == 0, which lives in the extracted leaf
    }

    /**
     * The SOUNDNESS GUARD for the parent: a counterexample in the NON-cold remainder must be refuted by
     * the PARENT run. `buggy(x)` is wrong at `x == 7` (in the parent's `x != MIN_VALUE` sub-domain), so
     * the parent REFUTES while the leaf verifies its branch. Confirms the parent really re-proves the
     * remainder rather than trusting the leaf for the whole domain.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a false property in the parent remainder is refuted by the parent`() {
        val x = Bmc.anyInt()
        coldBranch(x == Int.MIN_VALUE)
        Bmc.assume(x in -1000..1000) // keep the parent tractable; 7 is in this range, MIN_VALUE is not
        val buggy = if (x == 7) 999 else x // wrong only at x == 7 (in the parent's sub-domain)
        Bmc.check(buggy == x)
    }

    /**
     * EXERCISES THE FAN-OUT + LOCALIZED-COST REPORT. The proof verifies; the marked cold branch sends the
     * leaf and the parent to two independent jbmc runs that prove CONCURRENTLY on the shared pool, and
     * the harness prints a `bmc4j[branch-decompose]: ... localized-cost breakdown` line that reports each
     * obligation's engine formula size (SAT clauses / VCCs / program steps) and wall-clock, picking the
     * most-discriminating metric for its "cost-follows-extraction" call. The verdict (a full-domain
     * green) is what this pins; the breakdown is emitted to stdout for inspection. (Whether the cost
     * actually LOCALIZES is engine-dependent: on a small proof jbmc's symex + slicing often minimises
     * both arms so the report honestly says "spread"; the report localises when the leaf and parent
     * formulas genuinely diverge - it never fabricates a hot spot.)
     */
    @BmcProof
    @BmcBranchDecompose
    fun `fan out leaf and parent and emit the localized-cost report`() {
        val x = Bmc.anyInt()
        coldBranch(x == Int.MIN_VALUE)
        val r = if (x < -5) -5 else if (x > 5) 5 else x
        Bmc.check(r in -5..5) // holds in the leaf and the parent
    }
}
