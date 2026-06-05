package example.soundness;

/**
 * Two plain methods. Their contracts live test-side in {@code contracts.DeltasContract}, where
 * one is deliberately false — see that file and the README for the soundness guard.
 */
public final class Deltas {

    private Deltas() {
    }

    public static int delta(int a, int b) {
        return a - b;
    }

    public static int absDelta(int a, int b) {
        return Math.abs(a - b);
    }
}
