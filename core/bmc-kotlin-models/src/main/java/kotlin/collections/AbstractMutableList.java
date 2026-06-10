package kotlin.collections;

import java.util.AbstractList;
import kotlin.jvm.internal.markers.KMutableList;

/**
 * BMC model of {@link kotlin.collections.AbstractMutableList} — the skeletal mutable {@code List} base,
 * extending the {@code java.util.AbstractList} model (which declares the inherited {@code modCount}
 * field). {@code kotlinx.collections.immutable}'s {@code PersistentVectorBuilder} extends THIS class and
 * reads {@code modCount} via a generated {@code getModCount$kotlinx_collections_immutable} accessor; when
 * this base was unmodeled (opaque) and {@code java.util.AbstractList} lacked the field, JBMC's
 * {@code infer_opaque_type_fields} invariant failed and crashed (ENGINE_CRASH UNKNOWN) on any proof that
 * transitively loaded the builder — including a {@code persistentSetOf().size} proof. Modeling this base
 * (and the {@code modCount} field on {@code java.util.AbstractList}) removes the crash.
 *
 * <p>Size primitive is the Kotlin {@code getSize()} accessor (abstract); {@code size()} is a {@code final}
 * delegate. The derived positional surface is inherited from the {@code java.util.AbstractList} model.
 */
public abstract class AbstractMutableList<E> extends AbstractList<E> implements KMutableList {

    protected AbstractMutableList() {
    }

    /** The Kotlin {@code val size} primitive — abstract so it resolves to the subclass override. */
    public abstract int getSize();

    @Override
    public final int size() {
        return getSize();
    }
}
