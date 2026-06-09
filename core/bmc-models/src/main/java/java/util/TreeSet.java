package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.TreeSet}, composed over the existing {@link TreeMap} model: a TreeSet
 * is the key set of a map whose values are an ignored dummy, exactly as the JDK implements it (and as
 * this codebase's {@link HashSet} model is composed over {@link HashMap}). All set behavior delegates to
 * the backing {@code TreeMap<E, Object>}, so the bounded, collision-dense storage and the navigable
 * surface (first/last/ceiling/floor/higher/lower/pollFirst/pollLast/descendingIterator) inherit the
 * map's sound, on-demand sorted scan over the live key set with no separate ordered structure.
 *
 * <p>Composition, not subclassing: a subclassed collection model leaves {@code size()} etc. unresolved
 * under JBMC, so every set op is an explicit delegating override here.
 *
 * <p><b>Contract divergences — MIRRORED from the {@link TreeMap} model, not invented (a TreeSet is a
 * TreeMap key set, so its contract divergences are exactly the backing map's):</b>
 * <ul>
 *   <li><b>Natural ordering only.</b> This models the no-comparator TreeSet: elements are compared via
 *       {@link Comparable#compareTo} and {@code comparator()} returns {@code null}, like the JDK's
 *       natural-ordering TreeSet. The comparator-taking constructor is out of scope (tail).</li>
 *   <li><b>Ordering by the SIGN of {@code compareTo}</b> (not by {@code equals}/{@code hashCode}): a
 *       TreeSet considers two elements equal iff {@code compareTo} returns 0, inherited from the
 *       backing map's key comparisons. The differential suite compares ordering by sign, honoring this.</li>
 *   <li><b>Null handling.</b> The backing array-backed map does not reproduce the JDK's null-element
 *       {@link NullPointerException}; like the TreeMap model, the differential suite drives non-null
 *       elements, so this divergence is documented and unexercised rather than "fixed" to a stricter
 *       contract.</li>
 *   <li><b>Empty-set exception/null split</b> matches the JDK and the backing map: {@code first}/
 *       {@code last} throw {@link NoSuchElementException} when empty; the ceiling/floor/higher/lower
 *       family and {@code pollFirst}/{@code pollLast} return {@code null} when no element qualifies.</li>
 * </ul>
 *
 * <p>The multi-element range views ({@code subSet}/{@code headSet}/{@code tailSet}, in both the 1-arg
 * SortedSet and boolean-inclusive NavigableSet overloads) are live views over a bounded unordered store —
 * out of scope for the same reason the TreeMap model stubs {@code subMap}/{@code headMap}/{@code tailMap}.
 * Together with {@code spliterator()} and {@code toArray(IntFunction)} they are accounted for per-member
 * as method-level loud {@link BmcUnmodelable} stubs; there is no {@code @BmcModelTail} remainder.
 */
public class TreeSet<E> implements Set<E> {

    /** A TreeSet is the key set of a map to a single dummy value, like the JDK's {@code PRESENT}. */
    private static final Object PRESENT = new Object();

    private final TreeMap<E, Object> map = new TreeMap<>();

    public TreeSet() {
    }

    /**
     * Copy constructor: a new sorted set of {@code c}'s distinct elements (dedup via {@code compareTo}),
     * like {@code new TreeSet<>(collection)}. The distinct count is bounded by the backing map capacity.
     */
    public TreeSet(Collection<? extends E> c) {
        for (E e : c) {
            add(e);
        }
    }

    // --- core Set surface (delegating to the backing TreeMap) -------------------------------------

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public int size() {
        return map.size();
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean add(E e) {
        return map.put(e, PRESENT) == null;
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public void clear() {
        map.clear();
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public Iterator<E> iterator() {
        return sortedKeys(false).iterator();
    }

    // --- bulk / functional ops (delegating; dedup + ordering inherited from the backing map) ------

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            if (add(e)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object o : c) {
            if (remove(o)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        ArrayList<E> snapshot = sortedKeys(false);
        for (E e : snapshot) {
            if (!c.contains(e)) {
                remove(e);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        boolean changed = false;
        ArrayList<E> snapshot = sortedKeys(false);
        for (E e : snapshot) {
            if (filter.test(e)) {
                remove(e);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public void forEach(java.util.function.Consumer<? super E> action) {
        for (E e : sortedKeys(false)) {
            action.accept(e);
        }
    }

    @Override
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public java.util.stream.Stream<E> stream() {
        return new java.util.stream.ListStream<>(sortedKeys(false));
    }

    /** Sequential BMC has one thread, so a parallel stream is observably the sequential {@link #stream()}. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public java.util.stream.Stream<E> parallelStream() {
        return stream();
    }

    // --- SortedSet / NavigableSet single-element navigation (delegating to the backing map) -------
    // Each is a total function of the element set + natural ordering, derived from the map's bounded
    // sorted scan, so it matches the JDK exactly. comparator() is null (natural ordering). first/last
    // throw NoSuchElementException when empty; the ceiling/floor/higher/lower family and pollFirst/
    // pollLast return null when no element qualifies — the backing map's documented contract.

    /** Natural ordering only (no explicit comparator), so this is always {@code null}, like the JDK. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public Comparator<? super E> comparator() {
        return map.comparator();
    }

    /** The least (lowest) element; throws {@link NoSuchElementException} when empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E first() {
        return map.firstKey();
    }

    /** The greatest (highest) element; throws {@link NoSuchElementException} when empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E last() {
        return map.lastKey();
    }

    /** Least element &gt;= {@code e} (inclusive lower bound), or {@code null} if none. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E ceiling(E e) {
        return map.ceilingKey(e);
    }

    /** Greatest element &lt;= {@code e} (inclusive upper bound), or {@code null} if none. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E floor(E e) {
        return map.floorKey(e);
    }

    /** Least element strictly &gt; {@code e}, or {@code null} if none. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E higher(E e) {
        return map.higherKey(e);
    }

    /** Greatest element strictly &lt; {@code e}, or {@code null} if none. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E lower(E e) {
        return map.lowerKey(e);
    }

    /** Removes and returns the least element, or {@code null} when the set is empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E pollFirst() {
        Map.Entry<E, Object> e = map.pollFirstEntry();
        return e == null ? null : e.getKey();
    }

    /** Removes and returns the greatest element, or {@code null} when the set is empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public E pollLast() {
        Map.Entry<E, Object> e = map.pollLastEntry();
        return e == null ? null : e.getKey();
    }

    /** Iterator over the elements in DESCENDING order (the reverse of {@link #iterator()}). */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.treeset)")
    public Iterator<E> descendingIterator() {
        return sortedKeys(true).iterator();
    }

    // --- bounded sorted snapshot over the live key set --------------------------------------------
    // The backing map stores keys unordered, so each ordering-sensitive op takes a single sorted
    // snapshot of the live key set (ascending, or descending when reversed=true). The selection-sort
    // loop unwinds to the current size, like every other array-backed scan in these models. Sorting is
    // by Comparable — natural ordering, like the JDK's no-comparator TreeSet.
    @SuppressWarnings("unchecked")
    private ArrayList<E> sortedKeys(boolean descending) {
        ArrayList<E> keys = new ArrayList<>();
        for (E k : map.keySet()) {
            keys.add(k);
        }
        int n = keys.size();
        for (int i = 0; i < n; i++) {
            int sel = i;
            for (int j = i + 1; j < n; j++) {
                int c = ((Comparable<? super E>) keys.get(j)).compareTo(keys.get(sel));
                if (descending ? c > 0 : c < 0) {
                    sel = j;
                }
            }
            if (sel != i) {
                E tmp = keys.get(i);
                keys.set(i, keys.get(sel));
                keys.set(sel, tmp);
            }
        }
        return keys;
    }

    // --- SequencedCollection ends (Java 21+) over the SORTED order --------------------------------
    // For a sorted set, the first/last in encounter order ARE the least/greatest, so getFirst/getLast
    // delegate to first/last and removeFirst/removeLast to pollFirst/pollLast (which throw on empty via
    // first()/last() — JDK getFirst/getLast throw NoSuchElementException on an empty sorted set). The
    // JDK rejects explicit positioning on a sorted set: addFirst/addLast throw UnsupportedOperationException.

    @BmcModelConforms("differential (SetConformanceTest): getFirst() == first() (least element)")
    public E getFirst() {
        return first();
    }

    @BmcModelConforms("differential (SetConformanceTest): getLast() == last() (greatest element)")
    public E getLast() {
        return last();
    }

    @BmcModelConforms("differential (SetConformanceTest): removeFirst() throws when empty, else removes the least")
    public E removeFirst() {
        E e = first();   // throws NoSuchElementException when empty, like the JDK
        remove(e);
        return e;
    }

    @BmcModelConforms("differential (SetConformanceTest): removeLast() throws when empty, else removes the greatest")
    public E removeLast() {
        E e = last();
        remove(e);
        return e;
    }

    @BmcModelConforms("differential (SetConformanceTest): addFirst throws UnsupportedOperationException (sorted set rejects positioning)")
    public void addFirst(E e) {
        throw new UnsupportedOperationException();
    }

    @BmcModelConforms("differential (SetConformanceTest): addLast throws UnsupportedOperationException (sorted set rejects positioning)")
    public void addLast(E e) {
        throw new UnsupportedOperationException();
    }

    /**
     * A bounded snapshot of the elements in DESCENDING order (NavigableSet, Java 21+). The JDK returns
     * a live view; the model returns an independent insertion-ordered {@code LinkedHashSet} populated in
     * descending order (so its iteration order IS descending — a re-sorting {@code TreeSet} snapshot
     * would lose the reversal). Sound for read-only / build-then-read proofs. Returns the model
     * {@code Set} type (the audit matches by name+params, return-agnostic).
     */
    @BmcModelConforms("differential (SetConformanceTest): descendingSet() bounded snapshot in descending order")
    public Set<E> descendingSet() {
        LinkedHashSet<E> out = new LinkedHashSet<>();
        for (E e : sortedKeys(true)) {
            out.add(e);
        }
        return out;
    }

    /** SequencedCollection {@code reversed()} — same descending snapshot as {@link #descendingSet()}. */
    @BmcModelConforms("differential (SetConformanceTest): reversed() == descendingSet() bounded snapshot")
    public Set<E> reversed() {
        return descendingSet();
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ------------------

    @BmcUnmodelable(reason = "bulk membership — compose contains() explicitly")
    public boolean containsAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.containsAll(java.util.Collection) — bulk membership — compose contains() explicitly");
    }

    @BmcUnmodelable(reason = "NavigableSet range view over a bounded unordered store — out of scope (mirrors TreeMap.subMap); loud under JBMC")
    public SortedSet<E> subSet(E fromElement, E toElement) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.subSet(java.lang.Object,java.lang.Object) — NavigableSet range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "NavigableSet range view over a bounded unordered store — out of scope (mirrors TreeMap.headMap); loud under JBMC")
    public SortedSet<E> headSet(E toElement) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.headSet(java.lang.Object) — NavigableSet range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "NavigableSet range view over a bounded unordered store — out of scope (mirrors TreeMap.tailMap); loud under JBMC")
    public SortedSet<E> tailSet(E fromElement) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.tailSet(java.lang.Object) — NavigableSet range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "boolean-inclusive NavigableSet range view over a bounded unordered store — out of scope (mirrors the 2-arg subSet / TreeMap.subMap); loud under JBMC")
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.subSet(java.lang.Object,boolean,java.lang.Object,boolean) — boolean-inclusive NavigableSet range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "boolean-inclusive NavigableSet range view over a bounded unordered store — out of scope (mirrors the 1-arg headSet); loud under JBMC")
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.headSet(java.lang.Object,boolean) — boolean-inclusive NavigableSet range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "boolean-inclusive NavigableSet range view over a bounded unordered store — out of scope (mirrors the 1-arg tailSet); loud under JBMC")
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.tailSet(java.lang.Object,boolean) — boolean-inclusive NavigableSet range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "Spliterator (parallel-decomposition) view is out of scope for the sequential bounded model — iterate the model instead; loud under JBMC")
    public Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.spliterator() — Spliterator (parallel-decomposition) view is out of scope for the sequential bounded model — iterate the model instead");
    }

    @BmcUnmodelable(reason = "array snapshot — iterate the model instead")
    public Object[] toArray() {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.toArray() — array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "typed array snapshot via a generator — iterate the model instead; loud under JBMC")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.toArray(java.util.function.IntFunction) — typed array snapshot via a generator — iterate the model instead");
    }

    @BmcUnmodelable(reason = "shallow copy of a bounded model — construct a fresh set from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.TreeSet.clone() — shallow copy of a bounded model — construct a fresh set from the elements instead");
    }
}
