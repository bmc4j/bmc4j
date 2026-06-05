package example.recursion;

/** The same recursion with <b>no contract</b>: every proof must unroll the real call depth. */
public final class RecursiveNaive {

    private RecursiveNaive() {
    }

    public static int sumTo(int n) {
        if (n <= 0) {
            return 0;
        }
        return n + sumTo(n - 1);
    }
}
