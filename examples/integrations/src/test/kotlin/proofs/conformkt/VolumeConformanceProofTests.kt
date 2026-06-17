package proofs.conformkt

import example.conformkt.Volume
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.ConformProofsAgainstModel

/**
 * Conforming a `src/bmcModel` model against the **real implementation** with
 * [ConformProofsAgainstModel].
 *
 * The `custommodels*` proofs lean on models of un-analyzable services ([example.custommodelskt.ExchangeRates]
 * throws in `src/main`), so their real leg is intractable on purpose and they are *not* conformed —
 * that is the model's whole job. [Volume] is the opposite: it is a small, fully analyzable helper
 * whose model exists only to be checked. Because the real [Volume.adjust] is itself loop-free
 * integer arithmetic, the REAL leg is just as tractable as the model leg, so we can demand both.
 *
 * `@ConformProofsAgainstModel(Volume::class)` runs each proof below **twice**:
 *  - the **model leg** — the normal run, with the Kotlin `src/bmcModel` [Volume] substituted; and
 *  - the **real leg** — the same proof with that model *excluded*, so the real [Volume] is analysed.
 *
 * Both legs must reach the proof's expected verdict (default VERIFIED) or the proof fails, naming the
 * leg. An unsound model — one that VERIFIED the clamp bound while the real code could refute it —
 * would fail the real leg and be surfaced as UNSOUND. Here the model is faithful, so both legs pass.
 */
@ConformProofsAgainstModel(Volume::class)
class VolumeConformanceProofTests {

    /** The clamp holds for EVERY current/delta, including overflow-prone deltas — on both legs. */
    @BmcProof
    fun adjust_result_is_always_in_range() {
        val current = Bmc.anyInt(0, Volume.MAX)
        val delta = Bmc.anyInt() // unbounded: stresses the overflow-safe Long arithmetic both sides do
        val next = Volume.adjust(current, delta)
        Bmc.check(next in 0..Volume.MAX)
    }

    /** A non-saturating step is applied exactly (not silently clamped) — a behavioural equality the
     *  model must match the real class on, not just a one-sided bound. */
    @BmcProof
    fun in_range_step_is_applied_exactly() {
        val current = Bmc.anyInt(10, 90)
        val delta = Bmc.anyInt(-10, 10) // current + delta stays within 0..MAX, so no clamping occurs
        val next = Volume.adjust(current, delta)
        Bmc.check(next == current + delta)
    }
}
