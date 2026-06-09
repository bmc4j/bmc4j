package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean model of Kotlin's {@code kotlin.collections.IndexedValue} (the element/index pair produced by
 * {@code withIndex()} / {@code *Indexed} ops): {@code index}/{@code value} access, destructuring
 * ({@code component1}/{@code component2}), and the data-class {@code copy(index, value)} (a fresh pair
 * with the chosen fields). The real class is a {@code data class}; its auto-generated
 * {@code equals}/{@code hashCode}/{@code toString} are pure {@link Object} overrides that JBMC analyzes
 * directly (no model needed), so they are not part of this model's own auditable surface.
 */
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

    /**
     * Data-class {@code copy}: a fresh {@code IndexedValue} with the chosen {@code index} and
     * {@code value}. A pure constructor call — no shared state with the receiver.
     */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public IndexedValue<T> copy(int index, T value) {
        return new IndexedValue<>(index, value);
    }
}
