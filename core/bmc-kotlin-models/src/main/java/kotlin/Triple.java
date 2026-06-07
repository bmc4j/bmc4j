package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;

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

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public A getFirst() {
        return first;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public B getSecond() {
        return second;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public C getThird() {
        return third;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public A component1() {
        return first;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public B component2() {
        return second;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public C component3() {
        return third;
    }
}
