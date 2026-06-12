package example.loopsunwinding;

/** A loop whose result we prove against a closed form. */
public final class Sums {

    private Sums() {
    }

    /** Sum 1..n. Correct, and equal to n*(n+1)/2. */
    public static int sumTo(int n) {
        int total = 0;
        for (int i = 1; i <= n; i++) {
            total += i;
        }
        return total;
    }

    /**
     * Count down to zero from a SYMBOLIC start: the trip count is exactly {@code start}, a symbolic
     * input, so no FIXED unwind bound can cover it. The auto-unwind climb keeps firing the unwinding
     * assertion at this loop right up to the cap — the data-dependent-bound signal the diagnostic names.
     */
    public static int countDown(int start) {
        int steps = 0;
        for (int n = start; n > 0; n--) {
            steps++;
        }
        return steps;
    }
}
