package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import kotlin.jvm.functions.Function1;

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

    // ---- buildSet { } : the read-only set builder.
    //   SetsKt.buildSet:(Lkotlin/jvm/functions/Function1;)Ljava/util/Set;
    //   SetsKt.buildSet:(ILkotlin/jvm/functions/Function1;)Ljava/util/Set;                (capacity hint)
    // buildSet is INLINE, so a Kotlin call site inlines its body: createSetBuilder() (a fresh builder),
    // the user builder action, then build(set) to seal it read-only — so the INLINE path actually reaches
    // createSetBuilder/build (modeled below), not buildSet. This buildSet facade JVM method is the NON-
    // inline / Java reach: allocate the bounded HashSet model, run the concrete (devirtualized) builder
    // lambda on it, return it. Backs onto the bounded HashSet model — matching the established setOf/
    // mutableSetOf factories (the real builder is insertion-ordered, but those factories already model as
    // HashSet, so this stays consistent). Capacity hint ignored (fixed bounded backing).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> Set<E> buildSet(Function1<? super Set<E>, kotlin.Unit> builderAction) {
        HashSet<E> builder = new HashSet<>();
        builderAction.invoke(builder);
        return build(builder);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> Set<E> buildSet(int capacity, Function1<? super Set<E>, kotlin.Unit> builderAction) {
        HashSet<E> builder = new HashSet<>();
        builderAction.invoke(builder);
        return build(builder);
    }

    // ---- createSetBuilder() / createSetBuilder(int) / build(Set): the INLINE buildSet { } body's
    //   SetsKt.createSetBuilder:()Ljava/util/Set;
    //   SetsKt.createSetBuilder:(I)Ljava/util/Set;
    //   SetsKt.build:(Ljava/util/Set;)Ljava/util/Set;
    // building blocks. createSetBuilder returns a bounded HashSet builder; build returns it unchanged (the
    // real seal-to-read-only is the READ observable only — post-seal write rejection NOT modeled, matching
    // the read-observable precedent). createSetBuilder is what the inlined `buildSet { … }` call site
    // reaches; without it the path nondet-stubs (silently unsound). Capacity hint ignored.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> Set<E> createSetBuilder() {
        return new HashSet<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> Set<E> createSetBuilder(int capacity) {
        return new HashSet<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> Set<E> build(Set<E> builder) {
        return builder;
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

    // ---- setOfNotNull(element) / setOfNotNull(elements[]):
    //   SetsKt.setOfNotNull:(Ljava/lang/Object;)Ljava/util/Set;
    //   SetsKt.setOfNotNull:([Ljava/lang/Object;)Ljava/util/Set;
    // Kotlin contract: a NEW set of the given non-null element(s) (nulls filtered out), first-occurrence
    // order preserved (LinkedHashSet, dedup via equals). The single-arg form yields an empty set for a
    // null element. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> setOfNotNull(T element) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        if (element != null) {
            out.add(element);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> setOfNotNull(T[] elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (T e : elements) {
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- hashSetOf(elements[]) / linkedSetOf(elements[]):
    //   SetsKt.hashSetOf:([Ljava/lang/Object;)Ljava/util/HashSet;
    //   SetsKt.linkedSetOf:([Ljava/lang/Object;)Ljava/util/LinkedHashSet;
    // a NEW HashSet / LinkedHashSet of the given elements (dedup via equals; LinkedHashSet preserves
    // first-occurrence order). Both back onto bmc4j's bounded models. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> HashSet<T> hashSetOf(T[] elements) {
        HashSet<T> out = new HashSet<>();
        for (T e : elements) {
            out.add(e);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> LinkedHashSet<T> linkedSetOf(T[] elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (T e : elements) {
            out.add(e);
        }
        return out;
    }

    // NOTE: sortedSetOf / toSortedSet stay in the @BmcModelTail residue — they return a java.util.TreeSet,
    // for which bmc4j has no bounded model (only TreeMap is modeled). Out of scope until a TreeSet model
    // lands. optimizeReadOnlySet is an internal stdlib size-optimizer, also left loud. Loud under JBMC.
}
