package java.util.concurrent;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.ArrayList;
import java.util.Collection;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Sequential BMC model of {@link java.util.concurrent.CopyOnWriteArrayList} — functionally the bmc4j
 * bounded ArrayList model (bmc4j proves logic, not interleavings). The copy-on-write snapshot is a
 * concurrency concern that is invisible on the single thread BMC analyzes, so the list ops are exactly
 * the inherited array-backed semantics; the COW-specific <em>set-add</em> helpers ({@code addIfAbsent}/
 * {@code addAllAbsent}) and the from-index search overloads are modeled here over that backing.
 *
 * <p>The remaining inherited surface is classified per member here (the {@link ArrayList} model leaves
 * it in its own tail, and a superclass tail decision does not cover the subclass surface):
 * {@code toArray(IntFunction)} is a sequential snapshot and is modeled; {@code containsAll} is a bounded
 * sequential loop of {@code contains()}, modeled here directly (the {@link ArrayList} model leaves its
 * own {@code containsAll} loud, so it is overridden on this subclass rather than inherited);
 * {@code listIterator()}/{@code listIterator(int)} are the bidirectional {@code ListIterator} surface,
 * out of scope for the bounded array-backed model; {@code spliterator}/{@code parallelStream} are the
 * parallel-decomposition concurrency wall — those three stay loud-if-reached waivers.
 *
 * <p>The {@link ArrayList}-model loud stubs ({@code add(int, …)}/{@code addAll(int, …)}/
 * {@code clone}/{@code replaceAll}/{@code toArray(Object[])}) are
 * inherited unchanged (their loud bodies live on the ArrayList model); they are re-declared here as
 * class-level {@link BmcUnmodelable}(member=…) so the per-member gate accounts for them on this
 * subclass too (a superclass member-level waiver does not propagate to the subclass surface). The
 * {@code sort(Comparator)} member is now <em>modeled</em> on the ArrayList superclass (the nondet
 * sorted-permutation witness) and resolves through inheritance, so it needs no waiver here.
 */
@BmcUnmodelable(member = "add(int, java.lang.Object)", reason = "positional insert — inherited ArrayList-model loud stub; append + shift not modeled")
@BmcUnmodelable(member = "addAll(int, java.util.Collection)", reason = "positional bulk add — inherited ArrayList-model loud stub; add elements explicitly")
@BmcUnmodelable(member = "clone()", reason = "shallow copy of a bounded model — inherited ArrayList-model loud stub; construct a fresh list instead")
@BmcUnmodelable(member = "replaceAll(java.util.function.UnaryOperator)", reason = "functional-arg map — inherited ArrayList-model loud stub; JBMC stubs the operator dispatch")
@BmcUnmodelable(member = "toArray(java.lang.Object[])", reason = "typed array snapshot — inherited ArrayList-model loud stub; use toArray()/toArray(IntFunction) or iterate")
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

    /**
     * Bulk membership — true iff every element of {@code c} is present (by {@code equals}). A bounded
     * sequential loop of {@link #contains(Object)} over the backing; an empty {@code c} is vacuously true
     * (JDK semantics). Overridden here because the {@link ArrayList} model leaves its own {@code containsAll}
     * loud; single-thread observable: bulk membership is just a loop of point membership.
     */
    @Override
    @BmcModelConforms("differential (CopyOnWriteArrayList bulk membership over the array backing)")
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
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

    /**
     * Typed-array snapshot via a generator (sequential): allocate {@code generator.apply(size)} and fill
     * it from the backing in index order. Built directly over {@code get()/size()} (not via the inherited
     * {@code toArray(T[])}, which the ArrayList model leaves loud) so the snapshot is genuinely modeled.
     */
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (CopyOnWriteArrayList toArray(IntFunction) snapshot)")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        int n = size();
        T[] out = generator.apply(n);
        if (out.length < n) {
            out = (T[]) java.lang.reflect.Array.newInstance(out.getClass().getComponentType(), n);
        }
        for (int i = 0; i < n; i++) {
            out[i] = (T) get(i);
        }
        if (out.length > n) {
            out[n] = null;
        }
        return out;
    }

    // --- inherited surface left in the ArrayList model's tail, classified per member here ---------

    /** The bidirectional {@code ListIterator} surface — out of scope for the bounded array-backed model. */
    @BmcUnmodelable(reason = "the bidirectional ListIterator surface is out of scope for the bounded array-backed model — index with get()/size() instead")
    public java.util.ListIterator<E> listIterator() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CopyOnWriteArrayList.listIterator() — the bidirectional ListIterator surface is out of scope for the bounded array-backed model — index with get()/size() instead");
    }

    /** The bidirectional {@code ListIterator} surface — out of scope for the bounded array-backed model. */
    @BmcUnmodelable(reason = "the bidirectional ListIterator surface is out of scope for the bounded array-backed model — index with get()/size() instead")
    public java.util.ListIterator<E> listIterator(int index) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CopyOnWriteArrayList.listIterator(int) — the bidirectional ListIterator surface is out of scope for the bounded array-backed model — index with get()/size() instead");
    }

    /** Parallel-decomposition primitive — true-parallel split is the concurrency wall; index/iterate sequentially. */
    @BmcUnmodelable(reason = "spliterator's parallel split / true-parallel decomposition is the concurrency wall — use the sequential iterator()/get()/size() instead")
    public java.util.Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CopyOnWriteArrayList.spliterator() — spliterator's parallel split / true-parallel decomposition is the concurrency wall — use the sequential iterator()/get()/size() instead");
    }

    /** Parallel stream — true-parallel execution is the concurrency wall; use the sequential stream(). */
    @BmcUnmodelable(reason = "parallelStream's true-parallel execution is the concurrency wall — use the sequential stream() instead")
    public java.util.stream.Stream<E> parallelStream() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.CopyOnWriteArrayList.parallelStream() — parallelStream's true-parallel execution is the concurrency wall — use the sequential stream() instead");
    }
}
