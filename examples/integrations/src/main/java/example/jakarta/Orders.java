package example.jakarta;

/** Business logic over an {@link Order} with element-constrained lists. */
public final class Orders {

    private Orders() {
    }

    /**
     * BUG: divides by {@code scores.get(0) - 1}. Every score is {@code @Min(1)}, so a score of
     * exactly 1 makes the divisor zero — a valid Order at the element boundary hits a division by
     * zero. REFUTED only because the element loop puts {@code score >= 1} (not {@code > 1}) into the
     * domain at the boundary.
     */
    public static int firstScoreReciprocal(Order o) {
        if (o.scores == null || o.scores.isEmpty()) {
            return 0;
        }
        Integer s = o.scores.get(0);
        return s == null ? 0 : 100 / (s - 1);
    }

    /** The fix: guard the boundary. Provably safe for every valid Order. */
    public static int firstScoreReciprocalSafe(Order o) {
        if (o.scores == null || o.scores.isEmpty()) {
            return 0;
        }
        Integer s = o.scores.get(0);
        return (s == null || s <= 1) ? 0 : 100 / (s - 1);
    }

    /**
     * BUG (container @Valid cascade pin): claims the first line's quantity is at least 2. The element
     * cascade brings {@code OrderLine}'s {@code @Min(1)} into scope — admitting quantity 1, which
     * refutes this. The refutation exists ONLY because the container cascade is honored (without it
     * the element would be unconstrained but this property would still be refuted — the PASSES proof
     * below is the real cascade pin).
     */
    public static boolean firstLineQuantityAtLeast2(Order o) {
        if (o.lines == null || o.lines.isEmpty()) {
            return true;
        }
        OrderLine l = o.lines.get(0);
        return l == null || l.quantity >= 2;
    }

    /**
     * Safe EXACTLY because the container cascade bounds each line's quantity to 1..100: indexing a
     * 101-bucket array by quantity stays in range. WITHOUT the cascade {@code quantity} is an
     * unconstrained int and this is REFUTED — so a PASSES verdict pins the container cascade is honored.
     */
    public static int firstLineBucket(Order o) {
        if (o.lines == null || o.lines.isEmpty()) {
            return 0;
        }
        OrderLine l = o.lines.get(0);
        if (l == null) {
            return 0;
        }
        int[] buckets = new int[101]; // valid indices 0..100; quantity is 1..100
        return buckets[l.quantity];
    }
}
