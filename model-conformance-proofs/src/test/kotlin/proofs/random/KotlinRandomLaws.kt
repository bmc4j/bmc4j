package proofs.random

import kotlin.random.Random
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.checkThrows

/**
 * Model proofs for the `kotlin.random.Random` bounded-draw model — the "prove for every random outcome"
 * feature, mirroring [proofs.random.JavaRandomLaws]. A proof writes `Random.nextInt(6)`, which dispatches
 * through the modeled `Random.Default` object; each draw is nondet-in-range, so the property is proven for
 * EVERY outcome the RNG could produce, not just sampled values.
 *
 * The seeded `Random(seed)` factory is a LOUD stub, so a reproducibility proof is honestly UNKNOWN — never
 * a false refutation. Because the only way to a seeded `Random` is through that loud factory, the
 * false-refutation can never arise.
 */
class KotlinRandomLaws {

    /** The dice idiom — `nextInt(6) + 1` lands in `1..6` for EVERY outcome. */
    @BmcProof
    fun nextInt6_plus1_always_in_1_to_6() {
        val roll = Random.nextInt(6) + 1
        Bmc.check(roll in 1..6)
    }

    /** `nextInt(until)` is always within `[0, until)` — proven for a symbolic bound. */
    @BmcProof
    fun nextInt_until_in_range_for_every_bound() {
        val until = Bmc.anyInt(1, 1000)
        val v = Random.nextInt(until)
        Bmc.check(v in 0 until until)
    }

    /** `nextInt(from, until)` lands in `[from, until)` for every outcome. */
    @BmcProof
    fun nextInt_from_until_in_range() {
        val v = Random.nextInt(10, 20)
        Bmc.check(v in 10 until 20)
    }

    /** `nextLong(until)` is always within `[0, until)`. */
    @BmcProof
    fun nextLong_until_in_range() {
        val v = Random.nextLong(100L)
        Bmc.check(v in 0L until 100L)
    }

    /** `nextBoolean()` is true or false — both outcomes considered. */
    @BmcProof
    fun nextBoolean_is_true_or_false() {
        val b = Random.nextBoolean()
        Bmc.check(b || !b)
    }

    /** `nextInt(0)` throws IllegalArgumentException on the empty range, like the stdlib. */
    @BmcProof
    fun nextInt_empty_until_throws() {
        checkThrows<IllegalArgumentException> { Random.nextInt(0) }
    }

    /** `nextInt(from, until)` with `from >= until` throws on the empty range. */
    @BmcProof
    fun nextInt_empty_range_throws() {
        checkThrows<IllegalArgumentException> { Random.nextInt(5, 3) }
    }

    /**
     * The seeded `Random(seed)` factory is a LOUD stub: a reproducibility proof is honestly UNKNOWN, NOT
     * a false REFUTED. The keystone of the soundness separation — `Random(42).nextInt() ==
     * Random(42).nextInt()` (true in reality) is never falsely refuted because reaching a seeded `Random`
     * trips the loud factory first.
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    fun seeded_factory_is_unknown_not_refuted() {
        val a = Random(42).nextInt() // loud factory -> UNKNOWN
        val b = Random(42).nextInt()
        Bmc.check(a == b) // never evaluated
    }
}
