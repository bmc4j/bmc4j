package kotlin.collections;

import java.util.Collection;
import java.util.Iterator;
import kotlin.jvm.internal.markers.KMappedMarker;

/**
 * BMC model of {@link kotlin.collections.AbstractCollection} — the skeletal read-only {@code Collection}
 * base the Kotlin stdlib (and libraries like {@code kotlinx.collections.immutable}) extend. The single
 * size primitive is {@link #getSize()} (the Kotlin {@code val size} property accessor); a subclass
 * overrides it, and the JVM-level {@code size()} bridge is a {@code final} delegate to it. The DERIVED
 * surface ({@code isEmpty}/{@code contains}) is modeled here over {@code getSize}/{@code iterator}, so a
 * concrete immutable collection held through the {@code Collection}/{@code Set} interface DEVIRTUALIZES
 * to one sound body instead of an opaque nondet stub (which would demote a {@code size}/{@code isEmpty}
 * proof to a false UNKNOWN).
 *
 * <p>The primitives ({@link #getSize()}, {@link #iterator()}) stay ABSTRACT so JBMC resolves them to the
 * concrete subclass override (e.g. {@code PersistentOrderedSet.getSize()}); only the derived bodies are
 * implemented. This mirrors the {@code java.util.AbstractCollection} model, in Kotlin's {@code getSize}
 * shape.
 */
public abstract class AbstractCollection<E> implements Collection<E>, KMappedMarker {

    protected AbstractCollection() {
    }

    /** The Kotlin {@code val size} primitive — abstract so it resolves to the subclass override. */
    public abstract int getSize();

    /**
     * The JVM {@code Collection.size()} bridge: a delegate to {@link #getSize()}. NOT {@code final} (the
     * real stdlib marks it {@code final}) so the {@code Set} subtype {@link AbstractSet} can RE-DECLARE
     * this bridge one hop above a concrete set — which is what lets JBMC devirtualize a {@code Set.size()}
     * interface call to the concrete {@code getSize()} body. The {@code final}-ness is irrelevant to
     * analysis soundness.
     */
    @Override
    public int size() {
        return getSize();
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public abstract Iterator<E> iterator();

    @Override
    public boolean contains(Object element) {
        Iterator<E> it = iterator();
        while (it.hasNext()) {
            E e = it.next();
            if (element == null ? e == null : element.equals(e)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        Iterator<?> it = elements.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }
}
