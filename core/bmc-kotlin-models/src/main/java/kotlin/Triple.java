package kotlin;

/** Clean model of Kotlin's {@code Triple} — first/second/third and destructuring
 *  ({@code component1}/{@code component2}/{@code component3}). Mirrors {@link Pair}; built directly
 *  ({@code Triple(a, b, c)}), there is no infix factory like {@code to}. */
public final class Triple<A, B, C> {

    private final A first;
    private final B second;
    private final C third;

    public Triple(A first, B second, C third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public C getThird() {
        return third;
    }

    public A component1() {
        return first;
    }

    public B component2() {
        return second;
    }

    public C component3() {
        return third;
    }
}
