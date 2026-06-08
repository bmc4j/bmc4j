package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;

/** Clean model of Kotlin's {@code Triple} — first/second/third, destructuring
 *  ({@code component1}/{@code component2}/{@code component3}) and the data-class {@code copy}. Mirrors
 *  {@link Pair}; built directly ({@code Triple(a, b, c)}), there is no infix factory like {@code to}.
 *  The only other auto-generated member is {@code toString} (an {@code Object} override — out of the
 *  per-member audit surface), so the whole surface is accounted without a {@code @BmcModelTail}. */
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

    /** Data-class {@code copy}: a new {@code Triple} with the given (defaulted to current) components. */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public Triple<A, B, C> copy(A first, B second, C third) {
        return new Triple<>(first, second, third);
    }
}
