package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * Clean BMC model of {@link java.util.HashMap}: parallel fixed-capacity key/value arrays with
 * linear lookup. Sound and bounded — lookups unwind to the current size, so keep maps within the
 * proof's {@code unwind} bound. Key equality uses {@code equals} (sound for boxed primitives).
 * Capacity is {@value #CAPACITY}.
 */
@BmcModelTail(reason = "exotic remainder: newHashMap(int) factory — out of scope; loud under JBMC")
public class HashMap<K, V> implements Map<K, V> {

    private static final int CAPACITY = 64;

    private final Object[] keys = new Object[CAPACITY];
    private final Object[] vals = new Object[CAPACITY];
    private int size;

    public HashMap() {
    }

    public HashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
    }

    /**
     * Copy constructor: a new map holding {@code m}'s mappings, like {@code new HashMap<>(map)}.
     * Bounded by capacity {@value #CAPACITY}.
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    private int indexOfKey(Object key) {
        for (int i = 0; i < size; i++) {
            if (key == null ? keys[i] == null : key.equals(keys[i])) {
                return i;
            }
        }
        return -1;
    }

    // --- protected ordered-storage access (for the insertion-ordered SequencedMap subclass) --------
    // The backing arrays preserve insertion order, so the LinkedHashMap model's SequencedMap surface
    // (firstEntry/lastEntry/poll*/putFirst/putLast) is built over these. Indices unwind to the current
    // size like every other array-backed lookup. Not part of the audited real-class surface.

    /** The key stored at insertion index {@code i} (0-based, in insertion order). */
    @SuppressWarnings("unchecked")
    protected final K keyAt(int i) {
        return (K) keys[i];
    }

    /** The value stored at insertion index {@code i}. */
    @SuppressWarnings("unchecked")
    protected final V valueAt(int i) {
        return (V) vals[i];
    }

    /**
     * Insert ({@code key}, {@code value}) at the FRONT (insertion index 0), shifting existing entries
     * right; if {@code key} is already present, remove it first then reposition to the front (matching
     * LinkedHashMap.putFirst). Returns the prior value for {@code key}, or null.
     */
    protected final V putAtFront(K key, V value) {
        int existing = indexOfKey(key);
        V old = existing < 0 ? null : valueAt(existing);
        if (existing >= 0) {
            for (int j = existing; j < size - 1; j++) {
                keys[j] = keys[j + 1];
                vals[j] = vals[j + 1];
            }
            size--;
        }
        for (int j = size; j > 0; j--) {
            keys[j] = keys[j - 1];
            vals[j] = vals[j - 1];
        }
        keys[0] = key;
        vals[0] = value;
        size++;
        return old;
    }

    /**
     * Insert ({@code key}, {@code value}) at the BACK; if {@code key} is already present, remove it
     * first then append (matching LinkedHashMap.putLast — a present key moves to the end). Returns the
     * prior value for {@code key}, or null.
     */
    protected final V putAtBack(K key, V value) {
        V old = remove(key);
        keys[size] = key;
        vals[size] = value;
        size++;
        return old;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public int size() {
        return size;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V get(Object key) {
        int i = indexOfKey(key);
        return i < 0 ? null : (V) vals[i];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V put(K key, V value) {
        int i = indexOfKey(key);
        if (i >= 0) {
            V old = (V) vals[i];
            vals[i] = value;
            return old;
        }
        keys[size] = key;
        vals[size] = value;
        size++;
        return null;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public boolean containsValue(Object value) {
        for (int i = 0; i < size; i++) {
            if (value == null ? vals[i] == null : value.equals(vals[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V putIfAbsent(K key, V value) {
        int i = indexOfKey(key);
        if (i >= 0 && vals[i] != null) {
            return (V) vals[i];     // present + non-null: leave it, return current (JDK semantics)
        }
        // absent, or present-but-null: install the new value
        if (i >= 0) {
            vals[i] = value;
            return null;
        }
        keys[size] = key;
        vals[size] = value;
        size++;
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V remove(Object key) {
        int i = indexOfKey(key);
        if (i < 0) {
            return null;
        }
        V old = (V) vals[i];
        for (int j = i; j < size - 1; j++) {
            keys[j] = keys[j + 1];
            vals[j] = vals[j + 1];
        }
        size--;
        return old;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public void clear() {
        size = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V getOrDefault(Object key, V defaultValue) {
        int i = indexOfKey(key);
        return i < 0 ? defaultValue : (V) vals[i];
    }

    // --- functional-arg ops -------------------------------------------------
    // Implemented over the public get/put/remove/containsKey surface so the present-but-null edge
    // cases (the classic JDK divergence traps) are handled exactly: "no present mapping" means absent
    // OR mapped to null, and a null compute/merge result REMOVES the key. Functional arguments are
    // plain SAM calls (bmc4j desugars the lambda so JBMC devirtualizes the apply/accept).

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V cur = get(key);
        if (cur != null) {
            return cur;                       // present (non-null) — left untouched
        }
        V newValue = mappingFunction.apply(key);
        if (newValue == null) {
            return null;                      // null result: key stays absent (no mapping installed)
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V cur = get(key);
        if (cur == null) {
            return null;                      // absent (or null-mapped): untouched
        }
        V newValue = remappingFunction.apply(key, cur);
        if (newValue == null) {
            remove(key);                      // null result removes the mapping
            return null;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V cur = get(key);
        V newValue = remappingFunction.apply(key, cur);
        if (newValue == null) {
            if (cur != null || containsKey(key)) {
                remove(key);                  // null result removes any existing mapping
            }
            return null;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        V cur = get(key);
        V newValue = cur == null ? value : remappingFunction.apply(cur, value);
        if (newValue == null) {
            remove(key);                      // null merge result removes the mapping
        } else {
            put(key, newValue);
        }
        return newValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (int i = 0; i < size; i++) {
            action.accept((K) keys[i], (V) vals[i]);
        }
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public V replace(K key, V value) {
        if (containsKey(key)) {
            return put(key, value);
        }
        return null;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public boolean replace(K key, V oldValue, V newValue) {
        V cur = get(key);
        if ((cur != null || containsKey(key)) && (cur == null ? oldValue == null : cur.equals(oldValue))) {
            put(key, newValue);
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public Set<K> keySet() {
        HashSet<K> ks = new HashSet<>();
        for (int i = 0; i < size; i++) {
            ks.add((K) keys[i]);
        }
        return ks;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public Collection<V> values() {
        ArrayList<V> vs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            vs.add((V) vals[i]);
        }
        return vs;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public Set<Map.Entry<K, V>> entrySet() {
        HashSet<Map.Entry<K, V>> es = new HashSet<>();
        for (int i = 0; i < size; i++) {
            es.add(new Node<>((K) keys[i], (V) vals[i]));
        }
        return es;
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ----------------

    @BmcNotModelled(reason = "functional-arg bulk replace — JBMC stubs the lambda dispatch")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw fail("bmc4j: unmodelled member java.util.HashMap.replaceAll(java.util.function.BiFunction) — functional-arg bulk replace — JBMC stubs the lambda dispatch");
    }

    @BmcNotNeeded(reason = "compare-and-remove — compose get()/remove() explicitly")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public boolean remove(Object key, Object value) {
        throw fail("bmc4j: unmodelled member java.util.HashMap.remove(java.lang.Object,java.lang.Object) — compare-and-remove — compose get()/remove() explicitly");
    }

    @BmcNotNeeded(reason = "bulk put — put entries explicitly over the bounded model")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public void putAll(Map<? extends K, ? extends V> m) {
        throw fail("bmc4j: unmodelled member java.util.HashMap.putAll(java.util.Map) — bulk put — put entries explicitly over the bounded model");
    }

    @BmcNotNeeded(reason = "shallow copy of a bounded model — construct a fresh map from the entries instead")
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.hashmap)")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.HashMap.clone() — shallow copy of a bounded model — construct a fresh map from the entries instead");
    }

    /** Immutable key/value pair returned by {@link #entrySet()}. */
    private static final class Node<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        Node(K key, V value) {
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
