package example.integeroverflow;

/** Two tiny routines: one that intuition gets wrong, one it gets right. */
public final class Numbers {

    private Numbers() {
    }

    /** Absolute value. BUG: {@code abs(Integer.MIN_VALUE)} overflows to a negative. */
    public static int abs(int x) {
        return x < 0 ? -x : x;
    }

    /** The larger of two values. Correct for all inputs. */
    public static int max(int a, int b) {
        return a >= b ? a : b;
    }
}
