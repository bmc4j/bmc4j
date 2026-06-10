package kotlin.collections;

import java.util.Set;
import kotlin.jvm.internal.markers.KMappedMarker;

/**
 * BMC model of {@link kotlin.collections.AbstractSet} — the skeletal read-only {@code Set} base. The
 * size/membership surface ({@code getSize}/{@code iterator}/{@code contains}) is inherited from
 * {@link AbstractCollection}. A concrete immutable set ({@code kotlinx.collections.immutable}'s
 * {@code PersistentOrderedSet} / {@code PersistentHashSet}) extends this and overrides the {@code getSize}
 * /{@code iterator} primitives, so a {@code size}/{@code isEmpty} proof held through the {@code Set}
 * interface devirtualizes to one sound body instead of an opaque nondet stub.
 *
 * <p>The {@code final} {@code size()}/{@code isEmpty()} JVM bridges are RE-DECLARED here (delegating to
 * {@link #getSize()}) even though {@link AbstractCollection} already supplies them. A concrete set like
 * {@code PersistentOrderedSet} has no own {@code size()}/{@code isEmpty()} — the Java call
 * {@code Set.size()} must resolve to a bridge in the base. Declaring the bridge on the IMMEDIATE
 * superclass (one hop up, exactly as {@code kotlin.collections.AbstractMap} sits one hop above
 * {@code PersistentOrderedMap}) lets JBMC devirtualize the interface call to the concrete
 * {@code getSize()} body; left two hops up on {@link AbstractCollection}, the {@code Set.size()} call
 * stayed an unresolved nondet stub for {@code PersistentOrderedSet} (whose {@code getSize} delegates to a
 * backing {@code PersistentHashMap}).
 */
public abstract class AbstractSet<E> extends AbstractCollection<E> implements Set<E>, KMappedMarker {

    protected AbstractSet() {
    }

    @Override
    public int size() {
        return getSize();
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }
}
