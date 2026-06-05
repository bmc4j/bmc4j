package example.basics;

/**
 * A pure leaf with an input-dependent loop. Its contract lives test-side in
 * {@code contracts.TriangleContract} — production code carries no bmc references.
 */
public final class Triangle {

    private Triangle() {
    }

    /** The nth triangular number, the slow way (a loop, costly to inline). */
    public static int triangle(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }
}
