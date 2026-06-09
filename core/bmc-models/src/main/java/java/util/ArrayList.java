package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean BMC model of {@link java.util.ArrayList}: a fixed-capacity backing array plus a size.
 * All operations are plain array reads/writes — sound in CBMC and trivially bounded. Lookup loops
 * ({@code contains}/{@code indexOf}/iteration) unwind to the current size, so keep lists within the
 * proof's {@code unwind} bound. Capacity is {@value #CAPACITY}; adding beyond it is out of bounds.
 *
 * <p>Element equality in {@code contains}/{@code indexOf} uses {@code equals} — sound for boxed
 * primitives (modeled). String elements use JBMC's native {@code String.equals}; prefer the
 * dedicated string support for string-keyed lookups.
 *
 * <p>The view ops are genuine LIVE views over this backing (write-through, by index): {@code subList}
 * is a forward window, {@code reversed()} is a reverse view, {@code listIterator} is a bidirectional
 * by-index cursor, all over {@link ArrayListView} / the {@code ListItr} cursor. {@code ensureCapacity}/
 * {@code trimToSize} are observable no-ops (the capacity is fixed); {@code removeRange} deletes a
 * bounded slice in place; {@code parallelStream} is the sequential {@code stream()} (sequential BMC has
 * one thread). The {@code spliterator} parallel-decomposition view and the reflective
 * {@code toArray(IntFunction)} generator are loud {@link BmcUnmodelable}.
 */
public class ArrayList<E> implements List<E> {

    private static final int CAPACITY = 64;

    private final Object[] elements;
    private int size;

    public ArrayList() {
        elements = new Object[CAPACITY];
        size = 0;
    }

    public ArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
        }
        elements = new Object[CAPACITY];
        size = 0;
    }

    /**
     * Copy constructor: a new list holding {@code c}'s elements in iteration order, like
     * {@code new ArrayList<>(collection)}. Bounded by capacity {@value #CAPACITY}; a source longer
     * than that overflows the backing array (out of bounds), matching the model's documented bound.
     */
    public ArrayList(Collection<? extends E> c) {
        elements = new Object[CAPACITY];
        size = 0;
        for (E e : c) {
            elements[size] = e;
            size++;
        }
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public int size() {
        return size;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean add(E e) {
        elements[size] = e;
        size++;
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return (E) elements[index];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E set(int index, E element) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        E old = (E) elements[index];
        elements[index] = element;
        return old;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public int lastIndexOf(Object o) {
        for (int i = size - 1; i >= 0; i--) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    // --- SequencedCollection (Java 21+) head/tail ops over the bounded backing array ---------------
    // addFirst inserts at index 0 (shift right, loud past capacity); addLast appends. get/removeFirst/
    // -Last throw NoSuchElementException on an empty list, exactly like the JDK. The LinkedList model
    // inherits these unchanged (its array-backed semantics are identical), so deque/SequencedCollection
    // use on either list resolves to one sound body rather than a silent nondet stub.

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E getFirst() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return (E) elements[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E getLast() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return (E) elements[size - 1];
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void addFirst(E e) {
        // Append a slot (loud if past capacity), then shift the tail one position toward the end.
        elements[size] = null;
        size++;
        for (int i = size - 1; i > 0; i--) {
            elements[i] = elements[i - 1];
        }
        elements[0] = e;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void addLast(E e) {
        add(e);
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E removeFirst() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return remove(0);
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E removeLast() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return remove(size - 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public E remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        E old = (E) elements[index];
        for (int i = index; i < size - 1; i++) {
            elements[i] = elements[i + 1];
        }
        size--;
        return old;
    }

    /**
     * Removes the first element equal to {@code o} (by {@code equals}), shifting the tail down;
     * returns whether an element was removed. The {@code Collection.remove(Object)} overload —
     * distinct from {@link #remove(int)}. Without it, analysed {@code list.remove(element)} calls
     * (notably Kotlin's {@code MutableList.remove}) resolved to a JBMC nondet stub.
     */
    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void clear() {
        size = 0;
    }

    // --- bulk ops over the bounded backing array -----------------------------
    // addAll appends (loud out-of-bounds past CAPACITY, never a silent drop); removeAll/retainAll/
    // removeIf compact in place; forEach/toArray read in index order. Functional arguments are plain
    // SAM calls (bmc4j desugars the lambda so JBMC devirtualizes test/accept).

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            elements[size] = e;     // out of bounds past CAPACITY → loud model-bound signal
            size++;
            changed = true;
        }
        return changed;
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean removeAll(Collection<?> c) {
        return removeWhere(c, true);
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
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
                changed = true;             // dropped
            } else {
                elements[w++] = e;          // kept
            }
        }
        size = w;
        return changed;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
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
        size = w;
        return changed;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void forEach(java.util.function.Consumer<? super E> action) {
        for (int i = 0; i < size; i++) {
            action.accept((E) elements[i]);
        }
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public Object[] toArray() {
        Object[] out = new Object[size];
        for (int i = 0; i < size; i++) {
            out[i] = elements[i];
        }
        return out;
    }

    // --- functional / positional bulk ops over the bounded backing array (modeled) ------------------
    // replaceAll maps each slot through the operator in index order; containsAll reuses contains(); the
    // positional add/addAll shift the tail right (loud out-of-bounds past CAPACITY). Functional args are
    // plain SAM calls (bmc4j desugars the lambda so JBMC devirtualizes apply).

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void replaceAll(java.util.function.UnaryOperator<E> operator) {
        for (int i = 0; i < size; i++) {
            elements[i] = operator.apply((E) elements[i]);
        }
    }

    /** Bulk membership: true iff every element of {@code c} is contained here (reuses {@link #contains}). */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean containsAll(Collection<?> c) {
        // When the argument is itself an ArrayList model, read its backing BY INDEX rather than via the
        // interface-typed c.iterator(): that virtual dispatch on the Collection parameter is
        // devirtualization-fragile under JBMC — the iterator's index can go nondet, producing a false
        // counterexample (notably on the negative "element absent" case). A concrete-typed get(i) is
        // resolved soundly. Other Collection types fall back to the iterator.
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

    /**
     * Positional insert: shift the tail one slot right (loud out-of-bounds past CAPACITY), then place
     * {@code element} at {@code index}. {@code index == size} appends. Out-of-range indices throw, like
     * the JDK.
     */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void add(int index, E element) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException();
        }
        elements[size] = null;          // claim a slot (loud if past capacity)
        size++;
        for (int i = size - 1; i > index; i--) {
            elements[i] = elements[i - 1];
        }
        elements[index] = element;
    }

    /**
     * Positional bulk add: insert {@code c}'s elements (in iteration order) starting at {@code index},
     * shifting the tail right. {@code index == size} appends. Loud out-of-bounds past CAPACITY.
     */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public boolean addAll(int index, Collection<? extends E> c) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException();
        }
        boolean changed = false;
        int at = index;
        for (E e : c) {
            add(at, e);                 // shift-and-insert each element in turn
            at++;
            changed = true;
        }
        return changed;
    }

    // --- view ops: genuine LIVE views over the bounded backing array (modeled) ----------------------
    // subList is a forward window, reversed() a reverse view, listIterator a bidirectional by-index
    // cursor — every read/write translates BY INDEX to this backing list (never via a virtual iterator),
    // so they write through exactly like the JDK's AbstractList views. Sound + bounded under JBMC.

    /** A live forward-window view over {@code [fromIndex, toIndex)} (writes through to this list). */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public List<E> subList(int fromIndex, int toIndex) {
        return ArrayListView.subList(this, fromIndex, toIndex);
    }

    /** A live reverse view over this list (writes through to this list). */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public List<E> reversed() {
        return ArrayListView.reversed(this);
    }

    /** A bidirectional by-index cursor over the bounded backing array, positioned at the start. */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public ListIterator<E> listIterator() {
        return new ListItr(0);
    }

    /** A bidirectional by-index cursor positioned at {@code index} (0..size). */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException();
        }
        return new ListItr(index);
    }

    /** Sequential BMC has one thread, so a parallel stream is observably the sequential {@link #stream()}. */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public java.util.stream.Stream<E> parallelStream() {
        return stream();
    }

    // --- capacity tuning + range removal over the bounded backing array (modeled) -------------------
    // The backing capacity is FIXED at CAPACITY, so ensureCapacity/trimToSize are observable no-ops
    // (they only affect resizing, which this fixed array never does — size/get/iteration are unchanged).
    // removeRange deletes the [fromIndex, toIndex) slice in place, shifting the tail down.

    /** No-op: the backing capacity is fixed, so growth pre-allocation is unobservable. */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void ensureCapacity(int minCapacity) {
        // fixed-capacity backing array — nothing to grow; observably a no-op.
    }

    /** No-op: the backing capacity is fixed, so trimming to size is unobservable. */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public void trimToSize() {
        // fixed-capacity backing array — nothing to trim; observably a no-op.
    }

    /** Remove the {@code [fromIndex, toIndex)} slice in place, shifting the tail down (protected, like the JDK). */
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    protected void removeRange(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException();
        }
        int span = toIndex - fromIndex;
        for (int i = toIndex; i < size; i++) {
            elements[i - span] = elements[i];
        }
        size -= span;
    }

    // --- explicitly UNMODELLED members ---------------------------------------
    // Real ArrayList members this bounded array-backed model deliberately does not implement. Each is
    // a declared stub with a LOUD body (routed through the BmcUnmodelledReached sentinel), so reaching
    // one is honestly UNKNOWN (a model gap) — never a silent nondet stub, never a false refutation.
    // The decision (@BmcUnmodelable = "can't"; @BmcUnmodelable = "not worth it") + reason live ON the
    // stub, next to the surface it waives.

    @BmcUnmodelable(reason = "parallel-decomposition Spliterator (a tryAdvance/trySplit traversal view a sequential bounded model can't represent) — iterate the model or use stream() instead")
    public Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.spliterator() — parallel-decomposition Spliterator view — iterate the model or use stream() instead");
    }

    @BmcUnmodelable(reason = "array snapshot via a reflective IntFunction generator (creates a T[] of a reflective component type) — iterate the model instead")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.toArray(java.util.function.IntFunction) — array snapshot via a reflective generator — iterate the model instead");
    }

    @BmcUnmodelable(reason = "comparator-driven sort over the bounded array: a bounded insertion sort calling the comparator is modelable but O(n^2) symbolic comparisons are SAT-heavy and rarely the thing under proof — not worth it")
    public void sort(Comparator<? super E> c) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.sort(java.util.Comparator) — comparator-driven sort over the bounded array: O(n^2) symbolic comparisons are SAT-heavy and rarely the thing under proof");
    }

    @BmcUnmodelable(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "shallow copy of a bounded model — construct a fresh list from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.clone() — shallow copy of a bounded model — construct a fresh list from the elements instead");
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public Iterator<E> iterator() {
        return new Itr();
    }

    @Override
    @BmcModelConforms("differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist)")
    public java.util.stream.Stream<E> stream() {
        return new java.util.stream.ListStream<>(this);
    }

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

    /**
     * Bidirectional by-index cursor over the bounded backing array — a sound {@link ListIterator} that
     * writes through to the enclosing list. {@code last} tracks the index returned by the most recent
     * {@code next}/{@code previous} so {@code set}/{@code remove} target it, exactly like the JDK cursor.
     */
    private final class ListItr implements ListIterator<E> {
        private int cursor;
        private int last = -1;

        ListItr(int start) {
            cursor = start;
        }

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
            last = cursor;
            return (E) elements[cursor++];
        }

        @Override
        public boolean hasPrevious() {
            return cursor > 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E previous() {
            if (cursor <= 0) {
                throw new NoSuchElementException();
            }
            cursor--;
            last = cursor;
            return (E) elements[cursor];
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void set(E e) {
            if (last < 0) {
                throw new IllegalStateException();
            }
            ArrayList.this.set(last, e);
        }

        @Override
        public void add(E e) {
            ArrayList.this.add(cursor, e);
            cursor++;
            last = -1;
        }

        @Override
        public void remove() {
            if (last < 0) {
                throw new IllegalStateException();
            }
            ArrayList.this.remove(last);
            if (last < cursor) {
                cursor--;
            }
            last = -1;
        }
    }
}
