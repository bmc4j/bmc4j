package java.util;

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

    @Override
    public void clear() {
        size = 0;
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
