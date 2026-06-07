package proofs.suspendcontracts

import example.suspendcontracts.Calcs
import example.suspendcontracts.CalcsNaive
import kotlinx.coroutines.runBlocking
import org.bmc4j.Bmc
import org.bmc4j.Bmc.anyInt
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * The caller side of the `suspend` contract. Alongside these the processor auto-generates
 * `CalcContract__BmcEnforce.enforce__stepTo`, which drives the real suspend body to completion and
 * discharges `@Ensures` — it shows up green in the same report.
 *
 * The call-site redirect rewrites `Calcs.stepTo(n, $cont)` (the lowered suspend ABI) to the static
 * stub regardless of whether the CALLER is itself a suspend function: a non-suspend caller drives it
 * through `runBlocking { }`, a suspend caller calls it directly from its own state machine. Both reuse
 * the contract summary instead of unrolling the loop.
 */
class SuspendContractProofs {

    /**
     * Non-suspend caller. `runBlocking { }` drives the suspend call; the call site is redirected to the
     * contract stub, so this passes at `unwind = 2` (far too small to unroll `stepTo`'s loop, n up to 5).
     */
    @BmcProof(unwind = 2)
    fun `non-suspend caller reuses the contract via runBlocking`() {
        val n = anyInt(0, 5)
        val r = runBlocking { Calcs.stepTo(n) }
        Bmc.check(r == n)
    }

    /**
     * Suspend caller: the whole proof body runs inside `runBlocking { }`, so `Calcs.stepTo(n)` is called
     * directly from a coroutine context (the idiomatic way coroutine code is tested). The same redirect
     * fires inside this caller's state machine, so it too reuses `@Ensures result == n` at `unwind = 2`.
     */
    @BmcProof(unwind = 2)
    fun `suspend caller reuses the contract from a coroutine context`() = runBlocking {
        val n = anyInt(0, 5)
        Bmc.check(Calcs.stepTo(n) == n)
    }

    /**
     * UNDECIDED at the same bound: `CalcsNaive.stepTo` has no contract, so the real loop (with its
     * per-iteration suspension point) is inlined and overruns `unwind = 2` — incompleteness (UNKNOWN),
     * not a counterexample.
     */
    @BmcProof(unwind = 2, expect = Verdict.UNKNOWN)
    fun `without a contract the same bound is too small`() = runBlocking {
        val n = anyInt(0, 5)
        Bmc.check(CalcsNaive.stepTo(n) == n)
    }
}
