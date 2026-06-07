package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Clean model of Kotlin's {@code TuplesKt} — the {@code infix fun A.to(B)} that builds a Pair. */
@BmcModelTail(reason = "TuplesKt remainder (toList overloads) not exercised by the bounded proofs; "
        + "loud under JBMC if reached")
public final class TuplesKt {

    private TuplesKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <A, B> Pair<A, B> to(A a, B b) {
        return new Pair<>(a, b);
    }
}
