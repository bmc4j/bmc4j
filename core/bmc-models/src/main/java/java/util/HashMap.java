package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Clean BMC model of {@link java.util.HashMap}: parallel fixed-capacity key/value arrays with
 * linear lookup. Sound and bounded — lookups unwind to the current size, so keep maps within the
 * proof's {@code unwind} bound. Key equality uses {@code equals} (sound for boxed primitives).
 * Capacity is {@value #CAPACITY}.
 */
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

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        int i = indexOfKey(key);
        return i < 0 ? null : (V) vals[i];
    }

    @Override
    @SuppressWarnings("unchecked")
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
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    @Override
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
    public void clear() {
        size = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
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
    public void forEach(BiConsumer<? super K, ? super V> action) {
        for (int i = 0; i < size; i++) {
            action.accept((K) keys[i], (V) vals[i]);
        }
    }

    @Override
    public V replace(K key, V value) {
        if (containsKey(key)) {
            return put(key, value);
        }
        return null;
    }

    @Override
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
    public Set<K> keySet() {
        HashSet<K> ks = new HashSet<>();
        for (int i = 0; i < size; i++) {
            ks.add((K) keys[i]);
        }
        return ks;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<V> values() {
        ArrayList<V> vs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            vs.add((V) vals[i]);
        }
        return vs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Map.Entry<K, V>> entrySet() {
        HashSet<Map.Entry<K, V>> es = new HashSet<>();
        for (int i = 0; i < size; i++) {
            es.add(new Node<>((K) keys[i], (V) vals[i]));
        }
        return es;
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
