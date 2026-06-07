package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * Clean BMC model of {@link java.util.ArrayList}: a fixed-capacity backing array plus a size.
 * All operations are plain array reads/writes — sound in CBMC and trivially bounded. Lookup loops
 * ({@code contains}/{@code indexOf}/iteration) unwind to the current size, so keep lists within the
 * proof's {@code unwind} bound. Capacity is {@value #CAPACITY}; adding beyond it is out of bounds.
 *
 * <p>Element equality in {@code contains}/{@code indexOf} uses {@code equals} — sound for boxed
 * primitives (modeled). String elements use JBMC's native {@code String.equals}; prefer the
 * dedicated string support for string-keyed lookups.
 */
@BmcModelConforms("array-backed list — differential (ArrayListConformanceTest) + @BmcProof (proofs.arraylist); incl. the modeled bulk/functional ops addAll(Collection)/removeAll/retainAll/forEach/removeIf/toArray()")
@BmcModelTail(reason = "exotic remainder: SequencedCollection deque surface (addFirst/getLast/reversed/…), listIterator/subList/spliterator/parallelStream, capacity tuning (ensureCapacity/trimToSize), removeRange — out of scope for a bounded array-backed model; all loud under JBMC")
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
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean add(E e) {
        elements[size] = e;
        size++;
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return (E) elements[index];
    }

    @Override
    @SuppressWarnings("unchecked")
    public E set(int index, E element) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        E old = (E) elements[index];
        elements[index] = element;
        return old;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
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
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    @Override
    public void clear() {
        size = 0;
    }

    // --- bulk ops over the bounded backing array -----------------------------
    // addAll appends (loud out-of-bounds past CAPACITY, never a silent drop); removeAll/retainAll/
    // removeIf compact in place; forEach/toArray read in index order. Functional arguments are plain
    // SAM calls (bmc4j desugars the lambda so JBMC devirtualizes test/accept).

    @Override
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
    public boolean removeAll(Collection<?> c) {
        return removeWhere(c, true);
    }

    @Override
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
    public void forEach(java.util.function.Consumer<? super E> action) {
        for (int i = 0; i < size; i++) {
            action.accept((E) elements[i]);
        }
    }

    @Override
    public Object[] toArray() {
        Object[] out = new Object[size];
        for (int i = 0; i < size; i++) {
            out[i] = elements[i];
        }
        return out;
    }

    // --- explicitly UNMODELLED members ---------------------------------------
    // Real ArrayList members this bounded array-backed model deliberately does not implement. Each is
    // a declared stub with a LOUD body (routed through the BmcUnmodelledReached sentinel), so reaching
    // one is honestly UNKNOWN (a model gap) — never a silent nondet stub, never a false refutation.
    // The decision (@BmcNotModelled = "can't"; @BmcNotNeeded = "not worth it") + reason live ON the
    // stub, next to the surface it waives.

    @BmcNotModelled(reason = "functional-arg map — JBMC stubs the operator dispatch")
    public void replaceAll(java.util.function.UnaryOperator<E> operator) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.replaceAll(java.util.function.UnaryOperator) — functional-arg map — JBMC stubs the operator dispatch");
    }

    @BmcNotModelled(reason = "comparator-driven sort over the bounded array — not modeled")
    public void sort(Comparator<? super E> c) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.sort(java.util.Comparator) — comparator-driven sort over the bounded array — not modeled");
    }

    @BmcNotNeeded(reason = "positional bulk add — exotic; add elements explicitly")
    public boolean addAll(int index, Collection<? extends E> c) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.addAll(int,java.util.Collection) — positional bulk add — exotic; add elements explicitly");
    }

    @BmcNotNeeded(reason = "bulk membership — compose contains() explicitly")
    public boolean containsAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.containsAll(java.util.Collection) — bulk membership — compose contains() explicitly");
    }

    @BmcNotNeeded(reason = "positional insert — exotic; append + shift not modeled")
    public void add(int index, E element) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.add(int,java.lang.Object) — positional insert — exotic; append + shift not modeled");
    }

    @BmcNotNeeded(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcNotNeeded(reason = "shallow copy of a bounded model — construct a fresh list from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.ArrayList.clone() — shallow copy of a bounded model — construct a fresh list from the elements instead");
    }

    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    @Override
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
}
