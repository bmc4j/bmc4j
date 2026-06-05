package kotlin;

/** Clean model of Kotlin's {@code TuplesKt} — the {@code infix fun A.to(B)} that builds a Pair. */
public final class TuplesKt {

    private TuplesKt() {
    }

    public static <A, B> Pair<A, B> to(A a, B b) {
        return new Pair<>(a, b);
    }
}
