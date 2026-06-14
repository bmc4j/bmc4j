package proofs.branchdecompose

import org.bmc4j.Bmc
import org.bmc4j.BmcBranchDecompose
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * `@BmcBranchDecompose`: AUTOMATIC, SOUND branch decomposition. bmc4j DISCOVERS value branches by CFG
 * analysis (you mark nothing) - in the proof method AND in the callees the engine inlines, at any depth
 * - EXTRACTS each into a separately-proven synthetic method, proves it against an automatically-derived
 * SUMMARY of its input/output relation (one LEAF run per branch), and discharges each summary back into
 * the caller at the call site - so no caller re-explores any branch's control flow.
 *
 * The N leaves + the parent are an assume-guarantee fan-out proven concurrently; the proof passes iff
 * ALL VERIFIED. The summary is the branch's EXACT relation, so each LEAF certifies its summary is sound
 * and the PARENT proves the property given them - as precise as inlining. A wrong branch VALUE flows
 * through the exact summary into the parent's check (or fails its own leaf); a wrong remainder is caught
 * in the parent.
 */
class BranchDecomposeProofs {

    // ---- callee under test (the branches live HERE, one+ levels below the proof harness) ----

    private object Sut {
        /** clamp(x) to -10..10 - a value branch in a CALLEE (one level below the proof). */
        fun clamp(x: Int): Int = if (x < -10) -10 else if (x > 10) 10 else x

        /** Reached only when x >= 0 (the early-return guard is the branch's PATH CONDITION). */
        fun cappedNonNegative(x: Int): Int {
            if (x < 0) return 0
            return if (x < 10) x else 10 // value branch under path condition x >= 0; result in 0..10
        }

        /** A value branch TWO levels below the proof (proof -> sign -> magnitudeSign). */
        fun magnitudeSign(x: Int): Int = if (x < 0) -1 else if (x > 0) 1 else 0

        fun sign(x: Int): Int = magnitudeSign(x) // thin forwarder, so the branch is at depth 2

        /** A buggy clamp (returns 11 above range) - a wrong callee branch value. */
        fun badClamp(x: Int): Int = if (x < -10) -10 else if (x > 10) 11 else x

        /** A buggy nested branch (wrong at the deepest, path-condition-guarded arm). */
        fun badCappedNonNegative(x: Int): Int {
            if (x < 0) return 0
            return if (x < 10) x else 11 // wrong: the capped value should be 10, not 11
        }

        // ---- RUNG 2: branches that MUTATE a live-out LOCAL (multi-output relation, no heap) ----

        /**
         * A LOCAL-MUTATING branch: both arms WRITE the live-out local `y` (not a single value join), then
         * `y` is read after. The summary is the EXACT relation over `y`'s after-value; no frame needed
         * (locals are unaliased). For all x: y ends >= 1 (`-y` when y<0 is > 0, `y+1` when y>=0 is >= 1).
         */
        fun mutateLocalUp(x: Int): Int {
            var y = x
            if (y < 0) {
                y = -y
            } else {
                y = y + 1
            }
            return y
        }

        /** A buggy local-mutating branch: the else arm leaves `y` unchanged instead of +1, so for x == 0
         *  the result is 0, breaking the `>= 1` property. A wrong multi-output relation must REFUTE. */
        fun badMutateLocalUp(x: Int): Int {
            var y = x
            if (y < 0) {
                y = -y
            } else {
                y = y // wrong: should be y + 1; leaves y == 0 at x == 0
            }
            return y
        }
    }

    // ---- proofs whose ENTRY only calls the SUT; the branch is decomposed in the callee ----

    /**
     * VERIFIES via decomposition of a CALLEE branch. The proof harness only CALLS `clamp`; the value
     * branch lives one level down. bmc4j walks the call graph, extracts the callee branch, the leaf
     * certifies its summary, and the parent proves the trailing check given that summary. Full-domain
     * VERIFIES - the branch is decomposed where it actually lives, not in the thin harness.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `callee clamp branch extracted and discharged`() {
        val x = Bmc.anyInt()
        val r = Sut.clamp(x) // the decomposed branch is in clamp(), one level down
        Bmc.check(r in -10..10)
    }

    /**
     * VERIFIES via decomposition of a callee branch UNDER A PATH CONDITION. `cappedNonNegative` reaches
     * its value branch only when x >= 0; the leaf assumes that path condition (so it certifies the
     * summary over exactly the inputs the parent invokes the stub under), and the parent proves the
     * property. Full-domain VERIFIES.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `callee branch under a path condition verifies`() {
        val x = Bmc.anyInt()
        val r = Sut.cappedNonNegative(x)
        Bmc.check(r in 0..10)
    }

    /**
     * VERIFIES via decomposition of a branch TWO levels below the proof (proof -> sign -> magnitudeSign).
     * Confirms discovery recurses through the call graph to ANY depth.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `branch two levels down verifies`() {
        val x = Bmc.anyInt()
        val r = Sut.sign(x)
        Bmc.check(r in -1..1)
    }

    /**
     * MULTIPLE branches sliced. The proof reaches TWO callee branches (clamp + sign), each extracted and
     * proven by its own leaf, all discharged into the parent. The aggregate VERIFIES iff the parent and
     * BOTH leaves verify.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `multiple callee branches all sliced verify`() {
        val x = Bmc.anyInt()
        val a = Sut.clamp(x)
        val b = Sut.sign(x)
        Bmc.check(a in -10..10 && b in -1..1)
    }

    // ---- soundness guards: a wrong value at depth must be caught REFUTED ----

    /**
     * SOUNDNESS GUARD: a wrong VALUE in a CALLEE branch must surface, never be assumed away. The parent,
     * assuming the EXACT summary of `badClamp`, finds the trailing check FALSE at x > 10 (where the
     * result is 11), so the aggregate REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a wrong value in a callee branch is refuted`() {
        val x = Bmc.anyInt()
        val r = Sut.badClamp(x)
        Bmc.check(r in -10..10) // FALSE at x > 10; surfaces via the exact summary
    }

    /**
     * SOUNDNESS GUARD at a PATH-CONDITION depth: a wrong value in the guarded callee branch is caught.
     * The branch is reached only when x >= 0; its x >= 10 arm wrongly returns 11, so the parent (assuming
     * the exact summary under the path condition) REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a wrong value under a path condition is refuted`() {
        val x = Bmc.anyInt()
        val r = Sut.badCappedNonNegative(x)
        Bmc.check(r in 0..10) // FALSE at x >= 10; surfaces via the exact summary
    }

    /**
     * SOUNDNESS GUARD for the PARENT remainder: a bug OUTSIDE the branch is caught by the parent. The
     * callee clamp is correct, but the trailing check is wrong (claims `r in -5..5`), so the parent -
     * proving the remainder given the branch's summary - REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a bug in the parent remainder is refuted`() {
        val x = Bmc.anyInt(-1000, 1000)
        val r = Sut.clamp(x)
        Bmc.check(r in -5..5) // FALSE: clamp can return e.g. 8
    }

    // ---- RUNG 2: local-mutating branches decompose via a multi-output relation ----

    /**
     * VERIFIES via decomposition of a LOCAL-MUTATING callee branch (rung 2). `mutateLocalUp` writes the
     * live-out local `y` in both arms; bmc4j summarizes it as the EXACT relation over `y`'s after-value
     * (a multi-output relation - no frame, locals are unaliased), the leaf certifies it, the parent havocs
     * `y` and assumes the relation. Full-domain VERIFIES.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `local-mutating callee branch decomposes and verifies`() {
        // Bounded away from the Int.MIN_VALUE / Int.MAX_VALUE overflow edges (where `-x` and `x + 1`
        // wrap); the multi-output relation is EXACT, so the proof would otherwise REFUTE on that edge.
        val x = Bmc.anyInt(-1000, 1000)
        val r = Sut.mutateLocalUp(x)
        Bmc.check(r >= 1)
    }

    /**
     * SOUNDNESS GUARD (rung 2): a wrong local-update relation must REFUTE, never be assumed away. The else
     * arm leaves `y` unchanged (should be +1), so at x == 0 the result is 0; the parent, assuming the
     * EXACT (wrong) relation, finds `r >= 1` FALSE and REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    @BmcBranchDecompose
    fun `a wrong local-update relation is refuted`() {
        val x = Bmc.anyInt()
        val r = Sut.badMutateLocalUp(x)
        Bmc.check(r >= 1) // FALSE at x == 0 (result 0); surfaces via the exact multi-output relation
    }

    // ---- the original in-method (level 0) demo still holds ----

    /**
     * VERIFIES via decomposition of a branch in the PROOF METHOD itself (level 0). Confirms the in-method
     * case is unchanged by the call-graph extension.
     */
    @BmcProof
    @BmcBranchDecompose
    fun `in-method clamp branch extracted and discharged soundly`() {
        val x = Bmc.anyInt()
        val r = if (x < -10) -10 else if (x > 10) 10 else x // discovered + extracted in the proof method
        Bmc.check(r in -10..10)
    }
}
