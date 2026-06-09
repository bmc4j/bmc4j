package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.random.RandomGenerator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.Collections}. The bounded, natural-order, single-collection static
 * utilities are modeled over the concrete-backing list/set/map models: {@code enumeration}, the
 * {@code emptyList/Set/Map} and {@code singleton(List/Map)} / {@code nCopies} producers, the in-place
 * {@code reverse}/{@code swap}/{@code fill}/{@code rotate}/{@code replaceAll}/{@code copy} list edits,
 * the {@code frequency}/{@code disjoint}/{@code addAll} collection queries, natural-order {@code min}/
 * {@code max}/{@code binarySearch}/{@code sort}. Each iterates by index/iterator over the real backing
 * — no virtual dispatch through the Collections interface, no comparator devirt.
 *
 * <p>The remainder is now per-member LOUD ({@code @BmcUnmodelable}, honest {@code UNKNOWN} under JBMC if
 * reached — never a silent stub): the {@code Comparator}-taking {@code sort}/{@code min}/{@code max}/
 * {@code binarySearch}/{@code reverseOrder} (comparator devirt), the {@code shuffle} overloads
 * (seeded-RNG reproducibility, like the {@code Random} wall), the {@code unmodifiable*}/{@code
 * synchronized*}/{@code checked*} wrapper views (need a wrapper class over the backing), {@code
 * asLifoQueue}/{@code newSetFromMap}/{@code newSequencedSetFromMap}, and the {@code emptyIterator}/
 * {@code emptyEnumeration}/empty-navigable/sorted/sequenced exotic factories. The class-level
 * {@code @BmcModelTail} is gone: every real member is now an explicit per-member decision.
 *
 * <p>{@code enumeration} snapshots the collection's elements into a fixed-capacity array (bounded by
 * the source's size — keep it within the proof's {@code unwind}) and walks them by index. This is the
 * concrete-backing iteration pattern: no virtual dispatch through the Enumeration interface, the
 * cursor advances over a real array.
 */
public final class Collections {

    private Collections() {
    }

    /**
     * A bounded {@link Enumeration} over {@code c}'s elements, like {@code Collections.enumeration(c)}.
     * Iterates by index over a snapshot of the source — concrete backing, no interface dispatch.
     */
    @BmcModelConforms("differential (EnumerationConformanceTest) + @BmcProof (proofs.enumeration)")
    public static <T> Enumeration<T> enumeration(Collection<T> c) {
        return new ArrayEnumeration<>(c);
    }

    // --- empty / singleton / nCopies producers --------------------------------------------------
    // The JDK returns dedicated immutable types; the bounded model returns its own (mutable) backing
    // collection holding the right elements, which is all a proof observes (it reads, doesn't mutate).

    @BmcModelConforms("differential (CollectionsConformanceTest): emptyList() -> empty list")
    public static <T> List<T> emptyList() {
        return new ArrayList<>();
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): emptySet() -> empty set")
    public static <T> Set<T> emptySet() {
        return new HashSet<>();
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): emptyMap() -> empty map")
    public static <K, V> Map<K, V> emptyMap() {
        return new HashMap<>();
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): singletonList(T) -> one-element list")
    public static <T> List<T> singletonList(T o) {
        ArrayList<T> l = new ArrayList<>();
        l.add(o);
        return l;
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): singleton(T) -> one-element set")
    public static <T> Set<T> singleton(T o) {
        HashSet<T> s = new HashSet<>();
        s.add(o);
        return s;
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): singletonMap(K, V) -> one-entry map")
    public static <K, V> Map<K, V> singletonMap(K key, V value) {
        HashMap<K, V> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    /** A list of {@code n} copies of {@code o}; {@code n} must be {@code >= 0} (JDK: IAE otherwise). */
    @BmcModelConforms("differential (CollectionsConformanceTest): nCopies(int, T) -> n-element list")
    public static <T> List<T> nCopies(int n, T o) {
        if (n < 0) {
            throw new IllegalArgumentException("List length = " + n);
        }
        ArrayList<T> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(o);
        }
        return l;
    }

    // --- in-place list edits (index-driven over the concrete List model) ------------------------

    @BmcModelConforms("differential (CollectionsConformanceTest): reverse(List) in place")
    public static void reverse(List<?> list) {
        reverseHelper(list);
    }

    @SuppressWarnings("unchecked")
    private static <T> void reverseHelper(List<T> list) {
        int size = list.size();
        for (int i = 0, mid = size >> 1, j = size - 1; i < mid; i++, j--) {
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): swap(List, int, int) in place")
    public static void swap(List<?> list, int i, int j) {
        swapHelper(list, i, j);
    }

    @SuppressWarnings("unchecked")
    private static <T> void swapHelper(List<T> list, int i, int j) {
        T tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): fill(List, T) — set every position to obj")
    public static <T> void fill(List<? super T> list, T obj) {
        int size = list.size();
        for (int i = 0; i < size; i++) {
            list.set(i, obj);
        }
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): copy(List dest, List src) — overwrite dest[0..src.size)")
    public static <T> void copy(List<? super T> dest, List<? extends T> src) {
        int srcSize = src.size();
        if (srcSize > dest.size()) {
            throw new IndexOutOfBoundsException("Source does not fit in dest");
        }
        for (int i = 0; i < srcSize; i++) {
            dest.set(i, src.get(i));
        }
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): rotate(List, distance) in place")
    public static void rotate(List<?> list, int distance) {
        rotateHelper(list, distance);
    }

    @SuppressWarnings("unchecked")
    private static <T> void rotateHelper(List<T> list, int distance) {
        int size = list.size();
        if (size == 0) {
            return;
        }
        int d = distance % size;
        if (d < 0) {
            d += size;
        }
        if (d == 0) {
            return;
        }
        // Triple-reverse rotation (the JDK's "small list" algorithm), in place via get/set.
        // reverse [0,size), then [0,d), then [d,size) — leaves each element shifted right by d.
        reverseRange(list, 0, size);
        reverseRange(list, 0, d);
        reverseRange(list, d, size);
    }

    private static <T> void reverseRange(List<T> list, int from, int to) {
        int i = from, j = to - 1;
        while (i < j) {
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
            i++;
            j--;
        }
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): replaceAll(List, oldVal, newVal) — true if changed")
    public static <T> boolean replaceAll(List<T> list, T oldVal, T newVal) {
        boolean changed = false;
        int size = list.size();
        for (int i = 0; i < size; i++) {
            T e = list.get(i);
            if (oldVal == null ? e == null : oldVal.equals(e)) {
                list.set(i, newVal);
                changed = true;
            }
        }
        return changed;
    }

    // --- collection queries (iterator/element-equality over the concrete backing) ---------------

    @BmcModelConforms("differential (CollectionsConformanceTest): frequency(Collection, o) — count of equal elements")
    public static int frequency(Collection<?> c, Object o) {
        int count = 0;
        for (Object e : c) {
            if (o == null ? e == null : o.equals(e)) {
                count++;
            }
        }
        return count;
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): disjoint(Collection, Collection) — no shared element")
    public static boolean disjoint(Collection<?> c1, Collection<?> c2) {
        for (Object e : c1) {
            if (c2.contains(e)) {
                return false;
            }
        }
        return true;
    }

    @SafeVarargs
    @BmcModelConforms("differential (CollectionsConformanceTest): addAll(Collection, T...) — append all; true if any added")
    public static <T> boolean addAll(Collection<? super T> c, T... elements) {
        boolean changed = false;
        for (T e : elements) {
            if (c.add(e)) {
                changed = true;
            }
        }
        return changed;
    }

    // --- natural-order min / max / binarySearch / sort (Comparable elements, NO comparator) -----
    // compareTo is a plain virtual call on the concrete element (boxed primitive / String), never a
    // Comparator devirt — the Comparator-taking twins stay loud in the tail.

    @BmcModelConforms("differential (CollectionsConformanceTest): max(Collection) natural order")
    public static <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll) {
        Iterator<? extends T> i = coll.iterator();
        T candidate = i.next();   // empty -> NoSuchElementException, like the JDK
        while (i.hasNext()) {
            T next = i.next();
            if (next.compareTo(candidate) > 0) {
                candidate = next;
            }
        }
        return candidate;
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): min(Collection) natural order")
    public static <T extends Object & Comparable<? super T>> T min(Collection<? extends T> coll) {
        Iterator<? extends T> i = coll.iterator();
        T candidate = i.next();
        while (i.hasNext()) {
            T next = i.next();
            if (next.compareTo(candidate) < 0) {
                candidate = next;
            }
        }
        return candidate;
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): sort(List) natural order (extract / insertion sort / write back)")
    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        int size = list.size();
        // insertion sort in place via get/set, bounded by size — natural order, no comparator.
        for (int i = 1; i < size; i++) {
            T key = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).compareTo(key) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): binarySearch(List, key) natural order, sorted-assume")
    public static <T> int binarySearch(List<? extends Comparable<? super T>> list, T key) {
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Comparable<? super T> midVal = list.get(mid);
            int cmp = midVal.compareTo(key);
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

    // --- subList search / Enumeration drain (bounded, index/iterator over the concrete backing) ---

    @BmcModelConforms("differential (CollectionsConformanceTest): indexOfSubList(source, target) first match index, or -1")
    public static int indexOfSubList(List<?> source, List<?> target) {
        int sourceSize = source.size();
        int targetSize = target.size();
        int max = sourceSize - targetSize;
        for (int candidate = 0; candidate <= max; candidate++) {
            boolean match = true;
            for (int i = 0, j = candidate; i < targetSize; i++, j++) {
                if (!eq(target.get(i), source.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return candidate;
            }
        }
        return -1;
    }

    @BmcModelConforms("differential (CollectionsConformanceTest): lastIndexOfSubList(source, target) last match index, or -1")
    public static int lastIndexOfSubList(List<?> source, List<?> target) {
        int sourceSize = source.size();
        int targetSize = target.size();
        int max = sourceSize - targetSize;
        for (int candidate = max; candidate >= 0; candidate--) {
            boolean match = true;
            for (int i = 0, j = candidate; i < targetSize; i++, j++) {
                if (!eq(target.get(i), source.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return candidate;
            }
        }
        return -1;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Drain an {@link Enumeration} into a list, like {@code Collections.list(e)} (bounded by the source). */
    @BmcModelConforms("differential (CollectionsConformanceTest): list(Enumeration) -> ArrayList drain")
    public static <T> ArrayList<T> list(Enumeration<T> e) {
        ArrayList<T> l = new ArrayList<>();
        while (e.hasMoreElements()) {
            l.add(e.nextElement());
        }
        return l;
    }

    // --- loud walls (honest UNKNOWN under JBMC if reached) -------------------------------------------
    // Comparator-driven ops (devirt through the Comparator interface), the seeded shuffle (RNG
    // reproducibility — the Random wall), the wrapper views (need a wrapper class over the backing),
    // and the exotic empty-iterator / navigable / sorted / sequenced factories.

    @BmcUnmodelable(reason = "comparator-driven sort — devirt through the Comparator interface")
    public static <T> void sort(List<T> list, Comparator<? super T> c) {
        throw fail("bmc4j: unmodelled member java.util.Collections.sort(java.util.List, java.util.Comparator)"
                + " — a comparator-driven sort devirts through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven max — Comparator devirt")
    public static <T> T max(Collection<? extends T> coll, Comparator<? super T> comp) {
        throw fail("bmc4j: unmodelled member java.util.Collections.max(java.util.Collection, "
                + "java.util.Comparator) — comparator devirt through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven min — Comparator devirt")
    public static <T> T min(Collection<? extends T> coll, Comparator<? super T> comp) {
        throw fail("bmc4j: unmodelled member java.util.Collections.min(java.util.Collection, "
                + "java.util.Comparator) — comparator devirt through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven binarySearch — Comparator devirt")
    public static <T> int binarySearch(List<? extends T> list, T key, Comparator<? super T> c) {
        throw fail("bmc4j: unmodelled member java.util.Collections.binarySearch(java.util.List, "
                + "java.lang.Object, java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "reverse-order Comparator — devirt through the Comparator interface")
    public static <T> Comparator<T> reverseOrder() {
        throw fail("bmc4j: unmodelled member java.util.Collections.reverseOrder() — a returned Comparator "
                + "devirts through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "reverse-order Comparator wrapper — Comparator devirt")
    public static <T> Comparator<T> reverseOrder(Comparator<T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Collections.reverseOrder(java.util.Comparator) — a "
                + "returned Comparator devirts through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "shuffle permutes via an unseeded RNG — nondet permutation, no sound model")
    public static void shuffle(List<?> list) {
        throw fail("bmc4j: unmodelled member java.util.Collections.shuffle(java.util.List) — a nondet "
                + "permutation over an RNG has no sound bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "seeded shuffle — RNG reproducibility, the Random(long) wall")
    public static void shuffle(List<?> list, Random rnd) {
        throw fail("bmc4j: unmodelled member java.util.Collections.shuffle(java.util.List, java.util.Random)"
                + " — seeded RNG reproducibility, the Random wall; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "seeded shuffle over a RandomGenerator — RNG reproducibility wall")
    public static void shuffle(List<?> list, RandomGenerator rnd) {
        throw fail("bmc4j: unmodelled member java.util.Collections.shuffle(java.util.List, "
                + "java.util.random.RandomGenerator) — seeded RNG reproducibility, the Random wall; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> Collection<T> unmodifiableCollection(Collection<? extends T> c) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableCollection(java.util.Collection)"
                + " — a wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> List<T> unmodifiableList(List<? extends T> list) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableList(java.util.List) — a "
                + "wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> Set<T> unmodifiableSet(Set<? extends T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableSet(java.util.Set) — a "
                + "wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableMap(java.util.Map) — a "
                + "wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> SortedSet<T> unmodifiableSortedSet(SortedSet<T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableSortedSet(java.util.SortedSet)"
                + " — a wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <K, V> SortedMap<K, V> unmodifiableSortedMap(SortedMap<K, ? extends V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableSortedMap(java.util.SortedMap)"
                + " — a wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> NavigableSet<T> unmodifiableNavigableSet(NavigableSet<T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableNavigableSet("
                + "java.util.NavigableSet) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <K, V> NavigableMap<K, V> unmodifiableNavigableMap(NavigableMap<K, ? extends V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableNavigableMap("
                + "java.util.NavigableMap) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> SequencedCollection<T> unmodifiableSequencedCollection(SequencedCollection<? extends T> c) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableSequencedCollection("
                + "java.util.SequencedCollection) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <T> SequencedSet<T> unmodifiableSequencedSet(SequencedSet<? extends T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableSequencedSet("
                + "java.util.SequencedSet) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unmodifiable wrapper view — needs a wrapper class over the backing")
    public static <K, V> SequencedMap<K, V> unmodifiableSequencedMap(SequencedMap<K, ? extends V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.unmodifiableSequencedMap("
                + "java.util.SequencedMap) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <T> Collection<T> synchronizedCollection(Collection<T> c) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedCollection(java.util.Collection)"
                + " — a wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <T> List<T> synchronizedList(List<T> list) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedList(java.util.List) — a "
                + "wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <T> Set<T> synchronizedSet(Set<T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedSet(java.util.Set) — a "
                + "wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <K, V> Map<K, V> synchronizedMap(Map<K, V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedMap(java.util.Map) — a "
                + "wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <T> SortedSet<T> synchronizedSortedSet(SortedSet<T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedSortedSet(java.util.SortedSet)"
                + " — a wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <K, V> SortedMap<K, V> synchronizedSortedMap(SortedMap<K, V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedSortedMap(java.util.SortedMap)"
                + " — a wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <T> NavigableSet<T> synchronizedNavigableSet(NavigableSet<T> s) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedNavigableSet("
                + "java.util.NavigableSet) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "synchronized wrapper view — needs a wrapper class over the backing")
    public static <K, V> NavigableMap<K, V> synchronizedNavigableMap(NavigableMap<K, V> m) {
        throw fail("bmc4j: unmodelled member java.util.Collections.synchronizedNavigableMap("
                + "java.util.NavigableMap) — a wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <E> Collection<E> checkedCollection(Collection<E> c, Class<E> type) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedCollection(java.util.Collection, "
                + "java.lang.Class) — a checked wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <E> Queue<E> checkedQueue(Queue<E> queue, Class<E> type) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedQueue(java.util.Queue, "
                + "java.lang.Class) — a checked wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <E> List<E> checkedList(List<E> list, Class<E> type) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedList(java.util.List, "
                + "java.lang.Class) — a checked wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <E> Set<E> checkedSet(Set<E> s, Class<E> type) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedSet(java.util.Set, java.lang.Class)"
                + " — a checked wrapper view needs a wrapper class over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <K, V> Map<K, V> checkedMap(Map<K, V> m, Class<K> keyType, Class<V> valueType) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedMap(java.util.Map, java.lang.Class,"
                + " java.lang.Class) — a checked wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <E> SortedSet<E> checkedSortedSet(SortedSet<E> s, Class<E> type) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedSortedSet(java.util.SortedSet, "
                + "java.lang.Class) — a checked wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <K, V> SortedMap<K, V> checkedSortedMap(SortedMap<K, V> m, Class<K> keyType, Class<V> valueType) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedSortedMap(java.util.SortedMap, "
                + "java.lang.Class, java.lang.Class) — a checked wrapper view needs a wrapper class over the "
                + "backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <E> NavigableSet<E> checkedNavigableSet(NavigableSet<E> s, Class<E> type) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedNavigableSet(java.util.NavigableSet,"
                + " java.lang.Class) — a checked wrapper view needs a wrapper class over the backing; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "dynamically-typed checked wrapper view — needs a wrapper class over the backing")
    public static <K, V> NavigableMap<K, V> checkedNavigableMap(NavigableMap<K, V> m, Class<K> keyType, Class<V> valueType) {
        throw fail("bmc4j: unmodelled member java.util.Collections.checkedNavigableMap(java.util.NavigableMap,"
                + " java.lang.Class, java.lang.Class) — a checked wrapper view needs a wrapper class over the "
                + "backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty Iterator factory — exotic empty-iterator device")
    public static <T> Iterator<T> emptyIterator() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptyIterator() — an exotic empty-iterator"
                + " device; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty ListIterator factory — exotic empty-iterator device")
    public static <T> ListIterator<T> emptyListIterator() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptyListIterator() — an exotic "
                + "empty-iterator device; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty Enumeration factory — exotic empty-enumeration device")
    public static <T> Enumeration<T> emptyEnumeration() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptyEnumeration() — an exotic "
                + "empty-enumeration device; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty NavigableSet factory — exotic empty navigable view")
    public static <E> NavigableSet<E> emptyNavigableSet() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptyNavigableSet() — an exotic empty "
                + "navigable view; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty SortedSet factory — exotic empty sorted view")
    public static <E> SortedSet<E> emptySortedSet() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptySortedSet() — an exotic empty sorted "
                + "view; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty NavigableMap factory — exotic empty navigable view")
    public static <K, V> NavigableMap<K, V> emptyNavigableMap() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptyNavigableMap() — an exotic empty "
                + "navigable view; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "empty SortedMap factory — exotic empty sorted view")
    public static <K, V> SortedMap<K, V> emptySortedMap() {
        throw fail("bmc4j: unmodelled member java.util.Collections.emptySortedMap() — an exotic empty sorted "
                + "view; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "Set view backed by a Map — needs a view wrapper over the backing map")
    public static <E> Set<E> newSetFromMap(Map<E, Boolean> map) {
        throw fail("bmc4j: unmodelled member java.util.Collections.newSetFromMap(java.util.Map) — a Set view "
                + "backed by a Map needs a view wrapper over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "SequencedSet view backed by a SequencedMap — needs a view wrapper over the backing")
    public static <E> SequencedSet<E> newSequencedSetFromMap(SequencedMap<E, Boolean> map) {
        throw fail("bmc4j: unmodelled member java.util.Collections.newSequencedSetFromMap("
                + "java.util.SequencedMap) — a SequencedSet view backed by a SequencedMap needs a view "
                + "wrapper over the backing; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "Deque-as-LIFO-queue view — needs a view wrapper over the backing deque")
    public static <T> Queue<T> asLifoQueue(Deque<T> deque) {
        throw fail("bmc4j: unmodelled member java.util.Collections.asLifoQueue(java.util.Deque) — a LIFO "
                + "queue view over a Deque needs a view wrapper over the backing; honestly UNKNOWN");
    }

    /**
     * Concrete, fixed-capacity {@link Enumeration} backed by an element array filled from the source
     * collection's iterator at construction time. {@code nextElement} advances a cursor over that array
     * and throws {@link NoSuchElementException} past the end (JDK semantics).
     */
    private static final class ArrayEnumeration<T> implements Enumeration<T> {

        private static final int CAPACITY = 64;

        private final Object[] elements = new Object[CAPACITY];
        private final int count;
        private int cursor;

        ArrayEnumeration(Collection<T> c) {
            int n = 0;
            for (T e : c) {
                elements[n++] = e;
            }
            this.count = n;
        }

        @Override
        public boolean hasMoreElements() {
            return cursor < count;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T nextElement() {
            if (cursor >= count) {
                throw new NoSuchElementException();
            }
            return (T) elements[cursor++];
        }
    }
}
