package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.LinkedHashMap} — same array-backed behaviour as {@link HashMap}
 * (insertion order is preserved by the backing arrays). Because the backing is insertion-ordered,
 * the {@link java.util.SequencedMap} surface is modeled soundly here: {@code firstEntry}/
 * {@code lastEntry} read the ends of the insertion order, {@code pollFirstEntry}/{@code pollLastEntry}
 * read-and-remove them, {@code putFirst}/{@code putLast} (re)position an entry at an end exactly like
 * the JDK (a present key is moved), and {@code sequencedKeySet}/{@code sequencedValues}/
 * {@code sequencedEntrySet} are the order-preserving snapshots the inherited keySet/values/entrySet
 * already produce.
 *
 * <p>The access-order/eldest-entry LRU eviction hook ({@code removeEldestEntry}, paired with the
 * unmodeled {@code accessOrder} constructor) stays loud per-member — the insertion-ordered array has
 * no eviction policy. {@code reversed()} and the {@code newLinkedHashMap} presizing factory are modeled.
 */
public class LinkedHashMap<K, V> extends HashMap<K, V> {

    public LinkedHashMap() {
        super();
    }

    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }

    /**
     * Presizing factory ({@code LinkedHashMap.newLinkedHashMap(numMappings)}, Java 19+) — capacity is a
     * hint only, so the model returns a fresh empty map. Negative throws IllegalArgumentException, like
     * the JDK. (The inherited {@code newHashMap} is covered by the HashMap model.)
     */
    @BmcModelConforms("differential (MapConformanceTest): newLinkedHashMap(int) presizing factory -> empty map")
    public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int numMappings) {
        if (numMappings < 0) {
            throw new IllegalArgumentException("Negative number of mappings: " + numMappings);
        }
        return new LinkedHashMap<>();
    }

    // --- SequencedMap: ends of the insertion order -------------------------------------------------
    // Index 0 is the first-inserted (eldest) entry, index size-1 the last-inserted, since the backing
    // arrays preserve insertion order. Each query is a single bounded read.

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.linkedhashmap)")
    public Map.Entry<K, V> firstEntry() {
        if (isEmpty()) {
            return null;
        }
        return new SeqNode<>(keyAt(0), valueAt(0));
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.linkedhashmap)")
    public Map.Entry<K, V> lastEntry() {
        if (isEmpty()) {
            return null;
        }
        return new SeqNode<>(keyAt(size() - 1), valueAt(size() - 1));
    }

    /** Snapshot then remove the first-inserted entry; {@code null} on an empty map. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.linkedhashmap)")
    public Map.Entry<K, V> pollFirstEntry() {
        if (isEmpty()) {
            return null;
        }
        K k = keyAt(0);
        SeqNode<K, V> e = new SeqNode<>(k, valueAt(0));
        remove(k);
        return e;
    }

    /** Snapshot then remove the last-inserted entry; {@code null} on an empty map. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.linkedhashmap)")
    public Map.Entry<K, V> pollLastEntry() {
        if (isEmpty()) {
            return null;
        }
        K k = keyAt(size() - 1);
        SeqNode<K, V> e = new SeqNode<>(k, valueAt(size() - 1));
        remove(k);
        return e;
    }

    /** Position ({@code k},{@code v}) at the front; a present key is moved there (JDK semantics). */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.linkedhashmap)")
    public V putFirst(K k, V v) {
        return putAtFront(k, v);
    }

    /** Position ({@code k},{@code v}) at the back; a present key is moved there (JDK semantics). */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.linkedhashmap)")
    public V putLast(K k, V v) {
        return putAtBack(k, v);
    }

    // The sequenced views: the inherited keySet/values/entrySet already iterate the backing arrays in
    // insertion order, so the order-preserving sequenced snapshots are exactly those.
    //
    // Differential-only (no @BmcProof): these are declared on java.util.SequencedMap, which the real
    // JDK ships as DEFAULT methods on the analysis classpath. JBMC binds the call to that real
    // SequencedMap default instead of dispatching to this model override, and the default havocs the
    // returned view — so even `m.sequencedKeySet().size() == 2` is refutable under JBMC (counterexample
    // injects an out-of-range size). This is the same dynamic-dispatch/devirtualization artifact the
    // TreeMap sorted-navigation tail documents, not a model defect: the very same view body reached via
    // the modeled Map.keySet()/values()/entrySet() (declared in the bmc4j Map interface) verifies green.
    // The behaviour is pinned on the differential axis (MapConformanceTest), which runs the model on a
    // real JVM where the override is honored.

    @BmcModelConforms("differential (MapConformanceTest) — not @BmcProof: JBMC binds the real SequencedMap default over this override and havocs the view (devirtualization artifact)")
    public Set<K> sequencedKeySet() {
        return keySet();
    }

    @BmcModelConforms("differential (MapConformanceTest) — not @BmcProof: JBMC binds the real SequencedMap default over this override and havocs the view (devirtualization artifact)")
    public Collection<V> sequencedValues() {
        return values();
    }

    @BmcModelConforms("differential (MapConformanceTest) — not @BmcProof: JBMC binds the real SequencedMap default over this override and havocs the view (devirtualization artifact)")
    public Set<Map.Entry<K, V>> sequencedEntrySet() {
        return entrySet();
    }

    /**
     * A bounded snapshot of the map in reverse insertion order (SequencedMap, Java 21+). The JDK
     * returns a live view; the model returns an independent {@code LinkedHashMap} populated by reading
     * the backing in reverse (index {@code size-1} → 0). Differential-only (like the sequenced* views):
     * JBMC binds the real {@code SequencedMap.reversed} default over this override, so this is pinned on
     * the differential axis (MapConformanceTest) where the override is honored on a real JVM. Built by
     * index over the concrete backing, no entrySet() virtual dispatch.
     */
    @BmcModelConforms("differential (MapConformanceTest) — not @BmcProof: JBMC binds the real SequencedMap default over this override (devirtualization artifact, like sequenced*)")
    public Map<K, V> reversed() {
        LinkedHashMap<K, V> out = new LinkedHashMap<>();
        for (int i = size() - 1; i >= 0; i--) {
            out.put(keyAt(i), valueAt(i));
        }
        return out;
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ------------------

    @BmcUnmodelable(reason = "access-order LRU eviction hook — the insertion-ordered array model has no eviction policy; loud under JBMC")
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        throw fail("bmc4j: unmodelled member java.util.LinkedHashMap.removeEldestEntry(java.util.Map$Entry) — access-order LRU eviction hook — the insertion-ordered array model has no eviction policy");
    }

    /** Insertion-ordered key/value pair returned by {@link #firstEntry()} / {@link #lastEntry()}. */
    private static final class SeqNode<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        SeqNode(K key, V value) {
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
