package proofs.kotlinranges

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.kotlin.checkThrows
import kotlin.random.Random
import kotlin.ranges.IntProgression
import kotlin.ranges.LongProgression

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
        checkThrows<IllegalArgumentException> { 0.coerceIn(lo, hi) }
    }

    // ---- progression first() / last() (RangesKt.first/last(IntProgression) etc.). PR #129 probed the
    // allocating progression ops and REFUTED them; now modeled by reading the progression's start/end
    // accessors directly. Binding the range to an IntProgression-typed val makes `.first()`/`.last()`
    // resolve to the RangesKt EXTENSION (not the IntRange.first property), exercising the model. We
    // build the progression from a plain range (NOT `step` — `step` is a deliberately-loud
    // @BmcUnmodelable member that would trip the sentinel). The Int/Long `range.random(rng)` draws are
    // MODELED (sound nondet-in-range, see below); randomOrNull + the Char draw stay loud walls.

    @BmcProof
    fun progression_first_is_start() {
        val p: IntProgression = 0..6
        Bmc.check(p.first() == 0 && p.last() == 6)
    }

    @BmcProof
    fun progression_firstOrNull_lastOrNull_present() {
        val p: IntProgression = 1..5
        Bmc.check(p.firstOrNull() == 1 && p.lastOrNull() == 5)
    }

    @BmcProof
    fun progression_empty_firstOrNull_is_null() {
        // an ascending progression with first > last is empty
        val p: IntProgression = 5..1
        Bmc.check(p.firstOrNull() == null && p.lastOrNull() == null)
    }

    @BmcProof
    fun progression_empty_first_throws() {
        val p: IntProgression = 5..1
        checkThrows<NoSuchElementException> { p.first() }
    }

    @BmcProof
    fun progression_long_first_last() {
        val p: LongProgression = 0L..6L
        Bmc.check(p.first() == 0L && p.last() == 6L)
    }

    /** Symbolic progression law: for a..b (a<=b), first()==a and last()==b. */
    @BmcProof
    fun symbolic_progression_first_last() {
        val a = Bmc.anyInt(-100, 0)
        val b = Bmc.anyInt(1, 100)
        val p: IntProgression = a..b
        Bmc.check(p.first() == a && p.last() == b)
    }

    // ---- range.random(rng) (RangesKt.random(IntRange/LongRange, Random)): a SOUND nondet-in-range draw,
    // proven to land inside the inclusive range for EVERY outcome, plus the empty-range throw.

    /** `IntRange.random(rng)` lands in `[first, last]` for every outcome. */
    @BmcProof
    fun intRange_random_in_inclusive_range() {
        val v = (10..20).random(Random)
        Bmc.check(v in 10..20)
    }

    /** `LongRange.random(rng)` lands in `[first, last]` for every outcome. */
    @BmcProof
    fun longRange_random_in_inclusive_range() {
        val v = (10L..20L).random(Random)
        Bmc.check(v in 10L..20L)
    }

    /** Symbolic: `IntRange.random(rng)` over a symbolic non-empty range lands inside it. */
    @BmcProof
    fun symbolic_intRange_random_in_range() {
        val lo = Bmc.anyInt(-100, 100)
        val hi = Bmc.anyInt(-100, 100)
        Bmc.assume(lo <= hi)
        val v = (lo..hi).random(Random)
        Bmc.check(v in lo..hi)
    }

    /** `IntRange.random(rng)` on an empty range throws `NoSuchElementException`, like the stdlib. */
    @BmcProof
    fun intRange_random_empty_throws() {
        checkThrows<NoSuchElementException> { (5..3).random(Random) }
    }
}
