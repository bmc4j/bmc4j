package kotlin.collections;

import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;
import kotlin.jvm.internal.markers.KMappedMarker;

/**
 * BMC model of {@link kotlin.collections.AbstractList} — the skeletal read-only {@code List} base. The
 * two primitives are {@link #getSize()} (the {@code val size} accessor) and {@link #get(int)}; a concrete
 * immutable list overrides them. The DERIVED positional surface
 * ({@code isEmpty}/{@code contains}/{@code indexOf}/{@code iterator}) is modeled here over those two
 * primitives with INDEX/SIZE loops — the devirt-robust shape (a by-index walk over the concrete
 * primitives resolves where virtual iterator dispatch on an interface receiver is fragile), exactly as
 * the {@code java.util.AbstractList} model does.
 */
public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E>, KMappedMarker {

    protected AbstractList() {
    }

    /** The positional primitive — abstract so it resolves to the subclass override. */
    @Override
    public abstract E get(int index);

    @Override
    public boolean contains(Object element) {
        return indexOf(element) >= 0;
    }

    @Override
    public int indexOf(Object element) {
        int n = getSize();
        for (int i = 0; i < n; i++) {
            E e = get(i);
            if (element == null ? e == null : element.equals(e)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object element) {
        for (int i = getSize() - 1; i >= 0; i--) {
            E e = get(i);
            if (element == null ? e == null : element.equals(e)) {
                return i;
            }
        }
        return -1;
    }

    /** A sound by-index cursor over {@code get(i)}/{@code getSize()} (no virtual iterator dispatch). */
    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    private final class Itr implements Iterator<E> {
        private int cursor;

        @Override
        public boolean hasNext() {
            return cursor < getSize();
        }

        @Override
        public E next() {
            if (cursor >= getSize()) {
                throw new NoSuchElementException();
            }
            return get(cursor++);
        }
    }
}
