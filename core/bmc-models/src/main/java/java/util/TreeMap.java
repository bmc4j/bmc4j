package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.TreeMap} over the inherited array-backed {@link HashMap} storage,
 * plus the {@link java.util.NavigableMap} navigation surface computed by a bounded sorted scan of
 * the keys. The unordered functional results (get/put/containsKey/remove/compute/…) match the JDK
 * exactly via the inherited model; the navigation ops (firstKey/lastKey/firstEntry/lastEntry/
 * ceilingKey/floorKey/higherKey/lowerKey) are derived on demand from the live key set, so they need
 * no ordered backing structure and stay sound under BMC.
 *
 * <p>This models the <b>natural-ordering</b> TreeMap (no explicit comparator): keys are compared via
 * {@link Comparable#compareTo}, exactly as the JDK does when constructed without a comparator, so
 * {@code comparator()} returns {@code null}. A non-Comparable key trips {@code ClassCastException}
 * at the first comparison, like the JDK. Exception semantics on an empty map match the JDK:
 * {@code firstKey}/{@code lastKey} throw {@link NoSuchElementException}; {@code firstEntry}/
 * {@code lastEntry} and the ceiling/floor/higher/lower family return {@code null} when no qualifying
 * key exists. The single-key navigation entry family (ceilingEntry/floorEntry/higherEntry/
 * lowerEntry) and the poll-extreme ops (pollFirstEntry/pollLastEntry) are modeled here too, derived
 * from the same bounded scan. The bulk descending/ascending snapshots (descendingMap/descendingKeySet/
 * navigableKeySet) are modeled as bounded sorted snapshots. The multi-key RANGE views (sub/head/tail-map
 * in both the 1-arg SortedMap and boolean-inclusive NavigableMap overloads) and the SequencedMap defaults
 * (reversed/sequenced*, which would iterate in the model's hash-backed encounter order rather than key
 * order) are out of scope — accounted for per-member as method-level loud {@link BmcUnmodelable} stubs
 * (loud-if-reached), alongside the inherited-HashMap loud re-declarations; there is no
 * {@code @BmcModelTail} remainder.
 */
public class TreeMap<K, V> extends HashMap<K, V> implements SortedMap<K, V> {

    public TreeMap() {
        super();
    }

    public TreeMap(Map<? extends K, ? extends V> m) {
        super(m);
    }

    /** Natural ordering only (no explicit comparator), so this is always {@code null}, like the JDK. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Comparator<? super K> comparator() {
        return null;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public K firstKey() {
        K min = extreme(true);
        if (min == null && isEmpty()) {
            throw new NoSuchElementException();
        }
        return min;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public K lastKey() {
        K max = extreme(false);
        if (max == null && isEmpty()) {
            throw new NoSuchElementException();
        }
        return max;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> firstEntry() {
        if (isEmpty()) {
            return null;
        }
        K k = extreme(true);
        return new Entry<>(k, get(k));
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> lastEntry() {
        if (isEmpty()) {
            return null;
        }
        K k = extreme(false);
        return new Entry<>(k, get(k));
    }

    /** Least key &gt;= {@code key} (inclusive lower bound), or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public K ceilingKey(K key) {
        return bound(key, true, true);
    }

    /** Greatest key &lt;= {@code key} (inclusive upper bound), or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public K floorKey(K key) {
        return bound(key, false, true);
    }

    /** Least key strictly &gt; {@code key}, or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public K higherKey(K key) {
        return bound(key, true, false);
    }

    /** Greatest key strictly &lt; {@code key}, or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public K lowerKey(K key) {
        return bound(key, false, false);
    }

    // --- entry-returning navigation (mirrors the *Key family) --------------------------------------
    // Each pairs the navigated key with its current value, or returns null when no key qualifies —
    // exactly the JDK's NavigableMap ceilingEntry/floorEntry/higherEntry/lowerEntry contract.

    /** Least entry whose key is &gt;= {@code key}, or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> ceilingEntry(K key) {
        return entryFor(bound(key, true, true));
    }

    /** Greatest entry whose key is &lt;= {@code key}, or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> floorEntry(K key) {
        return entryFor(bound(key, false, true));
    }

    /** Least entry whose key is strictly &gt; {@code key}, or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> higherEntry(K key) {
        return entryFor(bound(key, true, false));
    }

    /** Greatest entry whose key is strictly &lt; {@code key}, or {@code null} if none. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> lowerEntry(K key) {
        return entryFor(bound(key, false, false));
    }

    // --- poll: read-and-remove the extreme entry --------------------------------------------------
    // Returns the min/max entry and removes that mapping, or returns null on an empty map — the JDK's
    // NavigableMap pollFirstEntry/pollLastEntry contract. The snapshot entry is taken BEFORE removal,
    // so its key/value reflect the polled mapping.

    /** Removes and returns the least entry, or {@code null} when the map is empty. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> pollFirstEntry() {
        return pollExtreme(true);
    }

    /** Removes and returns the greatest entry, or {@code null} when the map is empty. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public Map.Entry<K, V> pollLastEntry() {
        return pollExtreme(false);
    }

    // --- SequencedMap positioning is unsupported on a sorted map ----------------------------------
    // A TreeMap orders by key, so it cannot honor an explicit front/back position: the JDK throws
    // UnsupportedOperationException. Modeled here so a proof over code that calls putFirst/putLast on a
    // (sorted) TreeMap sees the real failure, not a silent pass.

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public V putFirst(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.treemap)")
    public V putLast(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /** Snapshot the {@code key}'s current mapping, or {@code null} when {@code key} is null. */
    private Map.Entry<K, V> entryFor(K key) {
        if (key == null) {
            return null;
        }
        return new Entry<>(key, get(key));
    }

    /** Snapshot then remove the least ({@code wantMin}) or greatest entry; null on an empty map. */
    private Map.Entry<K, V> pollExtreme(boolean wantMin) {
        if (isEmpty()) {
            return null;
        }
        K k = extreme(wantMin);
        Entry<K, V> e = new Entry<>(k, get(k));
        remove(k);
        return e;
    }

    // --- bounded sorted scan over the live key set -------------------------------------------------
    // The keys are read from the inherited keySet() snapshot (HashMap storage is private) and compared
    // via Comparable — natural ordering, like the JDK's no-comparator TreeMap. Each query is a single
    // pass; the loop unwinds to the current size, like every other array-backed lookup in these models.

    /** The minimum (least, {@code wantMin}) or maximum key, or {@code null} when the map is empty. */
    @SuppressWarnings("unchecked")
    private K extreme(boolean wantMin) {
        K best = null;
        boolean seen = false;
        for (K k : keySet()) {
            if (!seen || (wantMin ? cmp(k, best) < 0 : cmp(k, best) > 0)) {
                best = k;
                seen = true;
            }
        }
        return best;
    }

    /**
     * The key bounding {@code key}: the least key on the high side ({@code higher == true}) or the
     * greatest on the low side, with {@code inclusive} deciding whether an exactly-equal key qualifies.
     * Returns {@code null} when no key satisfies the bound — the JDK's ceiling/floor/higher/lower
     * semantics.
     */
    private K bound(K key, boolean higher, boolean inclusive) {
        K best = null;
        boolean seen = false;
        for (K k : keySet()) {
            int c = cmp(k, key);
            boolean qualifies = higher ? (inclusive ? c >= 0 : c > 0) : (inclusive ? c <= 0 : c < 0);
            if (!qualifies) {
                continue;
            }
            // On the high side we want the SMALLEST qualifying key; on the low side the LARGEST.
            if (!seen || (higher ? cmp(k, best) < 0 : cmp(k, best) > 0)) {
                best = k;
                seen = true;
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private int cmp(K a, K b) {
        return ((Comparable<? super K>) a).compareTo(b);
    }

    // --- bounded sorted-key snapshot (ascending or descending) ------------------------------------
    // Selection sort over the live key-set snapshot, by natural ordering — the same scan TreeSet uses.
    // The loop unwinds to the current size, like every other array-backed model op.
    @SuppressWarnings("unchecked")
    private ArrayList<K> sortedKeys(boolean descending) {
        ArrayList<K> keys = new ArrayList<>();
        for (K k : keySet()) {
            keys.add(k);
        }
        int n = keys.size();
        for (int i = 0; i < n; i++) {
            int sel = i;
            for (int j = i + 1; j < n; j++) {
                int c = cmp(keys.get(j), keys.get(sel));
                if (descending ? c > 0 : c < 0) {
                    sel = j;
                }
            }
            if (sel != i) {
                K tmp = keys.get(i);
                keys.set(i, keys.get(sel));
                keys.set(sel, tmp);
            }
        }
        return keys;
    }

    // --- NavigableMap / SequencedMap bulk views: bounded snapshots --------------------------------
    // The JDK returns LIVE views; the model returns independent insertion-ordered snapshots populated
    // in the requested order (ascending for navigableKeySet/sequenced*; descending for descending*/
    // reversed), so the snapshot's own iteration order IS the requested order. Sound for read-only /
    // build-then-read proofs. Differential-only for the SequencedMap defaults (reversed/sequenced*) —
    // like LinkedHashMap, JBMC binds the real SequencedMap default over the override; pinned on the
    // differential axis (MapConformanceTest) where the override is honored on a real JVM. Return the
    // model Set/Map/Collection types (the audit matches by name+params, return-agnostic).

    @BmcModelConforms("differential (MapConformanceTest): navigableKeySet() ascending snapshot of the keys")
    public Set<K> navigableKeySet() {
        LinkedHashSet<K> out = new LinkedHashSet<>();
        for (K k : sortedKeys(false)) {
            out.add(k);
        }
        return out;
    }

    @BmcModelConforms("differential (MapConformanceTest): descendingKeySet() descending snapshot of the keys")
    public Set<K> descendingKeySet() {
        LinkedHashSet<K> out = new LinkedHashSet<>();
        for (K k : sortedKeys(true)) {
            out.add(k);
        }
        return out;
    }

    @BmcModelConforms("differential (MapConformanceTest): descendingMap() descending-key snapshot map")
    public Map<K, V> descendingMap() {
        LinkedHashMap<K, V> out = new LinkedHashMap<>();
        for (K k : sortedKeys(true)) {
            out.put(k, get(k));
        }
        return out;
    }

    // NOTE: the SequencedMap defaults (reversed/sequencedKeySet/sequencedValues/sequencedEntrySet) are
    // deliberately NOT overridden here. TreeMap implements SortedMap, which on Java 21+ extends
    // SequencedMap with covariant returns (SequencedSet/SequencedCollection/SequencedMap) that the
    // bounded model's plain Set/Collection/Map types cannot satisfy. The inherited JDK default iterates
    // in this model's HashMap-backed encounter order, NOT TreeMap's key order, so it would silently
    // diverge from the sorted contract. They are accounted for as class-level @BmcUnmodelable
    // (loud-if-reached) at the top of this class; the modeled descending/ascending snapshots
    // (descendingMap/navigableKeySet/descendingKeySet) are the supported alternatives.

    // --- SortedMap range views (out of scope): loud stubs ------------------------------------------
    // The model implements java.util.SortedMap so a `SortedMap`-typed result (e.g. the value of Kotlin's
    // `MapsKt.toSortedMap(map)`, or `(SortedMap) treeMap`) downcasts cleanly under JBMC instead of
    // havoc'ing to an under-constrained object. The single-key navigation surface (firstKey/lastKey/
    // ceiling/floor/… above) is fully modeled; only the multi-key RANGE views remain out of scope —
    // a range view over a bounded unordered store is not modeled. Reaching one is a loud UNKNOWN.

    @BmcUnmodelable(reason = "SortedMap range view over a bounded unordered store — out of scope; loud under JBMC")
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.subMap(java.lang.Object,java.lang.Object) — SortedMap range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "SortedMap range view over a bounded unordered store — out of scope; loud under JBMC")
    public SortedMap<K, V> headMap(K toKey) {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.headMap(java.lang.Object) — SortedMap range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "SortedMap range view over a bounded unordered store — out of scope; loud under JBMC")
    public SortedMap<K, V> tailMap(K fromKey) {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.tailMap(java.lang.Object) — SortedMap range view over a bounded unordered store; out of scope");
    }

    // --- boolean-inclusive NavigableMap range views (out of scope): loud stubs --------------------
    // Same wall as the 1-arg SortedMap range views above, with from/to inclusivity flags — a live range
    // view over a bounded unordered store is not modeled. Loud-if-reached under JBMC.

    @BmcUnmodelable(reason = "boolean-inclusive NavigableMap range view over a bounded unordered store — out of scope (mirrors the 2-arg subMap); loud under JBMC")
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.subMap(java.lang.Object,boolean,java.lang.Object,boolean) — boolean-inclusive NavigableMap range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "boolean-inclusive NavigableMap range view over a bounded unordered store — out of scope (mirrors the 1-arg headMap); loud under JBMC")
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.headMap(java.lang.Object,boolean) — boolean-inclusive NavigableMap range view over a bounded unordered store; out of scope");
    }

    @BmcUnmodelable(reason = "boolean-inclusive NavigableMap range view over a bounded unordered store — out of scope (mirrors the 1-arg tailMap); loud under JBMC")
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.tailMap(java.lang.Object,boolean) — boolean-inclusive NavigableMap range view over a bounded unordered store; out of scope");
    }

    // --- SequencedMap defaults (out of scope): loud stubs -----------------------------------------
    // The inherited SequencedMap default would iterate in the model's HashMap-backed encounter order, NOT
    // TreeMap's key order, so it would silently diverge from the sorted contract — loud-if-reached. Use
    // descendingMap()/navigableKeySet()/descendingKeySet() for the modeled ordered snapshots.

    @BmcUnmodelable(reason = "SequencedMap.reversed would iterate in HashMap encounter order — NOT TreeMap key order; unsound, loud under JBMC. Use descendingMap() for the modeled descending-key snapshot.")
    @Override
    public SortedMap<K, V> reversed() {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.reversed() — SequencedMap.reversed would iterate in HashMap encounter order, not TreeMap key order — unsound; use descendingMap()");
    }

    @BmcUnmodelable(reason = "SequencedMap.sequencedKeySet would iterate in HashMap encounter order, not TreeMap key order — unsound; loud under JBMC. Use navigableKeySet() for the modeled ascending snapshot.")
    public SequencedSet<K> sequencedKeySet() {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.sequencedKeySet() — would iterate in HashMap encounter order, not TreeMap key order — unsound; use navigableKeySet()");
    }

    @BmcUnmodelable(reason = "SequencedMap.sequencedValues would iterate in HashMap encounter order, not TreeMap key order — unsound; loud under JBMC.")
    public SequencedCollection<V> sequencedValues() {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.sequencedValues() — would iterate in HashMap encounter order, not TreeMap key order — unsound");
    }

    @BmcUnmodelable(reason = "SequencedMap.sequencedEntrySet would iterate in HashMap encounter order, not TreeMap key order — unsound; loud under JBMC.")
    public SequencedSet<Map.Entry<K, V>> sequencedEntrySet() {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.sequencedEntrySet() — would iterate in HashMap encounter order, not TreeMap key order — unsound");
    }

    // --- inherited-HashMap loud stub, re-declared here --------------------------------------------
    // The gate resolves inherited @BmcModelConforms through the model chain but NOT inherited method-level
    // stubs, so the HashMap model's clone() stub is re-declared here (same reason) so every real member is
    // accounted for. putAll/remove(key,value)/replaceAll are MODELED on the HashMap base (not stubs), so
    // they are inherited as conforming members and need no re-declaration. Loud-if-reached under JBMC.

    @BmcUnmodelable(reason = "shallow copy of a bounded model — construct a fresh map from the entries instead; loud under JBMC (inherited from the HashMap model stub)")
    @Override
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.TreeMap.clone() — shallow copy of a bounded model — construct a fresh map from the entries instead");
    }

    /** Immutable key/value pair returned by {@link #firstEntry()} / {@link #lastEntry()}. */
    private static final class Entry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }
    }
}
