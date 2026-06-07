package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

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
 * from the same bounded scan. The comparator-taking constructor and the multi-key range/bulk views
 * (sub/head/tail-map, descendingMap/descendingKeySet/navigableKeySet) are out of scope (tail; loud
 * under JBMC).
 */
@BmcModelTail(reason = "NavigableMap/SortedMap range-view and bulk-navigation surface (headMap/tailMap/subMap/descendingMap/descendingKeySet/navigableKeySet) and the comparator-taking constructor — range views over a bounded unordered store are out of scope; all loud under JBMC")
public class TreeMap<K, V> extends HashMap<K, V> {

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
