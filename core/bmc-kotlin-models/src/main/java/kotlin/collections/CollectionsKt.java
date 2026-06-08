package kotlin.collections;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;
import org.cprover.CProver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;
import kotlin.ranges.IntRange;
import kotlin.sequences.ListSequence;
import kotlin.sequences.Sequence;

/**
 * Clean model of Kotlin's {@code CollectionsKt} facade for the immutable list factories. Kotlin's
 * {@code listOf(...)} routes through this facade and kotlin-stdlib internals that JBMC stubs, so the
 * real chain never reaches a modeled list. This replacement returns bmc4j's bounded {@code
 * ArrayList} model directly. Only the {@code listOf}/{@code emptyList} members are provided; other
 * {@code CollectionsKt} members remain JBMC stubs (as they already were).
 *
 * <p>The modeled members return bmc4j's bounded {@code java.util} collection models (audited on the
 * JDK side); the per-member audit here covers only the Kotlin-visible {@code CollectionsKt} surface, so
 * those java.util members are not double-counted. The vast remainder of this multifile facade
 * (~230 stdlib extension functions: aggregation, windowing, grouping, set ops, etc.) is the tail.
 */
public final class CollectionsKt {

    private CollectionsKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> emptyList() {
        return new ArrayList<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> listOf() {
        return new ArrayList<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> listOf(T element) {
        ArrayList<T> l = new ArrayList<>();
        l.add(element);
        return l;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> listOf(T[] elements) {
        ArrayList<T> l = new ArrayList<>();
        for (T e : elements) {
            l.add(e);
        }
        return l;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> mutableListOf() {
        return new ArrayList<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> mutableListOf(T[] elements) {
        ArrayList<T> l = new ArrayList<>();
        for (T e : elements) {
            l.add(e);
        }
        return l;
    }

    // ---- buildList { } : the read-only list builder.
    //   CollectionsKt.buildList:(Lkotlin/jvm/functions/Function1;)Ljava/util/List;
    //   CollectionsKt.buildList:(ILkotlin/jvm/functions/Function1;)Ljava/util/List;       (capacity hint)
    // buildList is an INLINE stdlib function, so from a Kotlin call site its body lands in the caller:
    // the inlined body calls createListBuilder() (a fresh ArrayList builder), invokes the user builder
    // action on it, then build(list) to seal it read-only — so the methods the INLINE path actually
    // reaches are createListBuilder/build (modeled below), not buildList. This buildList facade JVM
    // method exists for the NON-inline / Java reach (and for completeness); it mirrors the inlined shape
    // exactly: allocate the bounded ArrayList model, run the concrete (devirtualized) builder lambda on
    // it, return it as the read-only list. The capacity hint is ignored (the bounded ArrayList backing is
    // fixed-size) — sound, matching the mapCapacity precedent.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> List<E> buildList(Function1<? super List<E>, kotlin.Unit> builderAction) {
        ArrayList<E> builder = new ArrayList<>();
        builderAction.invoke(builder);
        return build(builder);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> List<E> buildList(int capacity, Function1<? super List<E>, kotlin.Unit> builderAction) {
        ArrayList<E> builder = new ArrayList<>();
        builderAction.invoke(builder);
        return build(builder);
    }

    // ---- createListBuilder() / createListBuilder(int) / build(List): the INLINE buildList { } body's
    //   CollectionsKt.createListBuilder:()Ljava/util/List;
    //   CollectionsKt.createListBuilder:(I)Ljava/util/List;
    //   CollectionsKt.build:(Ljava/util/List;)Ljava/util/List;
    // building blocks. The stdlib createListBuilder returns a SerializedCollection-backed mutable builder
    // and build seals it into a truly read-only list; bmc4j models the READ observable only — a bounded
    // ArrayList builder, and build returns it unchanged (the post-seal write-rejection of the real list is
    // NOT modeled, matching the asReversed/reversed read-observable precedent). createListBuilder is what
    // the inlined `buildList { … }` call site actually reaches; without these it nondet-stubs (silently
    // unsound). The capacity hint is ignored (fixed-size bounded backing).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> List<E> createListBuilder() {
        return new ArrayList<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> List<E> createListBuilder(int capacity) {
        return new ArrayList<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E> List<E> build(List<E> builder) {
        return builder;
    }

    // Helpers the inlined collection extensions (map/filter/fold/forEach/…) call, plus the
    // non-inline facade methods (sumOfInt/first/last). With these + java.lang.Iterable modeled,
    // `list.map { }.filter { }.sum()` etc. analyse over the bounded collection models.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int collectionSizeOrDefault(Iterable<?> iterable, int defaultValue) {
        return defaultValue; // only used to pre-size the destination; our ArrayList ignores capacity
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfInt(Iterable<Integer> source) {
        int sum = 0;
        Iterator<Integer> it = source.iterator();
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long sumOfLong(Iterable<Long> source) {
        long sum = 0;
        Iterator<Long> it = source.iterator();
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double sumOfDouble(Iterable<Double> source) {
        double sum = 0;
        Iterator<Double> it = source.iterator();
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T first(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T first(Iterable<T> source) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        return it.next();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T last(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(list.size() - 1);
    }

    // maxOrNull / minOrNull. The Kotlin compiler emits these for a List<T : Comparable<T>> as
    //   CollectionsKt.maxOrNull:(Ljava/lang/Iterable;)Ljava/lang/Comparable;
    //   CollectionsKt.minOrNull:(Ljava/lang/Iterable;)Ljava/lang/Comparable;
    // (the generic Iterable overload; the erased return is Comparable and the erased param Iterable).
    // The real stdlib chain reaches kotlin-stdlib internals JBMC stubs, so we model them directly by
    // iterating the bounded collection model and folding with compareTo. Returns null on an empty
    // source, matching Kotlin's *OrNull contract.

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Comparable maxOrNull(Iterable source) {
        Iterator it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        Comparable max = (Comparable) it.next();
        while (it.hasNext()) {
            Comparable e = (Comparable) it.next();
            if (max.compareTo(e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Comparable minOrNull(Iterable source) {
        Iterator it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        Comparable min = (Comparable) it.next();
        while (it.hasNext()) {
            Comparable e = (Comparable) it.next();
            if (min.compareTo(e) > 0) {
                min = e;
            }
        }
        return min;
    }

    // asSequence: the Kotlin compiler emits CollectionsKt.asSequence:(Ljava/lang/Iterable;)
    // Lkotlin/sequences/Sequence; for `iterable.asSequence()`. The real chain reaches kotlin-stdlib
    // internals JBMC stubs; we wrap the (bounded) source in bmc4j's eager ListSequence so the
    // downstream SequencesKt ops (map/filter/toList/sum/count) analyse over the bounded model.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> asSequence(Iterable<T> source) {
        return new ListSequence<>(source);
    }

    // ---- zip: the Kotlin compiler emits
    //   CollectionsKt.zip:(Ljava/lang/Iterable;Ljava/lang/Iterable;)Ljava/util/List;
    // for `a.zip(b)` (no transform). The real chain reaches kotlin-stdlib internals JBMC stubs; we
    // build the bounded ArrayList of Pairs directly, truncated to the shorter input, matching the
    // Kotlin contract.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> List<Pair<T, R>> zip(Iterable<T> source, Iterable<R> other) {
        ArrayList<Pair<T, R>> result = new ArrayList<>();
        Iterator<T> a = source.iterator();
        Iterator<R> b = other.iterator();
        while (a.hasNext() && b.hasNext()) {
            result.add(new Pair<>(a.next(), b.next()));
        }
        return result;
    }

    // ---- sorted / sortedWith: the Kotlin compiler emits
    //   CollectionsKt.sorted:(Ljava/lang/Iterable;)Ljava/util/List;            for `xs.sorted()`
    //   CollectionsKt.sortedWith:(Ljava/lang/Iterable;Ljava/util/Comparator;)Ljava/util/List;
    //                                                                          for `xs.sortedBy { }`
    // (the keySelector lambda is desugared by bmc4j into a real Comparator). The real chain reaches
    // kotlin-stdlib internals JBMC stubs. We copy into a fresh bounded ArrayList and insertion-sort
    // it (stable, bounded by size which stays within the proof's unwind), returning a NEW list and
    // leaving the source untouched — matching the Kotlin contract. Comparisons use the natural
    // ordering (sorted) or the supplied Comparator (sortedWith); both are sound for boxed primitives.

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> List<T> sorted(Iterable<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        insertionSort(out, null);
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> sortedWith(Iterable<T> source, Comparator<? super T> comparator) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        insertionSort(out, comparator);
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void insertionSort(ArrayList<T> a, Comparator<? super T> cmp) {
        int n = a.size();
        for (int i = 1; i < n; i++) {
            T key = a.get(i);
            int j = i - 1;
            // shift down every element that is strictly greater than key (stable: uses > 0, not >= 0)
            while (j >= 0 && compare(a.get(j), key, cmp) > 0) {
                a.set(j + 1, a.get(j));
                j--;
            }
            a.set(j + 1, key);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> int compare(T x, T y, Comparator<? super T> cmp) {
        if (cmp != null) {
            return cmp.compare(x, y);
        }
        return ((Comparable) x).compareTo(y);
    }

    // ---- take(n) / drop(n): the Kotlin compiler emits
    //   CollectionsKt.take:(Ljava/lang/Iterable;I)Ljava/util/List;   for `xs.take(n)`
    //   CollectionsKt.drop:(Ljava/lang/Iterable;I)Ljava/util/List;   for `xs.drop(n)`
    // The real chain reaches kotlin-stdlib internals JBMC stubs; we build the bounded ArrayList
    // directly. Kotlin's contract: a negative n throws IllegalArgumentException; take(n) returns the
    // first min(n, size) elements; drop(n) returns all but the first min(n, size) — both in order,
    // both NEW lists leaving the source untouched.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> take(Iterable<T> source, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested element count " + n + " is less than zero.");
        }
        ArrayList<T> out = new ArrayList<>();
        if (n == 0) {
            return out;
        }
        int taken = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            out.add(it.next());
            taken++;
            if (taken == n) {
                break;
            }
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> drop(Iterable<T> source, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested element count " + n + " is less than zero.");
        }
        ArrayList<T> out = new ArrayList<>();
        int dropped = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T v = it.next();
            if (dropped < n) {
                dropped++;
            } else {
                out.add(v);
            }
        }
        return out;
    }

    // ---- distinct(): the Kotlin compiler emits
    //   CollectionsKt.distinct:(Ljava/lang/Iterable;)Ljava/util/List;
    // Kotlin's contract: a NEW list of the distinct elements in first-occurrence order (dedup via
    // equals). We accumulate into a bounded LinkedHashSet (which dedups via equals and iterates in
    // insertion order in the model) then copy to a list — matching the order contract.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> distinct(Iterable<T> source) {
        LinkedHashSet<T> seen = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            seen.add(it.next());
        }
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seen.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- toSet(): the Kotlin compiler emits
    //   CollectionsKt.toSet:(Ljava/lang/Iterable;)Ljava/util/Set;
    // Kotlin's contract: a NEW LinkedHashSet preserving first-occurrence order, dedup via equals.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> toSet(Iterable<T> source) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- toMutableList(): the Kotlin compiler emits, for a List/Collection receiver,
    //   CollectionsKt.toMutableList:(Ljava/util/Collection;)Ljava/util/List;
    // Kotlin's contract: a NEW ArrayList copy of the source, in order (a mutable snapshot).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toMutableList(Collection<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- addAll(target, source): the Kotlin compiler emits this as the kernel of the INLINED
    //   xs.flatMap { } (and other accumulating ops):
    //   CollectionsKt.addAll:(Ljava/util/Collection;Ljava/lang/Iterable;)Z
    // The real chain reaches kotlin-stdlib internals JBMC stubs (so flatMap's accumulation was
    // silently nondet). We append every element of `source` to `target` in order, returning whether
    // the target changed — matching the Kotlin/Collections contract over the bounded models.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean addAll(Collection<T> target, Iterable<? extends T> source) {
        boolean changed = false;
        for (Iterator<? extends T> it = source.iterator(); it.hasNext(); ) {
            if (target.add(it.next())) {
                changed = true;
            }
        }
        return changed;
    }

    // ---- plus(element) / plus(collection): the Kotlin compiler emits
    //   CollectionsKt.plus:(Ljava/lang/Iterable;Ljava/lang/Object;)Ljava/util/List;       for `xs + e`
    //   CollectionsKt.plus:(Ljava/util/Collection;Ljava/lang/Object;)Ljava/util/List;      (Collection rcvr)
    //   CollectionsKt.plus:(Ljava/lang/Iterable;Ljava/lang/Iterable;)Ljava/util/List;      for `xs + ys`
    //   CollectionsKt.plus:(Ljava/util/Collection;Ljava/lang/Iterable;)Ljava/util/List;
    //   CollectionsKt.plus:(Ljava/lang/Iterable;[Ljava/lang/Object;)Ljava/util/List;       for `xs + arr`
    //   CollectionsKt.plus:(Ljava/util/Collection;[Ljava/lang/Object;)Ljava/util/List;
    // The real chain routes through internal builders JBMC nondet-stubs (probed REFUTED), so we build
    // the bounded ArrayList directly: a NEW list = receiver elements (in order) then the appended
    // element(s). The source is untouched — matching the Kotlin contract.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Iterable<T> source, T element) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        out.add(element);
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Collection<T> source, T element) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        out.add(element);
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Iterable<T> source, Iterable<? extends T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = elements.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Collection<T> source, Iterable<? extends T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = elements.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Iterable<T> source, T[] elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (T e : elements) {
            out.add(e);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Collection<T> source, T[] elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (T e : elements) {
            out.add(e);
        }
        return out;
    }

    // ---- minus(element) / minus(collection): the Kotlin compiler emits
    //   CollectionsKt.minus:(Ljava/lang/Iterable;Ljava/lang/Object;)Ljava/util/List;       for `xs - e`
    //   CollectionsKt.minus:(Ljava/lang/Iterable;Ljava/lang/Iterable;)Ljava/util/List;      for `xs - ys`
    //   CollectionsKt.minus:(Ljava/lang/Iterable;[Ljava/lang/Object;)Ljava/util/List;       for `xs - arr`
    // Kotlin contract: a NEW list of the source elements with the removed element(s) filtered out.
    // minus(single) removes only the FIRST occurrence; minus(collection/array) removes ALL elements
    // present in the removand. Source untouched. (Real chain nondet-stubs builders — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> minus(Iterable<T> source, T element) {
        ArrayList<T> out = new ArrayList<>();
        boolean removed = false;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (!removed && (e == null ? element == null : e.equals(element))) {
                removed = true;
            } else {
                out.add(e);
            }
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> minus(Iterable<T> source, Iterable<? extends T> elements) {
        ArrayList<T> remove = new ArrayList<>();
        for (Iterator<? extends T> it = elements.iterator(); it.hasNext(); ) {
            remove.add(it.next());
        }
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (!remove.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> minus(Iterable<T> source, T[] elements) {
        ArrayList<T> remove = new ArrayList<>();
        for (T e : elements) {
            remove.add(e);
        }
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (!remove.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- single() / singleOrNull(): the Kotlin compiler emits
    //   CollectionsKt.single:(Ljava/lang/Iterable;)Ljava/lang/Object;          / (Ljava/util/List;)…
    //   CollectionsKt.singleOrNull:(Ljava/lang/Iterable;)Ljava/lang/Object;     / (Ljava/util/List;)…
    // Kotlin contract: single() returns the sole element or throws (NoSuchElementException when empty,
    // IllegalArgumentException when >1); singleOrNull() returns the sole element or null (empty OR >1).
    // (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T single(Iterable<T> source) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException("Collection is empty.");
        }
        T single = it.next();
        if (it.hasNext()) {
            throw new IllegalArgumentException("Collection has more than one element.");
        }
        return single;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T single(List<T> list) {
        int n = list.size();
        if (n == 0) {
            throw new NoSuchElementException("List is empty.");
        }
        if (n != 1) {
            throw new IllegalArgumentException("List has more than one element.");
        }
        return list.get(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T singleOrNull(Iterable<T> source) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        T single = it.next();
        if (it.hasNext()) {
            return null;
        }
        return single;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T singleOrNull(List<T> list) {
        return list.size() == 1 ? list.get(0) : null;
    }

    // ---- reversed(): CollectionsKt.reversed:(Ljava/lang/Iterable;)Ljava/util/List; — a NEW list of the
    // source elements in reverse order, source untouched. (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> reversed(Iterable<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        // in-place reverse of the bounded array-backed model
        int i = 0;
        int j = out.size() - 1;
        while (i < j) {
            T tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
            i++;
            j--;
        }
        return out;
    }

    // ---- toList(Iterable): CollectionsKt.toList:(Ljava/lang/Iterable;)Ljava/util/List; — a NEW read-only
    // snapshot copy in order. (toMutableList(Collection) is already modeled above; add the Iterable
    // overload of both here.) (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toList(Iterable<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toMutableList(Iterable<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- toMutableSet(Iterable): CollectionsKt.toMutableSet:(Ljava/lang/Iterable;)Ljava/util/Set; — a
    // NEW LinkedHashSet preserving first-occurrence order (toSet is already modeled; this is the
    // mutable twin with the same observable). (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> toMutableSet(Iterable<T> source) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- union / intersect / subtract: set operations producing a NEW LinkedHashSet (first-occurrence
    //   CollectionsKt.union:(Ljava/lang/Iterable;Ljava/lang/Iterable;)Ljava/util/Set;
    //   CollectionsKt.intersect:(Ljava/lang/Iterable;Ljava/lang/Iterable;)Ljava/util/Set;
    //   CollectionsKt.subtract:(Ljava/lang/Iterable;Ljava/lang/Iterable;)Ljava/util/Set;
    // union = source ∪ other (source order then new-from-other); intersect = elements in BOTH;
    // subtract = source minus other. (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> union(Iterable<T> source, Iterable<T> other) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<T> it = other.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> intersect(Iterable<T> source, Iterable<T> other) {
        LinkedHashSet<T> otherSet = new LinkedHashSet<>();
        for (Iterator<T> it = other.iterator(); it.hasNext(); ) {
            otherSet.add(it.next());
        }
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (otherSet.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> subtract(Iterable<T> source, Iterable<T> other) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<T> it = other.iterator(); it.hasNext(); ) {
            out.remove(it.next());
        }
        return out;
    }

    // ---- averageOfInt / averageOfLong: CollectionsKt.averageOfInt:(Ljava/lang/Iterable;)D and
    //   averageOfLong:(Ljava/lang/Iterable;)D — sum / count as a double; NaN for an empty source
    //   (matching Kotlin: 0.0/0). The sum accumulates as a double to mirror the stdlib (it folds into a
    //   double accumulator), so this is sound under the no-symbolic-double proof policy (concrete only).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfInt(Iterable<Integer> source) {
        double sum = 0.0;
        int count = 0;
        for (Iterator<Integer> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfLong(Iterable<Long> source) {
        double sum = 0.0;
        int count = 0;
        for (Iterator<Long> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    // ---- flatten(Iterable<Iterable>): CollectionsKt.flatten:(Ljava/lang/Iterable;)Ljava/util/List; —
    // a NEW list concatenating every element of every inner iterable, in order. (Non-inline; real chain
    // nondet-stubs internal builders.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> flatten(Iterable<? extends Iterable<? extends T>> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<? extends Iterable<? extends T>> outer = source.iterator(); outer.hasNext(); ) {
            Iterable<? extends T> inner = outer.next();
            for (Iterator<? extends T> it = inner.iterator(); it.hasNext(); ) {
                out.add(it.next());
            }
        }
        return out;
    }

    // ---- sortedDescending(Iterable): CollectionsKt.sortedDescending:(Ljava/lang/Iterable;)Ljava/util/List;
    // — a NEW list sorted in DESCENDING natural order, source untouched. Implemented as an ascending
    // insertion-sort followed by a reverse (stable enough for the bounded element domain). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> List<T> sortedDescending(Iterable<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        insertionSort(out, null);
        // reverse in place to get descending
        int i = 0;
        int j = out.size() - 1;
        while (i < j) {
            T tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
            i++;
            j--;
        }
        return out;
    }

    // ---- sort(List) / sortDescending(List) / sortWith(List, Comparator): IN-PLACE mutators
    //   CollectionsKt.sort:(Ljava/util/List;)V
    //   CollectionsKt.sortDescending:(Ljava/util/List;)V
    //   CollectionsKt.sortWith:(Ljava/util/List;Ljava/util/Comparator;)V
    // sort the receiver in ascending natural / descending natural / Comparator order respectively,
    // returning void (the receiver is mutated). (Non-inline; real chain routes Collections.sort which
    // JBMC does not model over the bounded list.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        sortInPlace(toArrayList(list), list, null, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> void sortDescending(List<T> list) {
        sortInPlace(toArrayList(list), list, null, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> void sortWith(List<T> list, Comparator<? super T> comparator) {
        sortInPlace(toArrayList(list), list, comparator, false);
    }

    private static <T> ArrayList<T> toArrayList(List<T> list) {
        ArrayList<T> a = new ArrayList<>();
        for (int i = 0, n = list.size(); i < n; i++) {
            a.add(list.get(i));
        }
        return a;
    }

    // sort `scratch` (a copy), then write it back into `target` (optionally reversed for descending),
    // mutating the target in place to match the Kotlin in-place contract.
    private static <T> void sortInPlace(ArrayList<T> scratch, List<T> target,
            Comparator<? super T> cmp, boolean descending) {
        insertionSort(scratch, cmp);
        int n = scratch.size();
        for (int i = 0; i < n; i++) {
            T v = descending ? scratch.get(n - 1 - i) : scratch.get(i);
            target.set(i, v);
        }
    }

    // ---- asReversed(List) / asReversedMutable(List):
    //   CollectionsKt.asReversed:(Ljava/util/List;)Ljava/util/List;
    //   CollectionsKt.asReversedMutable:(Ljava/util/List;)Ljava/util/List;
    // The stdlib returns a LIVE reversed VIEW (writes alias back to the source). bmc4j models the READ
    // observable only: a reversed SNAPSHOT (a fresh bounded ArrayList in reverse order). The post-
    // construction write-through aliasing of the live view is NOT modeled — bounded proofs read the
    // reversed order (identical observable to the view as long as the source isn't mutated afterward),
    // matching the established `reversed()` precedent. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> asReversed(List<? extends T> list) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            out.add(list.get(i));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> asReversedMutable(List<T> list) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            out.add(list.get(i));
        }
        return out;
    }

    // ---- takeLast(List, int) / dropLast(List, int):
    //   CollectionsKt.takeLast:(Ljava/util/List;I)Ljava/util/List;
    //   CollectionsKt.dropLast:(Ljava/util/List;I)Ljava/util/List;
    // Kotlin contract: a negative n throws IllegalArgumentException; takeLast(n) returns the LAST
    // min(n, size) elements in order; dropLast(n) returns all but the last min(n, size). NEW lists,
    // source untouched. (Non-inline; the lambda-taking takeLastWhile/dropLastWhile are inline and stay
    // @BmcUnmodelable.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> takeLast(List<T> list, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested element count " + n + " is less than zero.");
        }
        int size = list.size();
        ArrayList<T> out = new ArrayList<>();
        if (n == 0) {
            return out;
        }
        int from = n >= size ? 0 : size - n;
        for (int i = from; i < size; i++) {
            out.add(list.get(i));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> dropLast(List<T> list, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested element count " + n + " is less than zero.");
        }
        int size = list.size();
        int keep = n >= size ? 0 : size - n;
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < keep; i++) {
            out.add(list.get(i));
        }
        return out;
    }

    // ---- slice(List, IntRange) / slice(List, Iterable<Integer>):
    //   CollectionsKt.slice:(Ljava/util/List;Lkotlin/ranges/IntRange;)Ljava/util/List;
    //   CollectionsKt.slice:(Ljava/util/List;Ljava/lang/Iterable;)Ljava/util/List;
    // Kotlin contract: a NEW list of the elements at the given indices. The IntRange overload returns the
    // [first..last] inclusive slice (empty list for an empty range); the Iterable overload picks the
    // listed indices in order. Out-of-range indices throw IndexOutOfBoundsException (via List.get).
    // (Non-inline.) The IntRange receiver is real kotlin-stdlib (getFirst/getLast/isEmpty are field reads).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> slice(List<T> list, kotlin.ranges.IntRange indices) {
        ArrayList<T> out = new ArrayList<>();
        if (indices.isEmpty()) {
            return out;
        }
        int from = indices.getFirst();
        int to = indices.getLast();
        for (int i = from; i <= to; i++) {
            out.add(list.get(i));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> slice(List<T> list, Iterable<Integer> indices) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<Integer> it = indices.iterator(); it.hasNext(); ) {
            out.add(list.get(it.next()));
        }
        return out;
    }

    // ---- chunked(Iterable, int): CollectionsKt.chunked:(Ljava/lang/Iterable;I)Ljava/util/List; — a NEW
    // list of sub-lists, each of `size` consecutive elements (the final chunk may be shorter). Kotlin
    // requires size > 0 (else IllegalArgumentException). (Non-inline; the transform overload chunked(_,_,
    // Function1) is inline and stays @BmcUnmodelable.) chunked is windowed(size, size, partialWindows=true).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<List<T>> chunked(Iterable<T> source, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size " + size + " must be greater than zero.");
        }
        ArrayList<List<T>> out = new ArrayList<>();
        ArrayList<T> current = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            current.add(it.next());
            if (current.size() == size) {
                out.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
    }

    // ---- windowed(Iterable, int, int, boolean): the full-arg overload
    //   CollectionsKt.windowed:(Ljava/lang/Iterable;IIZ)Ljava/util/List;
    // a NEW list of windows: sub-lists of length `size` starting at indices 0, step, 2*step, …; when
    // partialWindows is false the trailing windows that would extend past the end are dropped, when true
    // they are kept (shorter). Kotlin requires size > 0 AND step > 0 (else IllegalArgumentException).
    // (Non-inline; the transform overload is inline @BmcUnmodelable.)
    //
    // NOTE: the common Kotlin call `xs.windowed(size)` (step/partialWindows defaulted) routes through the
    // kotlinc-synthesized `windowed$default` bridge, which bmc4j does NOT model — JBMC nondet-stubs that
    // bridge so such a call site is UNKNOWN regardless of this body. This models the EXPLICIT full-arg
    // call `xs.windowed(size, step, partialWindows)`, which targets this method directly. The `$default`
    // bridge stays loud in the tail.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<List<T>> windowed(Iterable<T> source, int size, int step, boolean partialWindows) {
        if (size <= 0 || step <= 0) {
            throw new IllegalArgumentException(
                    "Both size " + size + " and step " + step + " must be greater than zero.");
        }
        // materialize the bounded source so we can index windows
        ArrayList<T> all = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            all.add(it.next());
        }
        int n = all.size();
        ArrayList<List<T>> out = new ArrayList<>();
        for (int start = 0; start < n; start += step) {
            int end = start + size; // exclusive
            if (end > n) {
                if (!partialWindows) {
                    break;
                }
                end = n;
            }
            ArrayList<T> window = new ArrayList<>();
            for (int i = start; i < end; i++) {
                window.add(all.get(i));
            }
            out.add(window);
        }
        return out;
    }

    // ===========================================================================================
    // models/kotlin-collections-3 pass: the high-value NON-INLINE CollectionsKt residue over the
    // bounded java.util backing. Inline-ness VERIFIED against kotlin-stdlib 2.4.0 @Metadata
    // (Attributes.isInline == false for every member below; the lambda-taking siblings of the same
    // name, e.g. count{}/any{}/firstOrNull{}/sumOf{}/partition{}, are inline and stay @BmcUnmodelable).
    // Each is a non-inline facade JVM method whose real kotlin-stdlib chain routes through internal
    // builders/iterators JBMC nondet-stubs; we build the bounded java.util collection model directly.
    // ===========================================================================================

    // ---- count / any / none (no-predicate forms): CollectionsKt.count:(Ljava/lang/Iterable;)I,
    //   any:(Ljava/lang/Iterable;)Z, none:(Ljava/lang/Iterable;)Z. count = element count; any = "not
    //   empty"; none = "empty". (Non-inline; the predicate overloads count{}/any{}/none{} are inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int count(Iterable<?> source) {
        if (source instanceof Collection) {
            return ((Collection<?>) source).size();
        }
        int n = 0;
        for (Iterator<?> it = source.iterator(); it.hasNext(); ) {
            it.next();
            n++;
        }
        return n;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean any(Iterable<?> source) {
        if (source instanceof Collection) {
            return !((Collection<?>) source).isEmpty();
        }
        return source.iterator().hasNext();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean none(Iterable<?> source) {
        if (source instanceof Collection) {
            return ((Collection<?>) source).isEmpty();
        }
        return !source.iterator().hasNext();
    }

    // ---- firstOrNull / lastOrNull (no-predicate forms): the *OrNull contract returns null on empty
    //   instead of throwing. Iterable + List overloads. (Non-inline; predicate overloads are inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T firstOrNull(Iterable<T> source) {
        Iterator<T> it = source.iterator();
        return it.hasNext() ? it.next() : null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T firstOrNull(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T lastOrNull(Iterable<T> source) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        T last = it.next();
        while (it.hasNext()) {
            last = it.next();
        }
        return last;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T lastOrNull(List<T> list) {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    // ---- maxOrThrow / minOrThrow: the Comparable-fold extremum that THROWS on an empty source
    //   (NoSuchElementException), Kotlin's `max()`/`min()` (JvmName maxOrThrow/minOrThrow). The *OrNull
    //   siblings are already modeled (return null on empty). (Non-inline; the *By{}/*Of{} forms are inline.)
    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Comparable maxOrThrow(Iterable source) {
        Iterator it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        Comparable max = (Comparable) it.next();
        while (it.hasNext()) {
            Comparable e = (Comparable) it.next();
            if (max.compareTo(e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Comparable minOrThrow(Iterable source) {
        Iterator it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        Comparable min = (Comparable) it.next();
        while (it.hasNext()) {
            Comparable e = (Comparable) it.next();
            if (min.compareTo(e) > 0) {
                min = e;
            }
        }
        return min;
    }

    // ---- maxWithOrNull / maxWithOrThrow / minWithOrNull / minWithOrThrow: Comparator-driven extremum.
    //   *OrNull returns null on empty; *OrThrow throws NoSuchElementException. The Comparator is a real
    //   first-class arg (Kotlin's maxWith/minWith take a Comparator, NOT a lambda) so this is non-inline.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T maxWithOrNull(Iterable<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        T max = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(max, e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T maxWithOrThrow(Iterable<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        T max = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(max, e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T minWithOrNull(Iterable<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        T min = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(min, e) > 0) {
                min = e;
            }
        }
        return min;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T minWithOrThrow(Iterable<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        T min = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(min, e) > 0) {
                min = e;
            }
        }
        return min;
    }

    // ---- sumOfByte / sumOfShort / sumOfFloat: the remaining typed sums (Int/Long/Double already
    //   modeled). Byte/Short widen to an int accumulator (matching Kotlin); Float folds into a double-
    //   sized... no — Kotlin's sumOfFloat folds into a FLOAT accumulator and returns float. (Non-inline;
    //   sumOf{}/sumBy{}/sumByDouble{} are inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfByte(Iterable<Byte> source) {
        int sum = 0;
        for (Iterator<Byte> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfShort(Iterable<Short> source) {
        int sum = 0;
        for (Iterator<Short> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float sumOfFloat(Iterable<Float> source) {
        float sum = 0.0f;
        for (Iterator<Float> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    // ---- averageOfByte / averageOfShort / averageOfFloat / averageOfDouble: sum / count as a double;
    //   NaN for an empty source (matching Kotlin's 0.0/0). Int/Long already modeled. Sum accumulates as
    //   a double to mirror the stdlib; sound under the concrete-only double policy. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfByte(Iterable<Byte> source) {
        double sum = 0.0;
        int count = 0;
        for (Iterator<Byte> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfShort(Iterable<Short> source) {
        double sum = 0.0;
        int count = 0;
        for (Iterator<Short> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfFloat(Iterable<Float> source) {
        double sum = 0.0;
        int count = 0;
        for (Iterator<Float> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfDouble(Iterable<Double> source) {
        double sum = 0.0;
        int count = 0;
        for (Iterator<Double> it = source.iterator(); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    // ---- filterNotNull / filterNotNullTo: a NEW list (or the supplied destination) of the non-null
    //   elements in order. (Non-inline — filterNotNull has no lambda; the predicate filter{} is inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> filterNotNull(Iterable<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, C extends Collection<? super T>> C filterNotNullTo(Iterable<T> source, C destination) {
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (e != null) {
                destination.add(e);
            }
        }
        return destination;
    }

    // ---- listOfNotNull(element) / listOfNotNull(vararg): a NEW read-only list of the non-null
    //   argument(s). The single-arg form yields an empty list when the arg is null. (Non-inline;
    //   listOfNotNull() no-arg/empty is a different inline factory.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> listOfNotNull(T element) {
        ArrayList<T> out = new ArrayList<>();
        if (element != null) {
            out.add(element);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> listOfNotNull(T[] elements) {
        ArrayList<T> out = new ArrayList<>();
        for (T e : elements) {
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- arrayListOf(vararg): CollectionsKt.arrayListOf:([Ljava/lang/Object;)Ljava/util/ArrayList; — a
    //   NEW (bounded) ArrayList of the arguments in order. (Non-inline; the no-arg arrayListOf() is inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> ArrayList<T> arrayListOf(T[] elements) {
        ArrayList<T> out = new ArrayList<>();
        for (T e : elements) {
            out.add(e);
        }
        return out;
    }

    // ---- getIndices(Collection) / getLastIndex(List): the `indices`/`lastIndex` property getters.
    //   indices = the IntRange 0..size-1 (empty range for an empty collection); lastIndex = size-1
    //   (-1 for an empty list). The returned IntRange is REAL kotlin-stdlib (analyzable; the slice model
    //   already drives it). (Non-inline property getters.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static IntRange getIndices(Collection<?> collection) {
        return new IntRange(0, collection.size() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int getLastIndex(List<? extends T> list) {
        return list.size() - 1;
    }

    // ---- reverse(List): IN-PLACE reverse of the receiver (Kotlin's MutableList.reverse(), JVM-backed by
    //   Collections.reverse). Returns void; the receiver is mutated. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> void reverse(List<T> list) {
        int i = 0;
        int j = list.size() - 1;
        while (i < j) {
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
            i++;
            j--;
        }
    }

    // ---- removeFirst / removeLast / removeFirstOrNull / removeLastOrNull (MutableList): remove and
    //   return the first/last element. The *OrNull forms return null on empty; the bare forms throw
    //   NoSuchElementException on empty. (Non-inline; these are Kotlin stdlib extensions, distinct from the
    //   JDK-21 SequencedCollection methods.) Backed by the bounded list model's remove(int).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T removeFirst(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        return list.remove(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T removeFirstOrNull(List<T> list) {
        return list.isEmpty() ? null : list.remove(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T removeLast(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        return list.remove(list.size() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T removeLastOrNull(List<T> list) {
        return list.isEmpty() ? null : list.remove(list.size() - 1);
    }

    // ---- requireNoNulls(Iterable) / requireNoNulls(List): return the receiver unchanged if it contains
    //   no nulls, else throw IllegalArgumentException naming the offending null. The stdlib returns a list
    //   of the SAME (non-null-typed) elements; we return the receiver after validating (the bounded source
    //   has no separate null-typed twin to copy into). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Iterable<T> requireNoNulls(Iterable<T> source) {
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            if (it.next() == null) {
                throw new IllegalArgumentException("null element found in " + source + ".");
            }
        }
        return source;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> requireNoNulls(List<T> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            if (list.get(i) == null) {
                throw new IllegalArgumentException("null element found in " + list + ".");
            }
        }
        return list;
    }

    // ---- unzip(Iterable<Pair>): CollectionsKt.unzip:(Ljava/lang/Iterable;)Lkotlin/Pair; — split a list
    //   of Pairs into a Pair of two lists (firsts, seconds), in order. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Pair<List<T>, List<R>> unzip(Iterable<? extends Pair<? extends T, ? extends R>> source) {
        ArrayList<T> firsts = new ArrayList<>();
        ArrayList<R> seconds = new ArrayList<>();
        for (Iterator<? extends Pair<? extends T, ? extends R>> it = source.iterator(); it.hasNext(); ) {
            Pair<? extends T, ? extends R> p = it.next();
            firsts.add(p.getFirst());
            seconds.add(p.getSecond());
        }
        return new Pair<>(firsts, seconds);
    }

    // ---- zip(Iterable, array): CollectionsKt.zip:(Ljava/lang/Iterable;[Ljava/lang/Object;)Ljava/util/List;
    //   for `xs.zip(arr)` — a NEW list of Pairs, truncated to the shorter input. The Iterable+Iterable zip
    //   is already modeled; this is the array-RHS twin. (Non-inline; the transform overload is inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> List<Pair<T, R>> zip(Iterable<T> source, R[] other) {
        ArrayList<Pair<T, R>> result = new ArrayList<>();
        Iterator<T> a = source.iterator();
        int i = 0;
        while (a.hasNext() && i < other.length) {
            result.add(new Pair<>(a.next(), other[i]));
            i++;
        }
        return result;
    }

    // ---- zipWithNext(Iterable): CollectionsKt.zipWithNext:(Ljava/lang/Iterable;)Ljava/util/List; — a NEW
    //   list of consecutive Pairs [(e0,e1),(e1,e2),…]; empty when the source has fewer than 2 elements.
    //   (Non-inline; the transform overload zipWithNext{} is inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<Pair<T, T>> zipWithNext(Iterable<T> source) {
        ArrayList<Pair<T, T>> result = new ArrayList<>();
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return result;
        }
        T prev = it.next();
        while (it.hasNext()) {
            T cur = it.next();
            result.add(new Pair<>(prev, cur));
            prev = cur;
        }
        return result;
    }

    // ---- withIndex(Iterable): CollectionsKt.withIndex:(Ljava/lang/Iterable;)Ljava/lang/Iterable; — pairs
    //   each element with its 0-based index as an IndexedValue. The stdlib returns a LAZY IndexingIterable
    //   (its iterator is internal stdlib JBMC nondet-stubs); we materialize the bounded source EAGERLY into
    //   a list of IndexedValue (identical read observable over the bounded model). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Iterable<IndexedValue<T>> withIndex(Iterable<T> source) {
        ArrayList<IndexedValue<T>> out = new ArrayList<>();
        int i = 0;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(new IndexedValue<>(i, it.next()));
            i++;
        }
        return out;
    }

    // ---- toHashSet(Iterable) / toCollection(Iterable, destination): bulk drains into a NEW HashSet (dedup
    //   via equals/hashCode, unordered) / into the supplied destination (returned). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> HashSet<T> toHashSet(Iterable<T> source) {
        HashSet<T> out = new HashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, C extends Collection<? super T>> C toCollection(Iterable<T> source, C destination) {
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            destination.add(it.next());
        }
        return destination;
    }

    // ---- toIntArray / toLongArray / toByteArray / toShortArray / toCharArray / toBooleanArray /
    //   toDoubleArray / toFloatArray (Collection<boxed>): a NEW primitive array of the unboxed elements in
    //   iteration order, sized to the collection. (Non-inline; over the bounded collection model.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] toIntArray(Collection<Integer> source) {
        int[] out = new int[source.size()];
        int i = 0;
        for (Iterator<Integer> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] toLongArray(Collection<Long> source) {
        long[] out = new long[source.size()];
        int i = 0;
        for (Iterator<Long> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] toByteArray(Collection<Byte> source) {
        byte[] out = new byte[source.size()];
        int i = 0;
        for (Iterator<Byte> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] toShortArray(Collection<Short> source) {
        short[] out = new short[source.size()];
        int i = 0;
        for (Iterator<Short> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] toCharArray(Collection<Character> source) {
        char[] out = new char[source.size()];
        int i = 0;
        for (Iterator<Character> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] toBooleanArray(Collection<Boolean> source) {
        boolean[] out = new boolean[source.size()];
        int i = 0;
        for (Iterator<Boolean> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] toDoubleArray(Collection<Double> source) {
        double[] out = new double[source.size()];
        int i = 0;
        for (Iterator<Double> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] toFloatArray(Collection<Float> source) {
        float[] out = new float[source.size()];
        int i = 0;
        for (Iterator<Float> it = source.iterator(); it.hasNext(); ) {
            out[i++] = it.next();
        }
        return out;
    }

    // ---- addAll(collection, elements[]): CollectionsKt.addAll:(Ljava/util/Collection;[Ljava/lang/Object;)Z
    // MUTATE the receiver (CONCRETE bounded collection model — add() devirtualizes) by appending the
    // vararg elements; returns true iff non-empty (the JDK Collection.addAll contract). The Iterable /
    // Sequence sibling forms are below. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean addAll(Collection<? super T> collection, T[] elements) {
        boolean changed = false;
        for (T e : elements) {
            collection.add(e);
            changed = true;
        }
        return changed;
    }

    // addAll(collection, sequence): the Sequence twin — append the sequence's elements (drained via its
    //   CollectionsKt.addAll:(Ljava/util/Collection;Lkotlin/sequences/Sequence;)Z
    // concrete backing) to the receiver; returns whether anything was added.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean addAll(Collection<? super T> collection, Sequence<? extends T> elements) {
        boolean changed = false;
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            collection.add(it.next());
            changed = true;
        }
        return changed;
    }

    // ---- plus(collection/iterable, sequence) / minus(iterable, sequence): the Sequence-arg twins of the
    //   CollectionsKt.plus:(Ljava/util/Collection;Lkotlin/sequences/Sequence;)Ljava/util/List;
    //   CollectionsKt.plus:(Ljava/lang/Iterable;Lkotlin/sequences/Sequence;)Ljava/util/List;
    //   CollectionsKt.minus:(Ljava/lang/Iterable;Lkotlin/sequences/Sequence;)Ljava/util/List;
    // element/array/iterable plus/minus modeled earlier. plus returns a NEW list = receiver ++ sequence;
    // minus returns a NEW list with ALL elements contained in the sequence removed (equals). Receiver
    // untouched. The sequence is drained via its CONCRETE backing (seqIter). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Collection<T> source, Sequence<? extends T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> plus(Iterable<T> source, Sequence<? extends T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> minus(Iterable<T> source, Sequence<? extends T> elements) {
        LinkedHashSet<T> remove = new LinkedHashSet<>();
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            remove.add(it.next());
        }
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T e = it.next();
            if (!remove.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- removeAll / retainAll (collection, {iterable | elements[] | sequence}): MUTATE the receiver
    //   CollectionsKt.removeAll:(Ljava/util/Collection;Ljava/lang/Iterable;)Z      (+ Object[] / Sequence)
    //   CollectionsKt.retainAll:(Ljava/util/Collection;Ljava/lang/Iterable;)Z      (+ Object[] / Sequence)
    // in place — removeAll drops every element contained in the argument; retainAll keeps only those.
    // Returns whether the receiver changed. Each materializes the argument into a bounded LinkedHashSet
    // and delegates to the CONCRETE collection model's removeAll/retainAll(Collection) (modeled, audited).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean removeAll(Collection<? super T> collection, Iterable<? extends T> elements) {
        return collection.removeAll(toBoundedSet(elements));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean removeAll(Collection<? super T> collection, T[] elements) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        for (T e : elements) {
            set.add(e);
        }
        return collection.removeAll(set);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean removeAll(Collection<? super T> collection, Sequence<? extends T> elements) {
        return collection.removeAll(seqToBoundedSet(elements));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean retainAll(Collection<? super T> collection, Iterable<? extends T> elements) {
        return collection.retainAll(toBoundedSet(elements));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean retainAll(Collection<? super T> collection, T[] elements) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        for (T e : elements) {
            set.add(e);
        }
        return collection.retainAll(set);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean retainAll(Collection<? super T> collection, Sequence<? extends T> elements) {
        return collection.retainAll(seqToBoundedSet(elements));
    }

    // ---- removeAll / retainAll (iterable | list, predicate): MUTATE the receiver in place by the
    //   CollectionsKt.removeAll:(Ljava/lang/Iterable;Lkotlin/jvm/functions/Function1;)Z
    //   CollectionsKt.removeAll:(Ljava/util/List;Lkotlin/jvm/functions/Function1;)Z
    //   CollectionsKt.retainAll:(Ljava/lang/Iterable;Lkotlin/jvm/functions/Function1;)Z
    //   CollectionsKt.retainAll:(Ljava/util/List;Lkotlin/jvm/functions/Function1;)Z
    // PREDICATE (non-inline stdlib functions — the JVM body invokes Function1.invoke per element). The
    // lambda is concrete-devirtualized (bmc4j desugars it), exactly like the bounded ArrayList.removeIf
    // model. removeAll drops elements the predicate accepts; retainAll keeps them. Compact in place over
    // the CONCRETE receiver via its index get/remove; returns whether anything changed.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean removeAll(Iterable<T> source, Function1<? super T, Boolean> predicate) {
        return filterInPlace(source, predicate, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean removeAll(List<T> source, Function1<? super T, Boolean> predicate) {
        return filterInPlace(source, predicate, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean retainAll(Iterable<T> source, Function1<? super T, Boolean> predicate) {
        return filterInPlace(source, predicate, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean retainAll(List<T> source, Function1<? super T, Boolean> predicate) {
        return filterInPlace(source, predicate, false);
    }

    // ---- binarySearch(list, element, fromIndex, toIndex): CollectionsKt.binarySearch:
    //   (Ljava/util/List;Ljava/lang/Comparable;II)I
    // NATURAL-ORDER binary search over the [fromIndex, toIndex) sub-range of a list assumed sorted by the
    // element's natural Comparable order. Returns the index of a match, or -(insertionPoint)-1 if absent —
    // the exact JDK/stdlib contract. Pure Comparable.compareTo arithmetic over the bounded list model
    // (JBMC's core competence); the COMPARATOR overload below stays a loud wall (total order over
    // unconstrained T). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Comparable<? super T>> int binarySearch(
            List<? extends T> list, T element, int fromIndex, int toIndex) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = list.get(mid);
            int cmp;
            if (midVal == null) {
                cmp = (element == null) ? 0 : -1;
            } else if (element == null) {
                cmp = 1;
            } else {
                cmp = ((Comparable) midVal).compareTo(element);
            }
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    // ---- withIndex(iterator): CollectionsKt.withIndex:(Ljava/util/Iterator;)Ljava/util/Iterator; — wrap an
    // iterator so each element is paired with its 0-based index as an IndexedValue. The stdlib returns a
    // LAZY IndexingIterator; we drain the (bounded) source EAGERLY into a list of IndexedValue and return
    // its iterator (identical read observable over the bounded model). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Iterator<IndexedValue<T>> withIndex(Iterator<T> iterator) {
        ArrayList<IndexedValue<T>> out = new ArrayList<>();
        int i = 0;
        while (iterator.hasNext()) {
            out.add(new IndexedValue<>(i, iterator.next()));
            i++;
        }
        return out.iterator();
    }

    // ---- helpers for the set-op / predicate / sequence models above. ---------------------------------

    /** Materialize an Iterable into a bounded LinkedHashSet (for the Collection.removeAll/retainAll arg). */
    private static <T> LinkedHashSet<T> toBoundedSet(Iterable<? extends T> elements) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        for (Iterator<? extends T> it = elements.iterator(); it.hasNext(); ) {
            set.add(it.next());
        }
        return set;
    }

    /** Materialize a model Sequence (via its concrete backing) into a bounded LinkedHashSet. */
    private static <T> LinkedHashSet<T> seqToBoundedSet(Sequence<? extends T> elements) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        for (Iterator<? extends T> it = seqIter(elements); it.hasNext(); ) {
            set.add(it.next());
        }
        return set;
    }

    /**
     * In-place predicate filter for removeAll/retainAll(predicate): compact the receiver, dropping
     * elements whose predicate result equals {@code removeMatched}. Operates on the receiver as a List
     * (the bounded list model is the concrete receiver; Iterable receivers in idiomatic Kotlin are
     * MutableList). Returns whether anything was removed.
     */
    @SuppressWarnings("unchecked")
    private static <T> boolean filterInPlace(Iterable<T> source, Function1<? super T, Boolean> predicate,
            boolean removeMatched) {
        CProver.assume(source instanceof List);
        List<T> list = (List<T>) source;          // the concrete bounded MutableList receiver
        int w = 0;
        boolean changed = false;
        int n = list.size();
        for (int r = 0; r < n; r++) {
            T e = list.get(r);
            if (predicate.invoke(e).booleanValue() == removeMatched) {
                changed = true;                    // dropped
            } else {
                list.set(w++, e);                  // kept
            }
        }
        // truncate the tail [w, n) by removing from the end
        for (int r = n - 1; r >= w; r--) {
            list.remove(r);
        }
        return changed;
    }

    /**
     * Drain a model {@link Sequence} via its concrete backing {@link ArrayList} snapshot's iterator rather
     * than the virtual {@code Sequence.iterator()} — {@link ListSequence} is the sole {@code final}
     * implementor, so the {@code checkcast} is sound and the concrete {@code ArrayList.iterator()} resolves
     * where the interface dispatch on the {@code Sequence}-typed parameter is kotlinc-version-fragile.
     * Mirrors {@code SequencesKt}'s {@code seqIter}/{@code backing} pattern.
     */
    @SuppressWarnings("unchecked")
    private static <T> Iterator<T> seqIter(Sequence<? extends T> source) {
        CProver.assume(source instanceof ListSequence);
        return (Iterator<T>) ((ListSequence<? extends T>) source).backingList().iterator();
    }

    // --- structural / inline / Random / string / reflective walls (loud stubs; reaching one demotes to a
    //     member-named UNKNOWN). Each names exactly why no sound bounded body exists. -------------------

    @BmcUnmodelable(reason = "comparator total-order over unconstrained T — bmc4j does not reason about a "
            + "Comparator's contract over symbolic elements (the Double.compare/devirt wall family); the "
            + "natural-order binarySearch(List,Comparable,int,int) above IS modeled. Loud-if-reached")
    public static int binarySearch(java.util.List a0, java.lang.Object a1, java.util.Comparator a2, int a3, int a4) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.binarySearch(java.util.List,java.lang.Object,java.util.Comparator,int,int) — comparator total-order over unconstrained T");
    }

    @BmcUnmodelable(reason = "comparison-function total-order over unconstrained T (Function1<T,Int> sign "
            + "search) — same total-order-over-T wall as the Comparator overload; loud-if-reached")
    public static int binarySearch(java.util.List a0, int a1, int a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.binarySearch(java.util.List,int,int,kotlin.jvm.functions.Function1) — comparison-function total-order over unconstrained T");
    }

    @BmcUnmodelable(reason = "chunked(_,_,transform) is INLINE — the body lands in the caller; the facade JVM "
            + "method is never called from a Kotlin call site. The no-transform chunked(Iterable,int) IS modeled")
    public static java.util.List chunked(java.lang.Iterable a0, int a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.chunked(java.lang.Iterable,int,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "windowed(_,_,_,_,transform) is INLINE — the body lands in the caller; the facade "
            + "JVM method is never called from a Kotlin call site. The no-transform windowed(...) IS modeled")
    public static java.util.List windowed(java.lang.Iterable a0, int a1, int a2, boolean a3, kotlin.jvm.functions.Function1 a4) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.windowed(java.lang.Iterable,int,int,boolean,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "elementAtOrElse(_,_,defaultValue) is INLINE — the body lands in the caller; the "
            + "facade JVM method is never called from a Kotlin call site")
    public static java.lang.Object elementAtOrElse(java.lang.Iterable a0, int a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.elementAtOrElse(java.lang.Iterable,int,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "kotlin.random.Random draw is nondeterministic — there is no sound bounded model "
            + "for random()/randomOrNull()/shuffle()/shuffled() (matches the RangesKt.random precedent); loud-if-reached")
    public static java.lang.Object random(java.util.Collection a0, kotlin.random.Random a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.random(java.util.Collection,kotlin.random.Random) — nondeterministic Random draw; no sound bounded model");
    }

    @BmcUnmodelable(reason = "kotlin.random.Random draw is nondeterministic — no sound bounded model; loud-if-reached")
    public static java.lang.Object randomOrNull(java.util.Collection a0, kotlin.random.Random a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.randomOrNull(java.util.Collection,kotlin.random.Random) — nondeterministic Random draw; no sound bounded model");
    }

    @BmcUnmodelable(reason = "kotlin.random.Random-driven in-place shuffle is nondeterministic — no sound "
            + "bounded model; loud-if-reached")
    public static void shuffle(java.util.List a0, kotlin.random.Random a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.shuffle(java.util.List,kotlin.random.Random) — nondeterministic Random-driven shuffle; no sound bounded model");
    }

    @BmcUnmodelable(reason = "Random-driven shuffled() is nondeterministic — no sound bounded model; loud-if-reached")
    public static java.util.List shuffled(java.lang.Iterable a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.shuffled(java.lang.Iterable) — nondeterministic shuffle; no sound bounded model");
    }

    @BmcUnmodelable(reason = "Random-driven shuffled(Random) is nondeterministic — no sound bounded model; loud-if-reached")
    public static java.util.List shuffled(java.lang.Iterable a0, kotlin.random.Random a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.shuffled(java.lang.Iterable,kotlin.random.Random) — nondeterministic shuffle; no sound bounded model");
    }

    @BmcUnmodelable(reason = "Random-driven shuffled(java.util.Random) (the JDK-Random overload) is "
            + "nondeterministic — no sound bounded model; loud-if-reached")
    public static java.util.List shuffled(java.lang.Iterable a0, java.util.Random a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.shuffled(java.lang.Iterable,java.util.Random) — nondeterministic shuffle; no sound bounded model");
    }

    @BmcUnmodelable(reason = "filterIsInstance dispatches on java.lang.Class.isInstance — runtime reflective "
            + "type introspection bmc4j does not model (kotlin.reflect.* is off the modeled surface); loud-if-reached")
    public static java.util.List filterIsInstance(java.lang.Iterable a0, java.lang.Class a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterIsInstance(java.lang.Iterable,java.lang.Class) — reflective Class.isInstance; not modeled");
    }

    @BmcUnmodelable(reason = "filterIsInstanceTo dispatches on java.lang.Class.isInstance — reflective type "
            + "introspection bmc4j does not model; loud-if-reached")
    public static java.util.Collection filterIsInstanceTo(java.lang.Iterable a0, java.util.Collection a1, java.lang.Class a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterIsInstanceTo(java.lang.Iterable,java.util.Collection,java.lang.Class) — reflective Class.isInstance; not modeled");
    }

    @BmcUnmodelable(reason = "joinTo is STRING-HEAVY (Appendable/StringBuilder append is the JBMC string-blowup "
            + "that OOMs) AND routes through a kotlinc-synthesized joinTo$default bridge bmc4j does not model; "
            + "a sound model needs a differential (not proof) harness for the string output; loud-if-reached")
    public static java.lang.Appendable joinTo(java.lang.Iterable a0, java.lang.Appendable a1, java.lang.CharSequence a2,
            java.lang.CharSequence a3, java.lang.CharSequence a4, int a5, java.lang.CharSequence a6, kotlin.jvm.functions.Function1 a7) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.joinTo(java.lang.Iterable,java.lang.Appendable,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1) — string-heavy; not modeled");
    }

    @BmcUnmodelable(reason = "joinToString is STRING-HEAVY (the JBMC string-blowup that OOMs CI) AND routes "
            + "through a kotlinc-synthesized joinToString$default bridge bmc4j does not model; loud-if-reached")
    public static java.lang.String joinToString(java.lang.Iterable a0, java.lang.CharSequence a1, java.lang.CharSequence a2,
            java.lang.CharSequence a3, int a4, java.lang.CharSequence a5, kotlin.jvm.functions.Function1 a6) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.joinToString(java.lang.Iterable,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1) — string-heavy; not modeled");
    }

    @BmcUnmodelable(reason = "toSortedSet returns a java.util.TreeSet — bmc4j has no bounded TreeSet model "
            + "(only natural-ordering TreeMap); a HashSet substitute would silently drop the sorted-navigation "
            + "observable; loud UNKNOWN under JBMC until a TreeSet model lands")
    public static java.util.TreeSet toSortedSet(java.lang.Iterable a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.toSortedSet(java.lang.Iterable) — returns a java.util.TreeSet; bmc4j has no bounded TreeSet model");
    }

    @BmcUnmodelable(reason = "toSortedSet(Comparator) returns a comparator-ordered java.util.TreeSet — bmc4j "
            + "has no bounded TreeSet model and the tree models are natural-ordering only; loud-if-reached")
    public static java.util.TreeSet toSortedSet(java.lang.Iterable a0, java.util.Comparator a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.toSortedSet(java.lang.Iterable,java.util.Comparator) — comparator-ordered java.util.TreeSet; bmc4j has no bounded TreeSet model");
    }

    @BmcUnmodelable(reason = "iterator(Enumeration) bridges a java.util.Enumeration (legacy external iteration "
            + "protocol) bmc4j does not model; loud-if-reached")
    public static java.util.Iterator iterator(java.util.Enumeration a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.iterator(java.util.Enumeration) — Enumeration bridge; not modeled");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib read-only-list size-optimizer (singleton/empty "
            + "specialization) on the list-build path; not reachable from idiomatic user code and its real "
            + "body routes through stdlib singleton-collection internals that JBMC stubs; loud-if-reached")
    public static java.util.List optimizeReadOnlyList(java.util.List a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.optimizeReadOnlyList(java.util.List) — internal kotlin-stdlib read-only-list size-optimizer");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib Iterable->Collection size probe (returns the size of a "
            + "Collection or null) on the conversion fast path; internal plumbing not reachable from idiomatic "
            + "user code; loud-if-reached")
    public static java.lang.Integer collectionSizeOrNull(java.lang.Iterable a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.collectionSizeOrNull(java.lang.Iterable) — internal Iterable size probe");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib Iterable->Collection conversion fast path; internal "
            + "plumbing not reachable from idiomatic user code; loud-if-reached")
    public static java.util.Collection convertToListIfNotCollection(java.lang.Iterable a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.convertToListIfNotCollection(java.lang.Iterable) — internal conversion fast path");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib Collection.toArray() common implementation — the JVM "
            + "array-bridge plumbing behind toArray; not reachable from idiomatic user code; loud-if-reached")
    public static java.lang.Object[] collectionToArrayCommonImpl(java.util.Collection a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.collectionToArrayCommonImpl(java.util.Collection) — internal toArray bridge");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib Collection.toArray(T[]) common implementation — JVM "
            + "array-bridge plumbing; not reachable from idiomatic user code; loud-if-reached")
    public static java.lang.Object[] collectionToArrayCommonImpl(java.util.Collection a0, java.lang.Object[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.collectionToArrayCommonImpl(java.util.Collection,java.lang.Object[]) — internal toArray bridge");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib toArray reflective array-copy helper (copyToArrayOfAny); "
            + "JVM array-bridge plumbing not reachable from idiomatic user code; loud-if-reached")
    public static java.lang.Object[] copyToArrayOfAny(java.lang.Object[] a0, boolean a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.copyToArrayOfAny(java.lang.Object[],boolean) — internal toArray array-copy helper");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib toArray terminator helper (terminateCollectionToArray); "
            + "JVM array-bridge plumbing not reachable from idiomatic user code; loud-if-reached")
    public static java.lang.Object[] terminateCollectionToArray(int a0, java.lang.Object[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.terminateCollectionToArray(int,java.lang.Object[]) — internal toArray terminator helper");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib vararg->Collection wrapper (asCollection) behind the "
            + "vararg factory fast path; internal plumbing not reachable from idiomatic user code; loud-if-reached")
    public static java.util.Collection asCollection(java.lang.Object[] a0, boolean a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.asCollection(java.lang.Object[],boolean) — internal vararg->Collection wrapper");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib count-overflow throw helper — unconditionally throws "
            + "ArithmeticException; an internal guard not reachable from idiomatic user code; loud-if-reached")
    public static void throwCountOverflow() {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.throwCountOverflow() — internal count-overflow throw helper");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib index-overflow throw helper — unconditionally throws "
            + "ArithmeticException; an internal guard not reachable from idiomatic user code; loud-if-reached")
    public static void throwIndexOverflow() {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.throwIndexOverflow() — internal index-overflow throw helper");
    }

    // --- inline lambda-taking members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void all(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.all(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void any(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.any(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associate(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associate(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateBy(java.lang.Iterable,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWith(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateWith(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWithTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateWithTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void binarySearchBy(java.util.List a0, java.lang.Comparable a1, int a2, int a3, kotlin.jvm.functions.Function1 a4) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.binarySearchBy(java.util.List,java.lang.Comparable,int,int,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void contains(java.lang.Iterable a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.contains(java.lang.Iterable,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void count(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.count(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void distinctBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.distinctBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void dropLastWhile(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.dropLastWhile(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void dropWhile(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.dropWhile(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void elementAt(java.lang.Iterable a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.elementAt(java.lang.Iterable,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void elementAtOrNull(java.lang.Iterable a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.elementAtOrNull(java.lang.Iterable,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filter(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filter(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterIndexedTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterIndexedTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNot(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterNot(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNotTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterNotTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void first(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.first(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void firstOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.firstOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMap(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMap(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequence(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMapSequence(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequenceTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMapSequenceTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMapTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void fold(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.fold(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldIndexed(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.foldIndexed(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldRight(java.util.List a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.foldRight(java.util.List,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldRightIndexed(java.util.List a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.foldRightIndexed(java.util.List,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.forEach(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(java.util.Iterator a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.forEach(java.util.Iterator,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEachIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.forEachIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void getOrNull(java.util.List a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.getOrNull(java.util.List,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupBy(java.lang.Iterable,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupingBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupingBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void indexOf(java.lang.Iterable a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOf(java.lang.Iterable,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void indexOf(java.util.List a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOf(java.util.List,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfFirst(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfFirst(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfFirst(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfFirst(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfLast(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfLast(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfLast(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfLast(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void last(java.lang.Iterable a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.last(java.lang.Iterable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void last(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.last(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void last(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.last(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void lastIndexOf(java.lang.Iterable a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastIndexOf(java.lang.Iterable,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void lastIndexOf(java.util.List a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastIndexOf(java.util.List,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void lastOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void lastOrNull(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastOrNull(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void map(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.map(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedNotNull(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexedNotNull(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedNotNullTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexedNotNullTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexedTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapNotNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNullTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapNotNullTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.maxByOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrThrow(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.maxByOrThrow(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.minByOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrThrow(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.minByOrThrow(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void none(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.none(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEach(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.onEach(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEachIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.onEachIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void partition(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.partition(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduce(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduce(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceIndexed(java.lang.Iterable,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexedOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceIndexedOrNull(java.lang.Iterable,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceOrNull(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRight(java.util.List a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRight(java.util.List,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRightIndexed(java.util.List a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRightIndexed(java.util.List,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRightIndexedOrNull(java.util.List a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRightIndexedOrNull(java.util.List,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRightOrNull(java.util.List a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRightOrNull(java.util.List,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningFold(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningFold(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningFoldIndexed(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningFoldIndexed(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningReduce(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningReduce(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningReduceIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningReduceIndexed(java.lang.Iterable,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void scan(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.scan(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void scanIndexed(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.scanIndexed(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void single(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.single(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void singleOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.singleOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortBy(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortBy(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortByDescending(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortByDescending(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortedBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedByDescending(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortedByDescending(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sumBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumByDouble(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sumByDouble(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void takeLastWhile(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.takeLastWhile(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void takeWhile(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.takeWhile(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void zip(java.lang.Iterable a0, java.lang.Iterable a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.zip(java.lang.Iterable,java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void zip(java.lang.Iterable a0, java.lang.Object[] a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.zip(java.lang.Iterable,java.lang.Object[],kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void zipWithNext(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.zipWithNext(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

}

