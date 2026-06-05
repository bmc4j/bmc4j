package example.recursion;

/**
 * Sum of 0..n by recursion (depth n — costly to inline). The contract lives test-side in
 * {@code contracts.RecursiveContract}; production stays free of bmc references.
 */
public final class Recursive {

    private Recursive() {
    }

    public static int sumTo(int n) {
        if (n <= 0) {
            return 0;
        }
        return n + sumTo(n - 1);
    }
}
