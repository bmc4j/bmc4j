package example.enums

/** Classifies a card suit by colour, using `when` over the enum (sound under JBMC). */
object Cards {

    fun isRed(s: Suit): Boolean = when (s) {
        Suit.HEARTS, Suit.DIAMONDS -> true
        else -> false
    }

    /** BUG: forgot SPADES — so SPADES is classified as neither red nor black. */
    fun isBlack(s: Suit): Boolean = when (s) {
        Suit.CLUBS -> true
        else -> false
    }

    /** The fix. */
    fun isBlackFixed(s: Suit): Boolean = when (s) {
        Suit.CLUBS, Suit.SPADES -> true
        else -> false
    }

    /** An exhaustive `when` over the enum — JBMC handles it soundly (see the proof). */
    fun rank(s: Suit): Int = when (s) {
        Suit.HEARTS -> 1
        Suit.DIAMONDS -> 2
        Suit.CLUBS -> 3
        Suit.SPADES -> 4
    }
}
