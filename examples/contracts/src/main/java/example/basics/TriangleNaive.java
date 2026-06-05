package example.basics;

/**
 * An identical loop with <b>no contract</b>. Calls to it are never summarized, so a proof
 * that uses it must inline the real loop — the baseline that shows what the contract buys.
 */
public final class TriangleNaive {

    private TriangleNaive() {
    }

    public static int triangle(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }
}
