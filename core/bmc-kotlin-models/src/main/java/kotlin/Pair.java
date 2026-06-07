package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Clean model of Kotlin's {@code Pair} (the {@code a to b} tuple), enough for first/second and
 *  destructuring ({@code component1}/{@code component2}). */
@BmcModelTail(reason = "Pair data-class auto-generated surface (copy/toString) not exercised by the "
        + "bounded proofs; loud under JBMC if reached")
public final class Pair<A, B> {

    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public A getFirst() {
        return first;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public B getSecond() {
        return second;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public A component1() {
        return first;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public B component2() {
        return second;
    }
}
