package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;

import java.util.HashSet;
import java.util.Set;

/**
 * Clean model of Kotlin's {@code SetsKt} facade for the set factories ({@code setOf}/{@code
 * mutableSetOf}/{@code emptySet}), returning bmc4j's bounded {@code HashSet} model directly instead
 * of routing through kotlin-stdlib internals JBMC stubs. Other members remain JBMC stubs.
 */
@BmcModelConforms("Kotlin stdlib model — @BmcProof (model-conformance-proofs); facade/value model, audited at class level")
public final class SetsKt {

    private SetsKt() {
    }

    public static <T> Set<T> emptySet() {
        return new HashSet<>();
    }

    public static <T> Set<T> setOf() {
        return new HashSet<>();
    }

    public static <T> Set<T> setOf(T element) {
        HashSet<T> s = new HashSet<>();
        s.add(element);
        return s;
    }

    public static <T> Set<T> setOf(T[] elements) {
        HashSet<T> s = new HashSet<>();
        for (T e : elements) {
            s.add(e);
        }
        return s;
    }

    public static <T> Set<T> mutableSetOf() {
        return new HashSet<>();
    }

    public static <T> Set<T> mutableSetOf(T[] elements) {
        HashSet<T> s = new HashSet<>();
        for (T e : elements) {
            s.add(e);
        }
        return s;
    }
}
