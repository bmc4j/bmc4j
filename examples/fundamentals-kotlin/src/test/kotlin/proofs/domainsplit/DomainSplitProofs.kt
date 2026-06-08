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
     * EXERCISES THE FAN-OUT: an 8-slice split (plus the cover) gives nine independent derived runs that
     * the extension submits to the shared jbmc pool concurrently, bounded by `bmc { parallelism }` /
     * `-PbmcParallelism`. The slices tile `[-1000, 1000]` by residue mod 8 (the cover is sound) and the
     * clamp stays in range in each, so the aggregate is a domain-scoped green. With more slices than the
     * pool is wide they run in waves — never nine unbounded jbmc processes.
     */
    @BmcProof
    fun `many slices fan out and verify`() {
        val x = Bmc.anyInt()
        domainSplit(x in -1000..1000) {
            slice(((x % 8) + 8) % 8 == 0)
            slice(((x % 8) + 8) % 8 == 1)
            slice(((x % 8) + 8) % 8 == 2)
            slice(((x % 8) + 8) % 8 == 3)
            slice(((x % 8) + 8) % 8 == 4)
            slice(((x % 8) + 8) % 8 == 5)
            slice(((x % 8) + 8) % 8 == 6)
            slice(((x % 8) + 8) % 8 == 7)
        }
        val r = if (x < -5) -5 else if (x > 5) 5 else x
        Bmc.check(r in -5..5) // holds in every residue slice
    }

    /**
     * A REFUTED slice surfaces its counterexample under CONCURRENCY: the three slices tile the domain
     * (sound cover) but `buggySign` mis-classifies `x == 1`, which lives in the `x > 0` slice — so that
     * slice REFUTES while the others run alongside it. The early-exit cancels the still-running runs and
     * surfaces the counterexample, exactly as in the sequential path.
     */
    @BmcProof(expect = Verdict.REFUTED)
    fun `a refuted slice surfaces its counterexample under fan-out`() {
        val x = Bmc.anyInt()
        domainSplit(x in -1000..1000) {
            slice(x < 0)
            slice(x == 0)
            slice(x > 0) // x == 1 is here, and buggySign(1) == 0 (should be 1)
        }
        // A buggy sign classifier with a seeded off-by-one: it swallows x == 1 into the zero bucket,
        // so the x > 0 slice REFUTES at x == 1.
        val buggySign = if (x < 0) -1 else if (x <= 1) 0 else 1
        val expected = if (x < 0) -1 else if (x == 0) 0 else 1
        Bmc.check(buggySign == expected)
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

    /**
     * PASSES with COMPOUND `in`-range slice conditions: each slice is a `(a..b) || (c..d)` disjunction
     * of ranges. `in`-range desugars to a short-circuiting compare chain, so the slice argument is built
     * from internal branches — yet the cover folds it to a clean boolean before ORing, so the two
     * disjunction slices cover `0..100` without a phantom gap.
     */
    @BmcProof
    fun `compound in-range disjunction slices cover`() {
        val x = Bmc.anyInt()
        domainSplit(x in 0..100) {
            slice(x in 0..10 || x in 90..100) // the two ends
            slice(x in 10..90)                // the middle
        }
        val r = if (x < 0) 0 else if (x > 100) 100 else x
        Bmc.check(r in 0..100)
    }

    /**
     * FAILS LOUD with COMPOUND `in`-range slices: the `||`-of-ranges slices leave a genuine GAP at
     * `x == 50`, so the cover REFUTES — the compound-condition fold still detects a real gap.
     */
    @BmcProof(expect = Verdict.REFUTED)
    fun `compound in-range disjunction slices with a gap fail loud`() {
        val x = Bmc.anyInt()
        domainSplit(x in 0..100) {
            slice(x in 0..10 || x in 90..100)
            slice(x in 10..49 || x in 51..89) // GAP: x == 50 is covered by no slice
        }
        Bmc.check(x == x)
    }
}
