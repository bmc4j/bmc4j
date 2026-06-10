package kotlin.collections;

import java.util.AbstractMap;
import kotlin.jvm.internal.markers.KMutableMap;

/**
 * BMC model of {@link kotlin.collections.AbstractMutableMap} — the skeletal mutable {@code Map} base,
 * extending the {@code java.util.AbstractMap} model. Mutable-map builders in
 * {@code kotlinx.collections.immutable} extend this hierarchy; modeling it keeps them non-opaque on the
 * analysis classpath. Size primitive is the Kotlin {@code getSize()} accessor (concrete in the real
 * class, delegating to the entry set); {@code size()} is a {@code final} delegate. The derived surface is
 * inherited from the {@code java.util.AbstractMap} model.
 */
public abstract class AbstractMutableMap<K, V> extends AbstractMap<K, V> implements KMutableMap {

    protected AbstractMutableMap() {
    }

    /** The Kotlin {@code val size} primitive: the entry-set's size. */
    public int getSize() {
        return entrySet().size();
    }

    @Override
    public final int size() {
        return getSize();
    }
}
