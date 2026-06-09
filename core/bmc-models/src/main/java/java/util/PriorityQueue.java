package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.PriorityQueue} that models the CONTRACT, not the binary heap. The
 * backing store is a fixed-capacity UNORDERED array; {@code peek}/{@code poll}/{@code element}/
 * {@code remove()} do a bounded LINEAR SCAN to select the LEAST element per the queue's ordering.
 * This is sound and bounded (the JDK only promises that the head is the least element — the heap is
 * an implementation detail), and it deliberately avoids modelling {@code siftUp}/{@code siftDown},
 * whose data-dependent index arithmetic is SAT-pathological.
 *
 * <p><b>Ordering.</b> A PriorityQueue is ordered either by a supplied {@link Comparator} or by the
 * elements' natural ordering:
 * <ul>
 *   <li><b>Comparator-provided:</b> the scan drives {@code comparator.compare(a, b)} directly. A
 *       desugared-lambda comparator devirtualizes under JBMC, so this is sound for symbolic elements.</li>
 *   <li><b>Natural order (no comparator):</b> dispatching a raw {@code Comparable.compareTo} over a
 *       nondet element is the unsound wall (the virtual target is unconstrained). Instead this model
 *       compares the BUILTIN Comparables (Integer/Long/Short/Byte/Character/Boolean/String) bit-precisely
 *       via an {@code instanceof} ladder, which is sound and bounded. A natural-order comparison of a
 *       non-builtin Comparable is a loud failure (never a silent raw {@code compareTo}).</li>
 * </ul>
 *
 * <p>Capacity is {@value #CAPACITY}; the real PriorityQueue forbids {@code null} elements (NPE on
 * {@code add(null)}), which this model mirrors. {@code offer}/{@code add} always return true within
 * capacity; inserting past capacity is a loud backing-array write (out of bounds), never a silent drop.
 * {@code peek}/{@code poll} return {@code null} on an empty queue; {@code element}/{@code remove()}
 * throw {@link NoSuchElementException}.
 */
public class PriorityQueue<E> implements Queue<E> {

    private static final int CAPACITY = 64;

    private final Object[] elements = new Object[CAPACITY];
    private int size;
    private final Comparator<? super E> comparator;

    public PriorityQueue() {
        this.comparator = null;
    }

    public PriorityQueue(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException();
        }
        this.comparator = null;
    }

    public PriorityQueue(Comparator<? super E> comparator) {
        this.comparator = comparator;
    }

    public PriorityQueue(int initialCapacity, Comparator<? super E> comparator) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException();
        }
        this.comparator = comparator;
    }

    /**
     * Copy constructor: a natural-order queue holding every element of {@code c} (in iteration order
     * in the backing store; the head is still the least under natural ordering). Bounded by capacity
     * {@value #CAPACITY}; a longer source overflows the backing array (loud out of bounds).
     */
    public PriorityQueue(Collection<? extends E> c) {
        this.comparator = null;
        for (E e : c) {
            offer(e);
        }
    }

    /**
     * Order two elements: negative if {@code a} sorts before {@code b}, positive after, zero if equal.
     * Drives the supplied comparator when present, else the builtin-Comparable ladder.
     */
    @SuppressWarnings("unchecked")
    private int order(E a, E b) {
        if (comparator != null) {
            return comparator.compare(a, b);
        }
        return naturalCompare(a, b);
    }

    /**
     * Natural-order comparison over the BUILTIN Comparables (bit-precise), avoiding a raw
     * {@code Comparable.compareTo} dispatch over a nondet element (the unsound wall). A non-builtin
     * Comparable is a loud failure. Mirrors the type-by-type comparison the JDK's natural ordering
     * performs, but resolved concretely so JBMC can reason about it.
     */
    private int naturalCompare(Object a, Object b) {
        if (a instanceof Integer && b instanceof Integer) {
            return Integer.compare((Integer) a, (Integer) b);
        }
        if (a instanceof Long && b instanceof Long) {
            return Long.compare((Long) a, (Long) b);
        }
        if (a instanceof Short && b instanceof Short) {
            return Short.compare((Short) a, (Short) b);
        }
        if (a instanceof Byte && b instanceof Byte) {
            return Byte.compare((Byte) a, (Byte) b);
        }
        if (a instanceof Character && b instanceof Character) {
            return Character.compare((Character) a, (Character) b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return Boolean.compare((Boolean) a, (Boolean) b);
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        throw fail("bmc4j: PriorityQueue natural-order comparison of a non-builtin Comparable element "
            + "(java.util.PriorityQueue compareTo over a nondet element is the unsound wall) — supply a "
            + "Comparator, or use a builtin Comparable (Integer/Long/Short/Byte/Character/Boolean/String)");
    }

    /** Index of the least element per the ordering (the JDK's head), or -1 when empty. */
    @SuppressWarnings("unchecked")
    private int leastIndex() {
        if (size == 0) {
            return -1;
        }
        int sel = 0;
        for (int i = 1; i < size; i++) {
            if (order((E) elements[i], (E) elements[sel]) < 0) {
                sel = i;
            }
        }
        return sel;
    }

    private int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private E removeAt(int i) {
        E removed = (E) elements[i];
        for (int j = i; j < size - 1; j++) {
            elements[j] = elements[j + 1];
        }
        size--;
        elements[size] = null;
        return removed;
    }

    // --- insertion -------------------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean add(E e) {
        return offer(e);
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        elements[size] = e;        // out of bounds past CAPACITY → loud model-bound signal
        size++;
        return true;
    }

    // --- least-element retrieval (linear scan) ---------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public E peek() {
        int i = leastIndex();
        return i < 0 ? null : (E) elements[i];
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public E poll() {
        int i = leastIndex();
        return i < 0 ? null : removeAt(i);
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public E element() {
        int i = leastIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return peek();
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public E remove() {
        int i = leastIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return removeAt(i);
    }

    // --- membership / removal --------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        removeAt(i);
        return true;
    }

    // --- size / clear ----------------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public int size() {
        return size;
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public void clear() {
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
    }

    /** The ordering comparator, or {@code null} for a natural-order queue (like the JDK). */
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public Comparator<? super E> comparator() {
        return comparator;
    }

    // --- iteration (unordered, like the JDK's heap-array iterator) -------------------------------

    @Override
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public Iterator<E> iterator() {
        return new Itr();
    }

    // --- bulk / functional ops -------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public void forEach(java.util.function.Consumer<? super E> action) {
        for (int i = 0; i < size; i++) {
            action.accept((E) elements[i]);
        }
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        int w = 0;
        boolean changed = false;
        for (int r = 0; r < size; r++) {
            E e = (E) elements[r];
            if (filter.test(e)) {
                changed = true;
            } else {
                elements[w++] = e;
            }
        }
        for (int i = w; i < size; i++) {
            elements[i] = null;
        }
        size = w;
        return changed;
    }

    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            offer(e);          // out of bounds past CAPACITY → loud model-bound signal
            changed = true;
        }
        return changed;
    }

    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean removeAll(Collection<?> c) {
        return removeWhere(c, true);
    }

    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean retainAll(Collection<?> c) {
        return removeWhere(c, false);
    }

    private boolean removeWhere(Collection<?> c, boolean removeMatched) {
        int w = 0;
        boolean changed = false;
        for (int r = 0; r < size; r++) {
            Object e = elements[r];
            if (c.contains(e) == removeMatched) {
                changed = true;     // dropped
            } else {
                elements[w++] = e;  // kept
            }
        }
        for (int i = w; i < size; i++) {
            elements[i] = null;
        }
        size = w;
        return changed;
    }

    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public boolean containsAll(Collection<?> c) {
        if (c instanceof ArrayList) {
            ArrayList<?> a = (ArrayList<?>) c;
            int n = a.size();
            for (int i = 0; i < n; i++) {
                if (!contains(a.get(i))) {
                    return false;
                }
            }
            return true;
        }
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    // --- array snapshot / streams ----------------------------------------------------------------

    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public Object[] toArray() {
        Object[] out = new Object[size];
        for (int i = 0; i < size; i++) {
            out[i] = elements[i];
        }
        return out;
    }

    /** A sequential stream over the elements (unordered, like the JDK's heap-array stream). */
    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public java.util.stream.Stream<E> stream() {
        ArrayList<E> snapshot = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            snapshot.add((E) elements[i]);
        }
        return new java.util.stream.ListStream<>(snapshot);
    }

    /** Sequential BMC has one thread, so a parallel stream is observably the sequential {@link #stream()}. */
    @BmcModelConforms("differential (PriorityQueueConformanceTest) + @BmcProof (proofs.priorityqueue)")
    public java.util.stream.Stream<E> parallelStream() {
        return stream();
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) -----------------

    @BmcUnmodelable(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.PriorityQueue.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "array snapshot via a reflective IntFunction generator (creates a T[] of a reflective component type) — iterate the model instead")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        throw fail("bmc4j: unmodelled member java.util.PriorityQueue.toArray(java.util.function.IntFunction) — array snapshot via a reflective generator — iterate the model instead");
    }

    @BmcUnmodelable(reason = "parallel-decomposition Spliterator (a tryAdvance/trySplit traversal view a sequential bounded model can't represent) — iterate the model or use stream() instead")
    public java.util.Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.PriorityQueue.spliterator() — parallel-decomposition Spliterator view — iterate the model or use stream() instead");
    }

    // --- iterator --------------------------------------------------------------------------------

    private final class Itr implements Iterator<E> {
        private int cursor;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor >= size) {
                throw new NoSuchElementException();
            }
            return (E) elements[cursor++];
        }
    }
}
