package java.util;

/**
 * Minimal BMC model of {@link java.util.AbstractCollection} — the skeletal {@link Collection} base a
 * user collection extends, overriding the two abstract primitives {@link #iterator()} and
 * {@link #size()}. The DERIVED operations ({@code isEmpty}/{@code contains}/{@code toArray}) are
 * modeled here in terms of those primitives, exactly as the JDK does, so a user subclass held through
 * the {@link Collection} (or {@link List}/{@link Set}) interface DEVIRTUALIZES to one sound body
 * instead of leaving the interface method an opaque nondet stub (which would otherwise produce a false
 * refutation on a havoc artifact).
 *
 * <p>The primitives stay ABSTRACT so JBMC resolves them to the user's override; only the derived
 * surface is implemented. Modeling them here is what makes {@code extends AbstractCollection}
 * provable without the user discovering and working around the missing devirtualization.
 *
 * <p>Traversal-based derived ops ({@code contains}/{@code toArray}) walk the user's {@link #iterator()}.
 * Bounded by the proof's {@code unwind}; concrete collections that can offer a cheaper by-index surface
 * (e.g. {@link AbstractList}) override these with index/size loops, which devirtualize more robustly.
 */
public abstract class AbstractCollection<E> implements Collection<E> {

    protected AbstractCollection() {
    }

    @Override
    public abstract Iterator<E> iterator();

    @Override
    public abstract int size();

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        Iterator<E> it = iterator();
        while (it.hasNext()) {
            E e = it.next();
            if (o == null ? e == null : o.equals(e)) {
                return true;
            }
        }
        return false;
    }

    /** A new {@code Object[]} holding the elements, in iteration order (over the user's {@link #iterator()}). */
    public Object[] toArray() {
        Object[] out = new Object[size()];
        Iterator<E> it = iterator();
        int i = 0;
        while (it.hasNext() && i < out.length) {
            out[i++] = it.next();
        }
        return out;
    }

    /** Default {@code add} is unsupported (the JDK's skeletal base throws); a mutable subclass overrides it. */
    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    /** Default {@code remove} is unsupported on the skeletal base (the bmc4j {@link Iterator} model has no
     *  {@code remove}); a mutable subclass overrides this with its own backing-store deletion. */
    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    /** Default {@code clear} is unsupported on the skeletal base; a mutable subclass overrides it. */
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
