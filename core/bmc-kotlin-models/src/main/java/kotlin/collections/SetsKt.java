package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

import java.util.HashSet;
import java.util.Set;

/**
 * Clean model of Kotlin's {@code SetsKt} facade for the set factories ({@code setOf}/{@code
 * mutableSetOf}/{@code emptySet}), returning bmc4j's bounded {@code HashSet} model directly instead
 * of routing through kotlin-stdlib internals JBMC stubs. Other members remain JBMC stubs.
 */
@BmcModelTail(reason = "exotic SetsKt facade remainder — kotlin-stdlib's set-builder / set-operation "
        + "extensions the bounded proofs do not exercise; loud under JBMC if reached")
public final class SetsKt {

    private SetsKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> emptySet() {
        return new HashSet<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> setOf() {
        return new HashSet<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> setOf(T element) {
        HashSet<T> s = new HashSet<>();
        s.add(element);
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> setOf(T[] elements) {
        HashSet<T> s = new HashSet<>();
        for (T e : elements) {
            s.add(e);
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> mutableSetOf() {
        return new HashSet<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> mutableSetOf(T[] elements) {
        HashSet<T> s = new HashSet<>();
        for (T e : elements) {
            s.add(e);
        }
        return s;
    }
}
