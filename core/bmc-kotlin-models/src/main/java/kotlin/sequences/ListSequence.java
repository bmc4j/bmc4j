package kotlin.sequences;

import org.bmc4j.models.audit.BmcModelConforms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Eager, array-backed {@link Sequence} model, mirroring the {@code ListStream} approach. Each
 * intermediate op produces a fresh sequence over a bounded {@link ArrayList} (the conformance-tested
 * bounded collection model). Eager evaluation is sound for the bounded element counts JBMC unwinds.
 */
public final class ListSequence<T> implements Sequence<T> {

    final ArrayList<T> data;

    public ListSequence(ArrayList<T> data) {
        this.data = data;
    }

    public ListSequence(T[] values) {
        this.data = new ArrayList<>();
        for (T v : values) {
            data.add(v);
        }
    }

    public ListSequence(Iterable<T> source) {
        this.data = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            data.add(it.next());
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public Iterator<T> iterator() {
        return data.iterator();
    }

    public List<T> snapshot() {
        ArrayList<T> copy = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            copy.add(data.get(i));
        }
        return copy;
    }

    /**
     * The concrete backing list, returned DIRECTLY (no copy) — the {@code CollectionsKt}/{@code MapsKt}/
     * {@code SetsKt} facades drain a {@code Sequence} parameter via this concrete {@link ArrayList} (whose
     * {@code iterator()} JBMC resolves) rather than the virtual {@code Sequence.iterator()}, mirroring
     * {@code SequencesKt}'s {@code backing}. Returning {@code data} directly avoids the extra bounded
     * element-by-element copy {@link #snapshot()} performs (which doubled the symbolic proof circuit).
     */
    public ArrayList<T> backingList() {
        return data;
    }
}
