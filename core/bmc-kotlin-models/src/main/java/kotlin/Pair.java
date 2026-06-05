package kotlin;

/** Clean model of Kotlin's {@code Pair} (the {@code a to b} tuple), enough for first/second and
 *  destructuring ({@code component1}/{@code component2}). */
public final class Pair<A, B> {

    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    public A component1() {
        return first;
    }

    public B component2() {
        return second;
    }
}
