package example.domainsplit;

/**
 * A tiny routine whose correctness a {@code domainSplit} proof partitions across the input range. The
 * point of the example is the PROOF shape (one slow obligation cut into independent slices + a cover
 * check), not the arithmetic — so the function is deliberately simple.
 */
public final class Clamp {

    private Clamp() {
    }

    /** Clamp {@code x} into {@code [lo, hi]} ({@code lo <= hi} assumed). The result is always in range. */
    public static int clamp(int x, int lo, int hi) {
        if (x < lo) {
            return lo;
        }
        if (x > hi) {
            return hi;
        }
        return x;
    }

    /**
     * A buggy sign classifier: returns {@code -1}/{@code 0}/{@code 1} for negative/zero/positive — but
     * with a seeded off-by-one that mis-classifies exactly {@code x == 1} as {@code 0}. Used by the
     * REFUTED-slice demo so a counterexample lands in one specific slice (the {@code x > 0} slice).
     */
    public static int buggySign(int x) {
        if (x < 0) {
            return -1;
        }
        if (x <= 1) { // BUG: should be x == 0; this also swallows x == 1 into the zero bucket.
            return 0;
        }
        return 1;
    }
}
