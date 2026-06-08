package kotlin.collections;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotNeeded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import kotlin.Pair;
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
@BmcModelTail(reason = "exotic CollectionsKt facade remainder — the bulk of kotlin-stdlib's Iterable/"
        + "Collection extension functions (windowing/grouping/aggregation/set-ops/etc.) the bounded "
        + "proofs do not exercise; loud under JBMC if reached")
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
    // @BmcNotNeeded.)
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
    // Function1) is inline and stays @BmcNotNeeded.) chunked is windowed(size, size, partialWindows=true).
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
    // (Non-inline; the transform overload is inline @BmcNotNeeded.)
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

    // NOTE: shuffled / shuffle stay in the @BmcModelTail residue — a Random draw is nondeterministic by
    // nature, so there is no sound bounded model (matches the RangesKt.random precedent). toSortedSet /
    // sortedSetOf are NOT modeled here either: they return a java.util.TreeSet, for which bmc4j has no
    // bounded model (only TreeMap exists) — out of scope until a TreeSet model lands. Loud under JBMC.

    // NOTE: joinToString / joinTo are deliberately NOT modeled in this pass and stay in the
    // @BmcModelTail residue. They are doubly hostile to bounded proof: (1) STRING-HEAVY (the
    // StringBuilder/append reasoning is the JBMC string-blowup that OOM'd CI — see #124), and (2) the
    // Kotlin call site routes through a kotlinc-synthesized `joinToString$default` bridge (default args)
    // that bmc4j does not model, so JBMC nondet-stubs the bridge and the verdict is UNKNOWN regardless
    // of the body's correctness. A sound model would need the `$default` bridge + a differential
    // (not proof) harness for the string output; out of scope here. Loud under JBMC if reached.

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void all(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.all(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void any(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.any(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associate(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associate(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateBy(java.lang.Iterable,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWith(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateWith(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWithTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.associateWithTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void binarySearchBy(java.util.List a0, java.lang.Comparable a1, int a2, int a3, kotlin.jvm.functions.Function1 a4) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.binarySearchBy(java.util.List,java.lang.Comparable,int,int,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void contains(java.lang.Iterable a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.contains(java.lang.Iterable,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void count(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.count(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void distinctBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.distinctBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void dropLastWhile(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.dropLastWhile(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void dropWhile(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.dropWhile(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void elementAt(java.lang.Iterable a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.elementAt(java.lang.Iterable,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void elementAtOrNull(java.lang.Iterable a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.elementAtOrNull(java.lang.Iterable,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filter(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filter(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterIndexedTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterIndexedTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNot(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterNot(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNotTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterNotTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.filterTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void first(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.first(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void firstOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.firstOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMap(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMap(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequence(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMapSequence(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequenceTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMapSequenceTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.flatMapTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void fold(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.fold(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldIndexed(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.foldIndexed(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldRight(java.util.List a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.foldRight(java.util.List,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldRightIndexed(java.util.List a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.foldRightIndexed(java.util.List,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.forEach(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(java.util.Iterator a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.forEach(java.util.Iterator,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEachIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.forEachIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void getOrNull(java.util.List a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.getOrNull(java.util.List,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupBy(java.lang.Iterable,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(java.lang.Iterable a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupByTo(java.lang.Iterable,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupingBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.groupingBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void indexOf(java.lang.Iterable a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOf(java.lang.Iterable,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void indexOf(java.util.List a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOf(java.util.List,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfFirst(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfFirst(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfFirst(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfFirst(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfLast(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfLast(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfLast(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.indexOfLast(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void last(java.lang.Iterable a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.last(java.lang.Iterable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void last(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.last(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void last(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.last(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void lastIndexOf(java.lang.Iterable a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastIndexOf(java.lang.Iterable,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void lastIndexOf(java.util.List a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastIndexOf(java.util.List,java.lang.Object) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void lastOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void lastOrNull(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.lastOrNull(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void map(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.map(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedNotNull(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexedNotNull(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedNotNullTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexedNotNullTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapIndexedTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapNotNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNullTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapNotNullTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapTo(java.lang.Iterable a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.mapTo(java.lang.Iterable,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.maxByOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrThrow(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.maxByOrThrow(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.minByOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrThrow(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.minByOrThrow(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void none(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.none(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEach(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.onEach(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEachIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.onEachIndexed(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void partition(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.partition(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduce(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduce(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceIndexed(java.lang.Iterable,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexedOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceIndexedOrNull(java.lang.Iterable,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceOrNull(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRight(java.util.List a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRight(java.util.List,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRightIndexed(java.util.List a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRightIndexed(java.util.List,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRightIndexedOrNull(java.util.List a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRightIndexedOrNull(java.util.List,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceRightOrNull(java.util.List a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.reduceRightOrNull(java.util.List,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningFold(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningFold(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningFoldIndexed(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningFoldIndexed(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningReduce(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningReduce(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void runningReduceIndexed(java.lang.Iterable a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.runningReduceIndexed(java.lang.Iterable,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void scan(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.scan(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void scanIndexed(java.lang.Iterable a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.scanIndexed(java.lang.Iterable,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void single(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.single(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void singleOrNull(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.singleOrNull(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortBy(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortBy(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortByDescending(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortByDescending(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortedBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedByDescending(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sortedByDescending(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumBy(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sumBy(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumByDouble(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.sumByDouble(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void takeLastWhile(java.util.List a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.takeLastWhile(java.util.List,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void takeWhile(java.lang.Iterable a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.takeWhile(java.lang.Iterable,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void zip(java.lang.Iterable a0, java.lang.Iterable a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.zip(java.lang.Iterable,java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void zip(java.lang.Iterable a0, java.lang.Object[] a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.zip(java.lang.Iterable,java.lang.Object[],kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void zipWithNext(java.lang.Iterable a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.CollectionsKt.zipWithNext(java.lang.Iterable,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

}

