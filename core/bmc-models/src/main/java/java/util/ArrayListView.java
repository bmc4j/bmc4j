package java.util;

/**
 * A genuine LIVE view over an {@link ArrayList} backing, supporting the two view shapes the list models
 * return: a {@code subList(from, to)} forward window and a {@code reversed()} reverse view. Every read
 * and structural write is translated BY INDEX to the backing list (never via a virtual iterator), so the
 * view writes through to the parent exactly like the JDK's {@code AbstractList} sublist / reversed view —
 * sound and bounded under JBMC (each scan unwinds to the view's size).
 *
 * <p>Two shapes, selected at construction:
 * <ul>
 *   <li><b>forward window</b> ({@code reversed == false}): a {@code [offset, offset+size)} slice; view
 *       index {@code i} maps to backing index {@code offset + i}. The window tracks its own {@code size};
 *       structural writes shift the backing array and adjust the window length, like the JDK sublist.</li>
 *   <li><b>reverse view</b> ({@code reversed == true}): the whole backing list in reverse; view index
 *       {@code i} maps to backing index {@code parent.size() - 1 - i}. {@code add(e)} prepends to the
 *       parent (so it appears at the reversed end), {@code add(i, e)} inserts at the mirrored position.</li>
 * </ul>
 *
 * <p>This is not part of the audited real-class surface (no JDK twin) — it is an internal model helper,
 * so it carries no audit annotations.
 */
final class ArrayListView<E> implements List<E> {

    private final ArrayList<E> backing;
    private final boolean reversed;
    private final int offset;   // forward window only
    private int size;           // forward window only (reverse tracks parent.size())

    private ArrayListView(ArrayList<E> backing, boolean reversed, int offset, int size) {
        this.backing = backing;
        this.reversed = reversed;
        this.offset = offset;
        this.size = size;
    }

    /** A forward window over {@code [from, to)} of {@code backing}. */
    static <E> ArrayListView<E> subList(ArrayList<E> backing, int from, int to) {
        if (from < 0 || to > backing.size() || from > to) {
            throw new IndexOutOfBoundsException();
        }
        return new ArrayListView<>(backing, false, from, to - from);
    }

    /** A reverse view over the whole of {@code backing}. */
    static <E> ArrayListView<E> reversed(ArrayList<E> backing) {
        return new ArrayListView<>(backing, true, 0, 0);
    }

    /** Backing index for view index {@code i} (no bounds check). */
    private int backingIndex(int i) {
        return reversed ? backing.size() - 1 - i : offset + i;
    }

    @Override
    public int size() {
        return reversed ? backing.size() : size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        return backing.get(backingIndex(index));
    }

    @Override
    public E set(int index, E element) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        return backing.set(backingIndex(index), element);
    }

    @Override
    public boolean add(E e) {
        if (reversed) {
            backing.add(0, e);          // the reversed end is the parent's front
        } else {
            backing.add(offset + size, e);
            size++;
        }
        return true;
    }

    /** Positional insert into the view, translated to the backing index. */
    public void add(int index, E element) {
        if (index < 0 || index > size()) {
            throw new IndexOutOfBoundsException();
        }
        if (reversed) {
            backing.add(backing.size() - index, element);
        } else {
            backing.add(offset + index, element);
            size++;
        }
    }

    @Override
    public E remove(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        E old = backing.remove(backingIndex(index));
        if (!reversed) {
            size--;
        }
        return old;
    }

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
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        int n = size();
        for (int i = 0; i < n; i++) {
            E e = get(i);
            if (o == null ? e == null : o.equals(e)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        for (int i = size() - 1; i >= 0; i--) {
            E e = get(i);
            if (o == null ? e == null : o.equals(e)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public E getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return get(0);
    }

    @Override
    public E getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return get(size() - 1);
    }

    @Override
    public void addFirst(E e) {
        add(0, e);
    }

    @Override
    public void addLast(E e) {
        add(size(), e);
    }

    @Override
    public E removeFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return remove(0);
    }

    @Override
    public E removeLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return remove(size() - 1);
    }

    @Override
    public void clear() {
        while (!isEmpty()) {
            remove(size() - 1);
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            add(e);
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

    private boolean removeWhere(Collection<?> c, boolean removeMatched) {
        boolean changed = false;
        for (int i = size() - 1; i >= 0; i--) {
            if (c.contains(get(i)) == removeMatched) {
                remove(i);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        boolean changed = false;
        for (int i = size() - 1; i >= 0; i--) {
            if (filter.test(get(i))) {
                remove(i);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void forEach(java.util.function.Consumer<? super E> action) {
        int n = size();
        for (int i = 0; i < n; i++) {
            action.accept(get(i));
        }
    }

    @Override
    public Object[] toArray() {
        int n = size();
        Object[] out = new Object[n];
        for (int i = 0; i < n; i++) {
            out[i] = get(i);
        }
        return out;
    }

    @Override
    public Iterator<E> iterator() {
        return new ViewItr();
    }

    @Override
    public java.util.stream.Stream<E> stream() {
        // A fresh snapshot list in view order, streamed — the same bounded by-index copy the other
        // model collections take for their stream().
        ArrayList<E> snapshot = new ArrayList<>();
        int n = size();
        for (int i = 0; i < n; i++) {
            snapshot.add(get(i));
        }
        return new java.util.stream.ListStream<>(snapshot);
    }

    private final class ViewItr implements Iterator<E> {
        private int cursor;

        @Override
        public boolean hasNext() {
            return cursor < size();
        }

        @Override
        public E next() {
            if (cursor >= size()) {
                throw new NoSuchElementException();
            }
            return get(cursor++);
        }
    }
}
