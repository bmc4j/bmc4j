package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;

/** Clean model of Kotlin's {@code TuplesKt} — the {@code infix fun A.to(B)} that builds a Pair. */
@BmcModelConforms("Kotlin stdlib model — @BmcProof (model-conformance-proofs); facade/value model, audited at class level")
public final class TuplesKt {

    private TuplesKt() {
    }

    public static <A, B> Pair<A, B> to(A a, B b) {
        return new Pair<>(a, b);
    }
}
