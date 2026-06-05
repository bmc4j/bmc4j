package example.assumedomain;

/** Array access — the function itself is correct; the proof's `assume` is what matters. */
public final class Items {

    private Items() {
    }

    /** Return the element at {@code i}. Safe exactly when {@code 0 <= i < a.length}. */
    public static int elementAt(int[] a, int i) {
        return a[i];
    }
}
