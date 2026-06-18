package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean BMC model of {@link java.util.Hashtable} in the {@link HashMap} model's style: parallel
 * fixed-capacity key/value arrays with linear, {@code equals}-based lookup. Sound and bounded — lookups
 * unwind to the current size, so keep tables within the proof's {@code unwind} bound. Capacity is
 * {@value #CAPACITY}.
 *
 * <p><b>Why this model exists.</b> Without it, {@code Hashtable} (and its {@link Dictionary} base and
 * the {@link Properties} subclass) is unmodeled: a {@code put} done in one place and a {@code get} done
 * in another do NOT share backing state in the analysis, so {@code get} havocs to a nondet (possibly
 * unbounded) value. {@code HashMap}/{@code LinkedHashMap}/{@code TreeMap}/{@code ConcurrentHashMap} are
 * already modeled; this brings the legacy {@code Hashtable} family onto the same faithful, shared-state
 * footing (the motivating case: a static {@link Properties} table filled in {@code <clinit>} whose
 * {@code getProperty} must return the stored literal, length-bounded by the input).
 *
 * <p>Only OBSERVABLE behavior is modeled; the JDK's real hashing / rehash / load-factor internals are
 * NOT mirrored (they are unobservable through the map contract). {@code keys()}/{@code elements()}
 * return concrete bounded enumerations that walk the backing by index (no virtual dispatch). Like the
 * real class, {@code null} keys and {@code null} values are rejected. The exotic remainder of the real
 * surface (IO, clone, the keySet/values/entrySet views and their iterators, rehash, equals/hashCode/
 * toString) is absorbed by the class-level {@code @BmcModelTail} with LOUD synthesized bodies — reaching
 * any of it is an honest member-named UNKNOWN, never a silent nondet stub.
 */
@org.bmc4j.models.audit.BmcModelTail(
    reason = "Hashtable's IO/clone/view/rehash surface (keySet/values/entrySet + their iterators, "
        + "rehash/contains-as-Map.containsValue duplication, equals/hashCode/toString, the load-factor "
        + "constructors' internals) is out of scope for this bounded observable-state model; the core "
        + "Dictionary/Map operations (get/put/remove/size/isEmpty/containsKey/containsValue/contains/"
        + "keys/elements/getOrDefault/putIfAbsent/clear) are modeled and audited")
public class Hashtable<K, V> extends Dictionary<K, V> implements Map<K, V> {

    private static final int CAPACITY = 64;

    private final Object[] keys = new Object[CAPACITY];
    private final Object[] vals = new Object[CAPACITY];
    private int size;

    public Hashtable() {
    }

    public Hashtable(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
        }
    }

    public Hashtable(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal Load: " + loadFactor);
        }
    }

    private int indexOfKey(Object key) {
        for (int i = 0; i < size; i++) {
            if (key.equals(keys[i])) {
                return i;
            }
        }
        return -1;
    }

    // --- protected ordered-storage access (for the Properties subclass / concrete enumerations) -----
    // The backing arrays preserve insertion order; the index walk unwinds to the current size like every
    // other array-backed lookup. Not part of the audited real-class surface (model-internal helpers).

    @SuppressWarnings("unchecked")
    final K keyAt(int i) {
        return (K) keys[i];
    }

    @SuppressWarnings("unchecked")
    final V valueAt(int i) {
        return (V) vals[i];
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public int size() {
        return size;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V get(Object key) {
        int i = indexOfKey(key);
        return i < 0 ? null : (V) vals[i];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V put(K key, V value) {
        // Hashtable rejects null keys AND null values (NPE), unlike HashMap.
        if (value == null) {
            throw new NullPointerException();
        }
        int i = indexOfKey(key);   // a null key NPEs here via equals — the JDK contract
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
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
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
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public boolean containsValue(Object value) {
        return contains(value);
    }

    /** Legacy {@code contains(value)} — value membership, as the JDK declares it on Hashtable. */
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public boolean contains(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        for (int i = 0; i < size; i++) {
            if (value.equals(vals[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V getOrDefault(Object key, V defaultValue) {
        int i = indexOfKey(key);
        return i < 0 ? defaultValue : (V) vals[i];
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V putIfAbsent(K key, V value) {
        int i = indexOfKey(key);
        if (i >= 0) {
            return (V) vals[i];     // Hashtable never stores null, so a present key is always non-null
        }
        put(key, value);
        return null;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public void clear() {
        size = 0;
    }

    // --- functional-arg ops (Map surface) — over the public get/put/remove so the present-but-null
    // edge cases match exactly, like the HashMap model. Hashtable never stores null, so "no present
    // mapping" is simply absence. Functional args are plain SAM calls (bmc4j desugars the lambda so
    // JBMC devirtualizes apply/accept). -------------------------------------------------------------

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V cur = get(key);
        if (cur != null) {
            return cur;
        }
        V newValue = mappingFunction.apply(key);
        if (newValue != null) {
            put(key, newValue);
        }
        return newValue;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V cur = get(key);
        if (cur == null) {
            return null;
        }
        V newValue = remappingFunction.apply(key, cur);
        if (newValue == null) {
            remove(key);
            return null;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V cur = get(key);
        V newValue = remappingFunction.apply(key, cur);
        if (newValue == null) {
            if (cur != null) {
                remove(key);
            }
            return null;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        V cur = get(key);
        V newValue = cur == null ? value : remappingFunction.apply(cur, value);
        if (newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        return newValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (int i = 0; i < size; i++) {
            action.accept((K) keys[i], (V) vals[i]);
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public V replace(K key, V value) {
        if (containsKey(key)) {
            return put(key, value);
        }
        return null;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public boolean replace(K key, V oldValue, V newValue) {
        V cur = get(key);
        if (cur != null && cur.equals(oldValue)) {
            put(key, newValue);
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public Set<K> keySet() {
        HashSet<K> ks = new HashSet<>();
        for (int i = 0; i < size; i++) {
            ks.add((K) keys[i]);
        }
        return ks;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public Collection<V> values() {
        ArrayList<V> vs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            vs.add((V) vals[i]);
        }
        return vs;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public Set<Map.Entry<K, V>> entrySet() {
        HashSet<Map.Entry<K, V>> es = new HashSet<>();
        for (int i = 0; i < size; i++) {
            es.add(new Node<>((K) keys[i], (V) vals[i]));
        }
        return es;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public Enumeration<K> keys() {
        return new KeyEnumeration<>(this);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.properties HashtableLaws)")
    public Enumeration<V> elements() {
        return new ValueEnumeration<>(this);
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

    /** Bounded by-index enumeration over the table's keys (concrete backing — no virtual dispatch). */
    private static final class KeyEnumeration<K> implements Enumeration<K> {
        private final Hashtable<K, ?> table;
        private int cursor;

        KeyEnumeration(Hashtable<K, ?> table) {
            this.table = table;
        }

        @Override
        public boolean hasMoreElements() {
            return cursor < table.size();
        }

        @Override
        public K nextElement() {
            if (cursor >= table.size()) {
                throw new NoSuchElementException();
            }
            return table.keyAt(cursor++);
        }
    }

    /** Bounded by-index enumeration over the table's values. */
    private static final class ValueEnumeration<V> implements Enumeration<V> {
        private final Hashtable<?, V> table;
        private int cursor;

        ValueEnumeration(Hashtable<?, V> table) {
            this.table = table;
        }

        @Override
        public boolean hasMoreElements() {
            return cursor < table.size();
        }

        @Override
        public V nextElement() {
            if (cursor >= table.size()) {
                throw new NoSuchElementException();
            }
            return table.valueAt(cursor++);
        }
    }
}
