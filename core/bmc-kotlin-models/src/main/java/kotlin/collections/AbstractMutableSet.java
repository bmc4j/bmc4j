package kotlin.collections;

import java.util.AbstractSet;
import kotlin.jvm.internal.markers.KMutableSet;

/**
 * BMC model of {@link kotlin.collections.AbstractMutableSet} — the skeletal mutable {@code Set} base,
 * extending the {@code java.util.AbstractSet} model. Mutable-set builders in
 * {@code kotlinx.collections.immutable} extend this hierarchy; modeling it keeps them non-opaque on the
 * analysis classpath. Size primitive is the Kotlin {@code getSize()} accessor (abstract); {@code size()}
 * is a {@code final} delegate. The membership surface is inherited from the {@code java.util.AbstractSet}
 * / {@code java.util.AbstractCollection} models.
 */
public abstract class AbstractMutableSet<E> extends AbstractSet<E> implements KMutableSet {

    protected AbstractMutableSet() {
    }

    /** The Kotlin {@code val size} primitive — abstract so it resolves to the subclass override. */
    public abstract int getSize();

    @Override
    public final int size() {
        return getSize();
    }
}
