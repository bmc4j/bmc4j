package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

import java.util.Iterator;
import java.util.LinkedHashSet;
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

    // ---- plus(set, element) / plus(set, elements[]) / plus(set, iterable): a NEW set = receiver ∪
    //   SetsKt.plus:(Ljava/util/Set;Ljava/lang/Object;)Ljava/util/Set;
    //   SetsKt.plus:(Ljava/util/Set;[Ljava/lang/Object;)Ljava/util/Set;
    //   SetsKt.plus:(Ljava/util/Set;Ljava/lang/Iterable;)Ljava/util/Set;
    // added element(s), first-occurrence order preserved (LinkedHashSet, dedup via equals); receiver
    // untouched. (The real chain routes through internal builders JBMC nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> plus(Set<T> source, T element) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        out.add(element);
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> plus(Set<T> source, T[] elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (T e : elements) {
            out.add(e);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> plus(Set<T> source, Iterable<? extends T> elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = elements.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- minus(set, element) / minus(set, elements[]) / minus(set, iterable): a NEW set with the
    //   SetsKt.minus:(Ljava/util/Set;Ljava/lang/Object;)Ljava/util/Set;
    //   SetsKt.minus:(Ljava/util/Set;[Ljava/lang/Object;)Ljava/util/Set;
    //   SetsKt.minus:(Ljava/util/Set;Ljava/lang/Iterable;)Ljava/util/Set;
    // removed element(s) excluded, order preserved; receiver untouched. (Real chain REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> minus(Set<T> source, T element) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        out.remove(element);
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> minus(Set<T> source, T[] elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (T e : elements) {
            out.remove(e);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> minus(Set<T> source, Iterable<? extends T> elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = elements.iterator(); it.hasNext(); ) {
            out.remove(it.next());
        }
        return out;
    }
}
