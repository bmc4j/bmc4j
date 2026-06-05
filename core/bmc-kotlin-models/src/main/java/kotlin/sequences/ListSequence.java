package kotlin.sequences;

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
    public Iterator<T> iterator() {
        return data.iterator();
    }

    List<T> snapshot() {
        ArrayList<T> copy = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            copy.add(data.get(i));
        }
        return copy;
    }
}
