package proofs.basics

import example.basics.Triangles
import example.basics.TrianglesNaive
import org.bmc4j.Bmc
import org.bmc4j.Bmc.anyInt
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * The caller side of the basics contract. Alongside these hand-written proofs the processor
 * auto-generates `TriangleContract__BmcEnforce.enforce__triangle`, which discharges `@Ensures` against
 * the real loop body and shows up green in the same report.
 */
class BasicsProofs {

    /**
     * PASSES at a tiny bound. `unwind = 2` is far too small to inline `triangle`'s loop (n up to 8) —
     * this only verifies because the static call is redirected to the contract summary, so the caller
     * reuses `@Ensures result >= 0` instead of unrolling.
     */
    @BmcProof(unwind = 2)
    fun `caller reuses the contract at a tiny bound`() {
        val n = anyInt(0, 8)
        val s = Triangles.triangle(n)
        Bmc.check(s >= 0)
    }

    /**
     * UNDECIDED at the same bound. Identical loop, but `TrianglesNaive` has no contract, so the real
     * loop is inlined and overruns `unwind = 2` — incompleteness (UNKNOWN), not a counterexample.
     */
    @BmcProof(unwind = 2, expect = Verdict.UNKNOWN)
    fun `without a contract the same bound is too small`() {
        val n = anyInt(0, 8)
        Bmc.check(TrianglesNaive.triangle(n) >= 0)
    }
}
