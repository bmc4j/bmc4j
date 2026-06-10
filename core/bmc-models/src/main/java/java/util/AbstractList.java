package java.util;

/**
 * Minimal BMC model of {@link java.util.AbstractList} — the skeletal {@link List} base a user list
 * extends, overriding the abstract primitives {@link #get(int)} and {@link #size()}. The DERIVED
 * positional surface ({@code isEmpty}/{@code contains}/{@code indexOf}/{@code lastIndexOf}/iteration)
 * is modeled here over those two primitives, so a user {@code class MyList extends AbstractList<…>}
 * held through {@link List} (or {@link Collection}) DEVIRTUALIZES to one sound body instead of the
 * interface method becoming an opaque nondet stub (a false refutation on a havoc artifact).
 *
 * <p>The primitives stay ABSTRACT so JBMC resolves them to the user's override. The derived bodies are
 * deliberately INDEX/SIZE based ({@code get(i)} over {@code 0..size()}) rather than iterator-based:
 * a by-index loop over the concrete primitives devirtualizes robustly, whereas virtual iterator
 * dispatch on an interface-typed receiver is fragile under JBMC. {@link #iterator()} is itself a sound
 * by-index cursor over {@code get}/{@code size}, so even {@code for-each} over the user list resolves.
 *
 * <p>Bounded by the proof's {@code unwind} (the lookup loops walk to {@code size()}). The concrete
 * {@link ArrayList}/{@link LinkedList} models do NOT extend this skeleton — they implement {@link List}
 * directly with their own array-backed bodies — so an {@code ArrayList} instance still resolves to its
 * own (faster, fuller) model; this skeleton is only what a USER subclass devirtualizes through.
 */
public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {

    /**
     * The JDK's structural-modification counter. Modeled (as the JDK declares it here) so a subclass
     * that reads it through this base resolves the field instead of leaving JBMC to infer it on an
     * opaque type — e.g. {@code kotlin.collections.AbstractMutableList}'s generated {@code getModCount}
     * accessor, reached transitively when proving over a kotlinx persistent collection, references this
     * inherited field; without it JBMC's {@code infer_opaque_type_fields} invariant fails and crashes.
     */
    protected transient int modCount = 0;

    protected AbstractList() {
    }

    @Override
    public abstract E get(int index);

    @Override
    public abstract int size();

    @Override
    public boolean isEmpty() {
        return size() == 0;
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
    public Object[] toArray() {
        int n = size();
        Object[] out = new Object[n];
        for (int i = 0; i < n; i++) {
            out[i] = get(i);
        }
        return out;
    }

    /** A sound by-index cursor over {@code get(i)}/{@code size()} (no virtual iterator dispatch). */
    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    // --- mutators: unsupported on the skeletal base (the JDK throws); a mutable subclass overrides ---

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }

    private final class Itr implements Iterator<E> {
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
