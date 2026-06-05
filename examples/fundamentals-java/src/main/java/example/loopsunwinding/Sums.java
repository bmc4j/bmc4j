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
}
