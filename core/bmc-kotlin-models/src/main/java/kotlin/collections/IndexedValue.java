package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of Kotlin's {@code kotlin.collections.IndexedValue} (the element/index pair produced by
 * {@code withIndex()} / {@code *Indexed} ops), enough for {@code index}/{@code value} access and
 * destructuring ({@code component1}/{@code component2}). The real class is a {@code data class}; the
 * auto-generated remainder (copy/toString/equals/hashCode) is not exercised by the bounded proofs and
 * is left in the loud tail.
 */
@BmcModelTail(reason = "IndexedValue data-class auto-generated surface (copy/toString/equals/hashCode) "
        + "not exercised by the bounded proofs; loud under JBMC if reached")
public final class IndexedValue<T> {

    private final int index;
    private final T value;

    public IndexedValue(int index, T value) {
        this.index = index;
        this.value = value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public int getIndex() {
        return index;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public T getValue() {
        return value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public int component1() {
        return index;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public T component2() {
        return value;
    }
}
