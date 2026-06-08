package java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.CopyOnWriteArrayList} — functionally the bmc4j
 * bounded ArrayList model (bmc4j proves logic, not interleavings). The copy-on-write snapshot is a
 * concurrency concern that is invisible on the single thread BMC analyzes, so the list ops are exactly
 * the inherited array-backed semantics; the COW-specific <em>set-add</em> helpers ({@code addIfAbsent}/
 * {@code addAllAbsent}) and the from-index search overloads are modeled here over that backing.
 */
@BmcModelTail(reason = "snapshot/array-view extras (getArray/clone/toArray(IntFunction)/parallelStream/spliterator), Deque surface, listIterator/subList — out of scope for this sequential array-backed model; all loud under JBMC")
public class CopyOnWriteArrayList<E> extends ArrayList<E> {

    public CopyOnWriteArrayList() {
        super();
    }

    /** Append {@code e} only if not already present (by {@code equals}); returns whether it was added. */
    @BmcModelConforms("differential (CopyOnWriteArrayList set-add surface)")
    public boolean addIfAbsent(E e) {
        if (contains(e)) {
            return false;
        }
        add(e);
        return true;
    }

    /** Append each element of {@code c} not already present (treating earlier additions as present); returns the count added. */
    @BmcModelConforms("differential (CopyOnWriteArrayList set-add surface)")
    public int addAllAbsent(Collection<? extends E> c) {
        int added = 0;
        for (E e : c) {
            if (addIfAbsent(e)) {
                added++;
            }
        }
        return added;
    }

    /** First index of {@code e} at or after {@code index} (by {@code equals}), or -1. */
    @BmcModelConforms("differential (CopyOnWriteArrayList from-index search)")
    public int indexOf(E e, int index) {
        for (int i = index; i < size(); i++) {
            E cur = get(i);
            if (e == null ? cur == null : e.equals(cur)) {
                return i;
            }
        }
        return -1;
    }

    /** Last index of {@code e} at or before {@code index} (by {@code equals}), or -1. */
    @BmcModelConforms("differential (CopyOnWriteArrayList from-index search)")
    public int lastIndexOf(E e, int index) {
        for (int i = index; i >= 0; i--) {
            E cur = get(i);
            if (e == null ? cur == null : e.equals(cur)) {
                return i;
            }
        }
        return -1;
    }
}
