package proofs.objecthost

import example.objecthost.Squares
import example.objecthost.SquaresNaive
import org.bmc4j.Bmc
import org.bmc4j.Bmc.anyInt
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * The caller side of the object-hosted contract. Alongside these hand-written proofs the processor
 * auto-generates `SquaresContract__BmcEnforce.enforce__pyramid` (VERIFIED) and `enforce__bogus`
 * (REFUTED) — the object-form contract discharges its enforce-proof identically to the static form.
 *
 * The point is that the contract host is a plain Kotlin `object` with ordinary `fun` predicates: the
 * generated stub/enforce invoke those predicates on the singleton (`SquaresContract.INSTANCE.bounded`)
 * instead of statically, and the proofs verify exactly as they would for a companion/`@JvmStatic` host.
 */
class ObjectHostProofs {

    /**
     * PASSES at a tiny bound. `unwind = 2` is far too small to inline `pyramid`'s loop (n up to 8) —
     * this only verifies because the static call is redirected to the object-hosted contract summary,
     * so the caller reuses `@Ensures result >= 0` instead of unrolling.
     */
    @BmcProof(unwind = 2)
    fun `caller reuses the object-hosted contract at a tiny bound`() {
        val n = anyInt(0, 8)
        val s = Squares.pyramid(n)
        Bmc.check(s >= 0)
    }

    /**
     * UNDECIDED at the same bound. Identical loop, but `SquaresNaive` has no contract, so the real loop
     * is inlined and overruns `unwind = 2` — incompleteness (UNKNOWN), not a counterexample.
     */
    @BmcProof(unwind = 2, expect = Verdict.UNKNOWN)
    fun `without a contract the same bound is too small`() {
        val n = anyInt(0, 8)
        Bmc.check(SquaresNaive.pyramid(n) >= 0)
    }
}
