package java.util;

/**
 * Minimal BMC model of {@link java.util.AbstractSet} — the skeletal {@link Set} base a user set
 * extends, overriding the abstract primitives {@link #iterator()} and {@link #size()} (inherited from
 * {@link AbstractCollection}). The DERIVED surface ({@code isEmpty}/{@code contains}/{@code toArray})
 * is supplied by {@link AbstractCollection}, so a user {@code class MySet extends AbstractSet<…>} held
 * through {@link Set} (or {@link Collection}) DEVIRTUALIZES to one sound body rather than leaving the
 * interface method an opaque nondet stub (a false refutation on a havoc artifact).
 *
 * <p>The membership/iteration surface a proof reaches is the inherited skeleton over
 * {@code iterator}/{@code size}; this skeleton adds no own bodies (the JDK's set-specific
 * {@code equals}/{@code hashCode}/{@code removeAll} are not needed for the devirt path). The concrete
 * {@link HashSet}/{@link LinkedHashSet} models implement
 * {@link Set} directly (array-backed) and do NOT extend this skeleton, so a {@code HashSet} instance
 * still resolves to its own model; this skeleton is only what a USER subclass devirtualizes through.
 */
public abstract class AbstractSet<E> extends AbstractCollection<E> implements Set<E> {

    protected AbstractSet() {
    }

}
