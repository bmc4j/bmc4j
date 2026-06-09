package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;

import java.util.ArrayList;
import java.util.List;

/** Clean model of Kotlin's {@code TuplesKt} — the {@code infix fun A.to(B)} that builds a Pair, plus
 *  the {@code Pair.toList()} / {@code Triple.toList()} flatteners (a new read-only list of the tuple's
 *  components, in order). The whole facade surface is now per-member modeled, so no
 *  {@code @BmcModelTail} catch-all is needed. */
public final class TuplesKt {

    private TuplesKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <A, B> Pair<A, B> to(A a, B b) {
        return new Pair<>(a, b);
    }

    /** {@code Pair<T, T>.toList()} → {@code [first, second]} as a new read-only list. */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toList(Pair<? extends T, ? extends T> pair) {
        ArrayList<T> l = new ArrayList<>();
        l.add(pair.getFirst());
        l.add(pair.getSecond());
        return l;
    }

    /** {@code Triple<T, T, T>.toList()} → {@code [first, second, third]} as a new read-only list. */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toList(Triple<? extends T, ? extends T, ? extends T> triple) {
        ArrayList<T> l = new ArrayList<>();
        l.add(triple.getFirst());
        l.add(triple.getSecond());
        l.add(triple.getThird());
        return l;
    }
}
