package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/** Clean model of Kotlin's {@code Pair} (the {@code a to b} tuple), enough for first/second,
 *  destructuring ({@code component1}/{@code component2}) and the data-class {@code copy}. The only
 *  remaining auto-generated member, {@code toString}, formats via {@code String.valueOf} of the
 *  components — out of scope for the bounded model, so it stays a loud {@link BmcUnmodelable} stub. */
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

    /** Data-class {@code copy}: a new {@code Pair} with the given (defaulted to current) components. */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public Pair<A, B> copy(A first, B second) {
        return new Pair<>(first, second);
    }
}
