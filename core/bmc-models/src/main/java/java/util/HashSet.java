package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * Clean BMC model of {@link java.util.HashSet}: a fixed-capacity array with dedup on {@code add}
 * (linear membership check). Sound and bounded — membership/iteration unwind to the current size.
 * Element equality uses {@code equals} (sound for boxed primitives). Capacity is {@value #CAPACITY}.
 */
@BmcModelConforms("dedup array set — differential (SetConformanceTest) + @BmcProof (proofs.hashset); incl. stream() (thin ListStream adapter)")
@BmcModelTail(reason = "exotic remainder: newHashSet(int) factory, spliterator/parallelStream, toArray(IntFunction) — out of scope; all loud under JBMC")
public class HashSet<E> implements Set<E> {

    private static final int CAPACITY = 64;

    private final Object[] elements = new Object[CAPACITY];
    private int size;

    public HashSet() {
    }

    public HashSet(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
    }

    /**
     * Copy constructor: a new set of {@code c}'s distinct elements (dedup via {@code equals}), like
     * {@code new HashSet<>(collection)}. The distinct count is bounded by capacity {@value #CAPACITY}.
     */
    public HashSet(Collection<? extends E> c) {
        for (E e : c) {
            add(e);
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
        if (indexOf(e) >= 0) {
            return false;
        }
        elements[size] = e;
        size++;
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        for (int j = i; j < size - 1; j++) {
            elements[j] = elements[j + 1];
        }
        size--;
        return true;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    /** A sequential stream over the set's elements — a thin adapter over the existing ListStream. */
    @Override
    @SuppressWarnings("unchecked")
    public java.util.stream.Stream<E> stream() {
        ArrayList<E> snapshot = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            snapshot.add((E) elements[i]);
        }
        return new java.util.stream.ListStream<>(snapshot);
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ----------------

    @BmcNotModelled(reason = "functional-arg iteration — iterate explicitly")
    public void forEach(java.util.function.Consumer<? super E> action) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.forEach(java.util.function.Consumer) — functional-arg iteration — iterate explicitly");
    }

    @BmcNotModelled(reason = "functional-arg filter — JBMC stubs the predicate dispatch")
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.removeIf(java.util.function.Predicate) — functional-arg filter — JBMC stubs the predicate dispatch");
    }

    @BmcNotNeeded(reason = "bulk add — add elements explicitly over the bounded model")
    public boolean addAll(Collection<? extends E> c) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.addAll(java.util.Collection) — bulk add — add elements explicitly over the bounded model");
    }

    @BmcNotNeeded(reason = "bulk membership — compose contains() explicitly")
    public boolean containsAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.containsAll(java.util.Collection) — bulk membership — compose contains() explicitly");
    }

    @BmcNotNeeded(reason = "bulk remove — compose remove() explicitly")
    public boolean removeAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.removeAll(java.util.Collection) — bulk remove — compose remove() explicitly");
    }

    @BmcNotNeeded(reason = "bulk retain — exotic over a bounded model")
    public boolean retainAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.retainAll(java.util.Collection) — bulk retain — exotic over a bounded model");
    }

    @BmcNotNeeded(reason = "array snapshot — iterate the model instead")
    public Object[] toArray() {
        throw fail("bmc4j: unmodelled member java.util.HashSet.toArray() — array snapshot — iterate the model instead");
    }

    @BmcNotNeeded(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.HashSet.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcNotNeeded(reason = "shallow copy of a bounded model — construct a fresh set from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.HashSet.clone() — shallow copy of a bounded model — construct a fresh set from the elements instead");
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
