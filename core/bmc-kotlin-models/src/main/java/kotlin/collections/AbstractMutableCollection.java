package kotlin.collections;

import java.util.AbstractCollection;
import kotlin.jvm.internal.markers.KMutableCollection;

/**
 * BMC model of {@link kotlin.collections.AbstractMutableCollection} — the skeletal mutable {@code Collection}
 * base, extending the {@code java.util.AbstractCollection} model. Its size primitive is the Kotlin
 * {@code getSize()} accessor (abstract, resolved to the subclass override); {@code size()} is a
 * {@code final} delegate to it. The derived iteration/membership surface is inherited from the
 * {@code java.util.AbstractCollection} model. Builders for {@code kotlinx.collections.immutable} extend
 * this hierarchy, so modeling it keeps their classes from being opaque on the analysis classpath.
 */
public abstract class AbstractMutableCollection<E> extends AbstractCollection<E> implements KMutableCollection {

    protected AbstractMutableCollection() {
    }

    /** The Kotlin {@code val size} primitive — abstract so it resolves to the subclass override. */
    public abstract int getSize();

    @Override
    public final int size() {
        return getSize();
    }
}
