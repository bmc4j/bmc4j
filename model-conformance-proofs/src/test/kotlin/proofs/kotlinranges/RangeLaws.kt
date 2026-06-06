package proofs.kotlinranges

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Laws of the `kotlin.ranges.RangesKt` model (`coerceAtLeast` / `coerceAtMost` / `coerceIn`),
 * proved symbolically over the model itself — the same axis as the other Kotlin-facade law
 * suites. `coerceIn` joined the model when an un-modeled call nondet-stubbed and produced a
 * spurious counterexample; these laws pin the real stdlib semantics, including the
 * empty-range throw.
 */
class RangeLaws {

    @BmcProof
    fun coerceAtLeast_is_max() {
        val v = Bmc.anyInt()
        val lo = Bmc.anyInt()
        val c = v.coerceAtLeast(lo)
        Bmc.check(c >= lo && (c == v || c == lo))
    }

    @BmcProof
    fun coerceAtMost_is_min() {
        val v = Bmc.anyInt()
        val hi = Bmc.anyInt()
        val c = v.coerceAtMost(hi)
        Bmc.check(c <= hi && (c == v || c == hi))
    }

    @BmcProof
    fun coerceAtMost_long_is_min() {
        val v = Bmc.anyLong()
        val hi = Bmc.anyLong()
        val c = v.coerceAtMost(hi)
        Bmc.check(c <= hi && (c == v || c == hi))
    }

    /** The headline law: for a non-empty range the result always lands inside it. */
    @BmcProof
    fun coerceIn_lands_in_range() {
        val v = Bmc.anyInt()
        val lo = Bmc.anyInt(-100, 100)
        val hi = Bmc.anyInt(-100, 100)
        Bmc.assume(lo <= hi)
        Bmc.check(v.coerceIn(lo, hi) in lo..hi)
    }

    /** A value already inside the range passes through unchanged. */
    @BmcProof
    fun coerceIn_is_identity_inside() {
        val v = Bmc.anyInt(-100, 100)
        val lo = Bmc.anyInt(-100, 100)
        val hi = Bmc.anyInt(-100, 100)
        Bmc.assume(lo <= v && v <= hi)
        Bmc.check(v.coerceIn(lo, hi) == v)
    }

    @BmcProof
    fun coerceIn_long_lands_in_range() {
        val v = Bmc.anyLong()
        val lo = Bmc.anyLong(-100L, 100L)
        val hi = Bmc.anyLong(-100L, 100L)
        Bmc.assume(lo <= hi)
        Bmc.check(v.coerceIn(lo, hi) in lo..hi)
    }

    /** Stdlib contract: an empty range (min > max) throws IllegalArgumentException. */
    @BmcProof
    fun coerceIn_empty_range_throws() {
        val lo = Bmc.anyInt(-100, 100)
        val hi = Bmc.anyInt(-100, 100)
        Bmc.assume(lo > hi)
        var threw = false
        try {
            0.coerceIn(lo, hi)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        Bmc.check(threw)
    }
}
