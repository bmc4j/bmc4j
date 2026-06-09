package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean BMC model of {@link java.util.ArrayDeque}: a fixed-capacity array, insertion-ordered, with
 * the head at index 0 and the tail at index {@code size-1}. No comparator — elements are stored
 * opaquely (compared by {@code equals} for {@code contains}/{@code remove(Object)}), so there is no
 * Comparable dispatch to reason about. Sound and bounded — every op unwinds to the current size and
 * inserting past capacity {@value #CAPACITY} is a loud backing-array write (out of bounds), never a
 * silent drop.
 *
 * <p>Semantics match the JDK exactly: {@code getFirst}/{@code getLast}/{@code removeFirst}/
 * {@code removeLast}/{@code element}/{@code pop} throw {@link NoSuchElementException} on an empty
 * deque, while {@code peek*}/{@code poll*} return {@code null}. {@code offer}/{@code offerLast}
 * enqueue at the tail; {@code offerFirst}/{@code push}/{@code addFirst} insert at the head. The Queue
 * view is FIFO (enqueue at tail via {@code add}/{@code offer}, dequeue at head via {@code poll}/
 * {@code remove}); the Stack view is LIFO over the head ({@code push}/{@code pop}). The real
 * {@code ArrayDeque} forbids {@code null} elements (NPE on {@code add(null)} etc.); this model
 * mirrors that.
 */
public class ArrayDeque<E> implements Deque<E> {

    private static final int CAPACITY = 64;

    private final Object[] elements = new Object[CAPACITY];
    private int size;

    public ArrayDeque() {
    }

    /** A presized-but-empty deque; the capacity hint is observably irrelevant to this bounded model. */
    public ArrayDeque(int numElements) {
    }

    /**
     * Copy constructor: a new deque holding {@code c}'s elements in iteration order, like
     * {@code new ArrayDeque<>(collection)}. Bounded by capacity {@value #CAPACITY}; a source longer
     * than that overflows the backing array (loud out of bounds), matching the model's documented bound.
     */
    public ArrayDeque(Collection<? extends E> c) {
        for (E e : c) {
            addLast(e);
        }
    }

    private int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    private int lastIndexOf(Object o) {
        for (int i = size - 1; i >= 0; i--) {
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

    // --- head/tail insertion ---------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public void addFirst(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        for (int j = size; j > 0; j--) {
            elements[j] = elements[j - 1];     // shift right; out of bounds past CAPACITY → loud
        }
        elements[0] = e;
        size++;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public void addLast(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        elements[size] = e;                    // out of bounds past CAPACITY → loud model-bound signal
        size++;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public void push(E e) {
        addFirst(e);
    }

    // --- head/tail removal -----------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E removeFirst() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return removeAt(0);
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E removeLast() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return removeAt(size - 1);
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E pollFirst() {
        return size == 0 ? null : removeAt(0);
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E pollLast() {
        return size == 0 ? null : removeAt(size - 1);
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E pop() {
        return removeFirst();
    }

    // --- head/tail peek --------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E getFirst() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return (E) elements[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E getLast() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return (E) elements[size - 1];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E peekFirst() {
        return size == 0 ? null : (E) elements[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E peekLast() {
        return size == 0 ? null : (E) elements[size - 1];
    }

    // --- Queue surface (FIFO: enqueue at tail, dequeue at head) ----------------------------------

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean offer(E e) {
        return offerLast(e);
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E remove() {
        return removeFirst();
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E poll() {
        return pollFirst();
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E element() {
        return getFirst();
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public E peek() {
        return peekFirst();
    }

    // --- membership / occurrence removal ---------------------------------------------------------

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean removeFirstOccurrence(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        removeAt(i);
        return true;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean removeLastOccurrence(Object o) {
        int i = lastIndexOf(o);
        if (i < 0) {
            return false;
        }
        removeAt(i);
        return true;
    }

    // --- size / clear ----------------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public int size() {
        return size;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public void clear() {
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
    }

    // --- iteration -------------------------------------------------------------------------------

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public Iterator<E> iterator() {
        return new Itr();
    }

    @Override
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public Iterator<E> descendingIterator() {
        return new DescItr();
    }

    // --- bulk / functional ops -------------------------------------------------------------------
    // forEach reads head→tail; removeIf compacts in place; addAll appends at the tail (loud out of
    // bounds past CAPACITY, never a silent drop); removeAll/retainAll compact. Functional arguments
    // are plain SAM calls (bmc4j desugars the lambda so JBMC devirtualizes accept/test).

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public void forEach(java.util.function.Consumer<? super E> action) {
        for (int i = 0; i < size; i++) {
            action.accept((E) elements[i]);
        }
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
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

    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            addLast(e);        // out of bounds past CAPACITY → loud model-bound signal
            changed = true;
        }
        return changed;
    }

    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean removeAll(Collection<?> c) {
        return removeWhere(c, true);
    }

    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean retainAll(Collection<?> c) {
        return removeWhere(c, false);
    }

    /** Compact in place, dropping elements whose membership in {@code c} equals {@code removeMatched}. */
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

    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public boolean containsAll(Collection<?> c) {
        // Read an ArrayList argument's backing BY INDEX rather than via the interface-typed
        // c.iterator() — that virtual dispatch is devirtualization-fragile under JBMC (mirrors the
        // HashSet model's containsAll). A concrete-typed get(i) resolves soundly; other types fall back.
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

    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public Object[] toArray() {
        Object[] out = new Object[size];
        for (int i = 0; i < size; i++) {
            out[i] = elements[i];
        }
        return out;
    }

    /** A sequential stream over the elements in head→tail order — a thin adapter over ListStream. */
    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public java.util.stream.Stream<E> stream() {
        ArrayList<E> snapshot = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            snapshot.add((E) elements[i]);
        }
        return new java.util.stream.ListStream<>(snapshot);
    }

    /** Sequential BMC has one thread, so a parallel stream is observably the sequential {@link #stream()}. */
    @BmcModelConforms("differential (DequeConformanceTest) + @BmcProof (proofs.arraydeque)")
    public java.util.stream.Stream<E> parallelStream() {
        return stream();
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) -----------------

    @BmcUnmodelable(reason = "live reversed Deque view over the bounded array (Java 21+) — iterate descendingIterator()/the model instead; loud under JBMC")
    public Deque<E> reversed() {
        throw fail("bmc4j: unmodelled member java.util.ArrayDeque.reversed() — live reversed Deque view — iterate descendingIterator() or the model instead");
    }

    @BmcUnmodelable(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.ArrayDeque.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "array snapshot via a reflective IntFunction generator (creates a T[] of a reflective component type) — iterate the model instead")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        throw fail("bmc4j: unmodelled member java.util.ArrayDeque.toArray(java.util.function.IntFunction) — array snapshot via a reflective generator — iterate the model instead");
    }

    @BmcUnmodelable(reason = "parallel-decomposition Spliterator (a tryAdvance/trySplit traversal view a sequential bounded model can't represent) — iterate the model or use stream() instead")
    public java.util.Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.ArrayDeque.spliterator() — parallel-decomposition Spliterator view — iterate the model or use stream() instead");
    }

    @BmcUnmodelable(reason = "shallow copy of a bounded model — construct a fresh deque from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.ArrayDeque.clone() — shallow copy of a bounded model — construct a fresh deque from the elements instead");
    }

    // --- iterators ---------------------------------------------------------------------------------

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

    private final class DescItr implements Iterator<E> {
        private int cursor = size - 1;

        @Override
        public boolean hasNext() {
            return cursor >= 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor < 0) {
                throw new NoSuchElementException();
            }
            return (E) elements[cursor--];
        }
    }
}
