package kotlin.collections;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.ListSequence;
import kotlin.sequences.Sequence;
import org.cprover.CProver;

/**
 * Clean model of Kotlin's {@code SetsKt} facade for the set factories ({@code setOf}/{@code
 * mutableSetOf}/{@code emptySet}), returning bmc4j's bounded {@code HashSet} model directly instead
 * of routing through kotlin-stdlib internals JBMC stubs. The whole {@code SetsKt} surface is now
 * accounted for PER MEMBER (no class-level {@code @BmcModelTail} catch-all): the bounded set ops are
 * modeled with real delegating bodies, and the genuine walls (a {@code TreeSet} return — bmc4j has no
 * bounded {@code TreeSet} model — and the internal stdlib read-only optimizer) carry a per-member
 * loud {@code @BmcUnmodelable}.
 */
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

    // ---- plus(set, sequence) / minus(set, sequence): the Sequence-arg twins of the element/array/
    //   SetsKt.plus:(Ljava/util/Set;Lkotlin/sequences/Sequence;)Ljava/util/Set;
    //   SetsKt.minus:(Ljava/util/Set;Lkotlin/sequences/Sequence;)Ljava/util/Set;
    // iterable overloads above. A NEW set = receiver ∪ / \ the sequence's elements, first-occurrence
    // order preserved (LinkedHashSet, dedup via equals); receiver untouched. The Sequence is drained via
    // its CONCRETE backing ArrayList iterator (seqIter) — never the virtual Sequence.iterator() — exactly
    // as the SequencesKt facade does, so the dispatch devirtualizes robustly under JBMC.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> plus(Set<T> source, Sequence<? extends T> elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> minus(Set<T> source, Sequence<? extends T> elements) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            out.remove(it.next());
        }
        return out;
    }

    /**
     * Drain a model {@link Sequence} via its concrete backing {@link ArrayList}'s iterator rather than
     * the virtual {@code Sequence.iterator()} — {@link ListSequence} is the sole {@code final}
     * implementor, so the {@code checkcast} is sound and {@code ArrayList.iterator()} is a concrete-typed
     * call JBMC resolves where the interface dispatch on the {@code Sequence}-typed parameter is
     * kotlinc-version-fragile. Mirrors {@code SequencesKt}'s {@code seqIter}/{@code backing} pattern.
     */
    @SuppressWarnings("unchecked")
    private static <T> Iterator<T> seqIter(Sequence<? extends T> source) {
        CProver.assume(source instanceof ListSequence);
        return (Iterator<T>) ((ListSequence<? extends T>) source).backingList().iterator();
    }

    // ---- sortedSetOf / optimizeReadOnlySet: the genuine walls. ----------------------------------------
    //   SetsKt.sortedSetOf:([Ljava/lang/Object;)Ljava/util/TreeSet;
    //   SetsKt.sortedSetOf:(Ljava/util/Comparator;[Ljava/lang/Object;)Ljava/util/TreeSet;
    // sortedSetOf returns a java.util.TreeSet, for which bmc4j has NO bounded model (only TreeMap is
    // modeled, and TreeMap is natural-ordering only). Modeling it as a HashSet would silently drop the
    // sorted-navigation observable; the comparator form additionally needs a comparator-ordered backing
    // that bmc4j's tree models do not have. Both stay loud until a bounded TreeSet model lands.
    @BmcUnmodelable(reason = "returns a java.util.TreeSet — bmc4j has no bounded TreeSet model (only "
            + "natural-ordering TreeMap); a HashSet substitute would silently drop the sorted-navigation "
            + "observable; loud UNKNOWN under JBMC until a TreeSet model lands")
    public static java.util.TreeSet sortedSetOf(java.lang.Object[] a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.SetsKt.sortedSetOf(java.lang.Object[]) — returns a java.util.TreeSet; bmc4j has no bounded TreeSet model");
    }

    @BmcUnmodelable(reason = "returns a comparator-ordered java.util.TreeSet — bmc4j has no bounded TreeSet "
            + "model, and the tree models are natural-ordering only (no comparator backing); loud UNKNOWN "
            + "under JBMC until a comparator-ordered TreeSet model lands")
    public static java.util.TreeSet sortedSetOf(java.util.Comparator a0, java.lang.Object[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.SetsKt.sortedSetOf(java.util.Comparator,java.lang.Object[]) — comparator-ordered java.util.TreeSet; bmc4j has no bounded TreeSet model");
    }

    //   SetsKt.optimizeReadOnlySet:(Ljava/util/Set;)Ljava/util/Set;
    // An INTERNAL kotlin-stdlib size-optimizer (0→emptySet, 1→singleton, else the set itself) on the
    // read-only set-build path. Not called from idiomatic user code; the real body routes through stdlib
    // singleton-collection internals JBMC stubs. Loud-if-reached rather than a fictional passthrough.
    @BmcUnmodelable(reason = "internal kotlin-stdlib read-only-set size-optimizer (singleton/empty "
            + "specialization) on the set-build path; not reachable from idiomatic user code and its real "
            + "body routes through stdlib singleton-collection internals that JBMC stubs; loud-if-reached")
    public static java.util.Set optimizeReadOnlySet(java.util.Set a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.SetsKt.optimizeReadOnlySet(java.util.Set) — internal kotlin-stdlib read-only-set size-optimizer");
    }
}
