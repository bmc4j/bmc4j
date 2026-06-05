package java.util;

/**
 * Clean BMC model of {@link java.util.HashSet}: a fixed-capacity array with dedup on {@code add}
 * (linear membership check). Sound and bounded — membership/iteration unwind to the current size.
 * Element equality uses {@code equals} (sound for boxed primitives). Capacity is {@value #CAPACITY}.
 */
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
