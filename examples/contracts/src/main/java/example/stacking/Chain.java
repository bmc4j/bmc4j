package example.stacking;

/**
 * Three recursive functions stacked into a call chain: {@code f} sums {@code g}, {@code g}
 * sums {@code h}, {@code h} sums the integers. Their contracts live test-side in
 * {@code contracts.ChainContract}; production stays free of bmc references.
 *
 * <pre>
 *   h(n) = n(n+1)/2                          (triangular)
 *   g(n) = sum h(0..n) = n(n+1)(n+2)/6       (tetrahedral)
 *   f(n) = sum g(0..n) = n(n+1)(n+2)(n+3)/24 (pentatope)
 * </pre>
 */
public final class Chain {

    private Chain() {
    }

    public static int f(int n) {
        if (n <= 0) {
            return 0;
        }
        return g(n) + f(n - 1);
    }

    public static int g(int n) {
        if (n <= 0) {
            return 0;
        }
        return h(n) + g(n - 1);
    }

    public static int h(int n) {
        if (n <= 0) {
            return 0;
        }
        return n + h(n - 1);
    }
}
