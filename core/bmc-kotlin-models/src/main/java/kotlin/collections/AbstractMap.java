package kotlin.collections;

import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import kotlin.jvm.internal.markers.KMappedMarker;

/**
 * BMC model of {@link kotlin.collections.AbstractMap} — the skeletal read-only {@code Map} base the
 * Kotlin stdlib (and {@code kotlinx.collections.immutable}'s {@code PersistentOrderedMap} /
 * {@code PersistentHashMap}) extend. The size primitive is {@link #getSize()} (the {@code val size}
 * accessor); a concrete immutable map overrides it, and {@code size()} is a {@code final} delegate. The
 * DERIVED surface ({@code isEmpty}, {@code containsKey}) is modeled over {@code getSize}/{@code getEntries}
 * so a {@code size}/{@code isEmpty} proof held through the {@code Map} interface devirtualizes to one
 * sound body instead of an opaque nondet stub.
 *
 * <p>{@link #getEntries()} is the abstract entry-set primitive (like the JDK's {@code entrySet}); the
 * concrete map overrides it. Mirrors the {@code java.util.AbstractMap} model, in Kotlin's {@code getSize}
 * /{@code getEntries} shape.
 */
public abstract class AbstractMap<K, V> implements Map<K, V>, KMappedMarker {

    protected AbstractMap() {
    }

    /** The abstract entry-set primitive — resolves to the subclass override. */
    public abstract Set<Map.Entry<K, V>> getEntries();

    /** The Kotlin {@code val size} primitive: the entry-set's size (concrete in the real class). */
    public int getSize() {
        return getEntries().size();
    }

    @Override
    public final int size() {
        return getSize();
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        Iterator<Map.Entry<K, V>> it = getEntries().iterator();
        while (it.hasNext()) {
            K k = it.next().getKey();
            if (key == null ? k == null : key.equals(k)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return getEntries();
    }
}
