package example.enums;

/** Classifies a card suit by colour — using {@code ==}, not {@code switch}. */
public final class Cards {

    private Cards() {
    }

    public static boolean isRed(Suit s) {
        return s == Suit.HEARTS || s == Suit.DIAMONDS;
    }

    /** BUG: forgot SPADES — so SPADES is classified as neither red nor black. */
    public static boolean isBlack(Suit s) {
        return s == Suit.CLUBS;
    }

    /** The fix. */
    public static boolean isBlackFixed(Suit s) {
        return s == Suit.CLUBS || s == Suit.SPADES;
    }

    /** A {@code switch} over the enum — JBMC handles this soundly (see the proof). */
    public static int rank(Suit s) {
        switch (s) {
            case HEARTS: return 1;
            case DIAMONDS: return 2;
            case CLUBS: return 3;
            case SPADES: return 4;
            default: return 0;
        }
    }
}
