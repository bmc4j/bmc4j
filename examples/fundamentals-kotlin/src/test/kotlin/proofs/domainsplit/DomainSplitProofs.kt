package proofs.domainsplit

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.domainSplit
import org.bmc4j.kotlin.slice

/**
 * Kotlin block-form of the `domainSplit` DSL. `domainSplit(cond) { slice(...) }` lowers to the same
 * flat marker sequence the Java form uses; the `{ }` is purely visual grouping. The split expands one
 * proof into N slice runs (the body under `assume(slice_i)`) plus one cover run
 * (`overall => union(slices)`), aggregated to pass iff the cover and every slice VERIFIED.
 */
class DomainSplitProofs {

    /** PASSES via its slices, written with the Kotlin block + `x in range` sugar. */
    @BmcProof
    fun `clamp result stays in range when split by sign`() {
        val x = Bmc.anyInt()
        domainSplit(x in -1_000_000..1_000_000) {
            slice(x < 0)
            slice(x == 0)
            slice(x > 0)
        }
        val r = if (x < -10) -10 else if (x > 10) 10 else x
        Bmc.check(r in -10..10) // body runs once per slice
    }

    /**
     * FAILS LOUD via the COVER proof: the slices leave a GAP at `x == 0`, so the cover obligation
     * `overall => (x<0 || x>0)` is REFUTED — never a silent green over the uncovered point.
     */
    @BmcProof(expect = Verdict.REFUTED)
    fun `a gap in the cover fails loud`() {
        val x = Bmc.anyInt()
        domainSplit(x in -100..100) {
            slice(x < 0)
            slice(x > 0) // GAP: x == 0 is claimed but covered by no slice
        }
        Bmc.check(x == x)
    }
}
