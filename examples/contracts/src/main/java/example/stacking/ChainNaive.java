package example.stacking;

/** The same three-deep stack with <b>no contracts</b>: a caller must inline all of it. */
public final class ChainNaive {

    private ChainNaive() {
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
