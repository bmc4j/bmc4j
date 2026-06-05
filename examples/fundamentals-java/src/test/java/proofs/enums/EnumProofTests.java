package proofs.enums;

import example.enums.Cards;
import example.enums.Suit;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

class EnumProofTests {

    /**
     * FAILS: SPADES is neither red nor black, so the two classifiers agree
     * (both false) for it — the "exactly one colour" property breaks.
     */
    // Expected verdict: REFUTED - the seeded bug misclassifies one suit.
    @BmcProof(expect = Verdict.REFUTED)
    void every_suit_is_exactly_one_colour() {
        Suit s = Bmc.anyOf(Suit.values());
        Bmc.check(Cards.isRed(s) != Cards.isBlack(s));
    }

    /** PASSES: the fixed classifier covers every suit. */
    @BmcProof
    void fixed_classifier_covers_every_suit() {
        Suit s = Bmc.anyOf(Suit.values());
        Bmc.check(Cards.isRed(s) != Cards.isBlackFixed(s));
    }

    /** PASSES: a {@code switch} over the enum verifies — JBMC handles it soundly. */
    @BmcProof
    void switch_over_enum_is_sound() {
        Suit s = Bmc.anyOf(Suit.values());
        int r = Cards.rank(s);
        Bmc.check(r >= 1 && r <= 4);
    }
}
