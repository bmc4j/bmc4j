package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * Clean BMC model of {@link java.util.HashSet}: a fixed-capacity array with dedup on {@code add}
 * (linear membership check). Sound and bounded — membership/iteration unwind to the current size.
 * Element equality uses {@code equals} (sound for boxed primitives). Capacity is {@value #CAPACITY}.
 */
@BmcModelConforms("dedup array set — differential (SetConformanceTest) + @BmcProof (proofs.hashset)")
@BmcNotModelled(member = "forEach(java.util.function.Consumer)", reason = "functional-arg iteration — iterate explicitly")
@BmcNotModelled(member = "removeIf(java.util.function.Predicate)", reason = "functional-arg filter — JBMC stubs the predicate dispatch")
@BmcNotNeeded(member = "addAll(java.util.Collection)", reason = "bulk add — add elements explicitly over the bounded model")
@BmcNotNeeded(member = "containsAll(java.util.Collection)", reason = "bulk membership — compose contains() explicitly")
@BmcNotNeeded(member = "removeAll(java.util.Collection)", reason = "bulk remove — compose remove() explicitly")
@BmcNotNeeded(member = "retainAll(java.util.Collection)", reason = "bulk retain — exotic over a bounded model")
@BmcNotNeeded(member = "toArray()", reason = "array snapshot — iterate the model instead")
@BmcNotNeeded(member = "toArray(java.lang.Object[])", reason = "typed array snapshot — iterate the model instead")
@BmcModelTail(reason = "exotic remainder: newHashSet(int) factory, clone(), spliterator/parallelStream, toArray(IntFunction) — out of scope; all loud under JBMC")
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
