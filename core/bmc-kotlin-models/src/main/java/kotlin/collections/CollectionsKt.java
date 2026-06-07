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

