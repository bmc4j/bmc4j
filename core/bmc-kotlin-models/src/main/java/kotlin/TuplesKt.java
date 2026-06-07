package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;

/** Clean model of Kotlin's {@code TuplesKt} — the {@code infix fun A.to(B)} that builds a Pair. */
public final class TuplesKt {

    private TuplesKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <A, B> Pair<A, B> to(A a, B b) {
        return new Pair<>(a, b);
    }
}
