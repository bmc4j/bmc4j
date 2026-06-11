package proofs.enums

import example.enums.Cards
import example.enums.Suit
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

class EnumProofs {

    /**
     * FAILS: SPADES is neither red nor black, so the two classifiers agree (both false)
     * for it — the "exactly one colour" property breaks.
     */
    // Expected verdict: REFUTED - the seeded bug misclassifies one suit.
    @BmcProof(expect = Verdict.REFUTED)
    fun every_suit_is_exactly_one_colour() {
        val s = Bmc.anyOf(Suit.values())
        Bmc.check(Cards.isRed(s) != Cards.isBlack(s))
    }

    /** PASSES: the fixed classifier covers every suit. */
    @BmcProof(unwind = 8)
    fun fixed_classifier_covers_every_suit() {
        val s = Bmc.anyOf(Suit.values())
        Bmc.check(Cards.isRed(s) != Cards.isBlackFixed(s))
    }

    /** PASSES: an exhaustive `when` over the enum verifies — JBMC handles it soundly. */
    @BmcProof(unwind = 8)
    fun when_over_enum_is_sound() {
        val s = Bmc.anyOf(Suit.values())
        val r = Cards.rank(s)
        Bmc.check(r in 1..4)
    }

    /**
     * PASSES: from Kotlin, the varargs `anyOf(first, ...rest)` overload resolves for an explicit value
     * set, while `anyOf(Suit.values())` above still binds the array overload (a single array arg, no
     * spread) — verified from Kotlin.
     */
    @BmcProof
    fun anyOf_varargs_picks_a_listed_region() {
        val region = Bmc.anyOf("us", "eu")
        Bmc.check(region == "us" || region == "eu")
    }
}
