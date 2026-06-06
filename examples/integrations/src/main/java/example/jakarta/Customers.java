package example.jakarta;

/** Business logic over a {@link Customer} with a cascaded {@link Address}. */
public final class Customers {

    private Customers() {
    }

    // 4 buckets: zip 0..99999 maps to zip/33333 in {0,1,2,3}, exactly the valid index range.
    static final String[] REGIONS = {"far-west", "west", "central", "east"};

    /**
     * Indexes a 4-bucket array by {@code zip / 33333}. This is safe EXACTLY because Address's own
     * {@code @Min(0)}/{@code @Max(99999)} bound zip to 0..99999, so the index stays in {0,1,2,3}.
     *
     * <p>That safety only holds when the {@code @Valid} cascade puts Address's bounds into the proof
     * domain. WITHOUT the cascade, {@code address.zip} is an unconstrained {@code int} and this would
     * be REFUTED (a huge zip indexes out of bounds) — so a PASSES verdict here pins that the cascade
     * IS honored (the regression the cascade closes).
     */
    public static int region(Customer c) {
        return REGIONS[c.address.zip / 33333].length();
    }

    /**
     * BUG: a 3-bucket lookup. Even with the cascade's {@code zip <= 99999}, {@code zip / 33333} can be
     * 3 (at zip 99999), which is out of bounds for 3 buckets. REFUTED for a perfectly valid Customer —
     * the inner constraint admits the boundary value that breaks this.
     */
    public static int regionNarrow(Customer c) {
        String[] three = {"west", "central", "east"};
        return three[c.address.zip / 33333].length();
    }

    /** The fix: clamp into range. Provably safe for every valid Customer regardless of zip. */
    public static int regionNarrowSafe(Customer c) {
        String[] three = {"west", "central", "east"};
        int i = c.address.zip / 33333;
        if (i >= three.length) {
            i = three.length - 1;
        }
        return three[i].length();
    }
}
