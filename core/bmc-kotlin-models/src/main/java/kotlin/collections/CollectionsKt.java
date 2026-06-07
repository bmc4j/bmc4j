package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;

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
}

