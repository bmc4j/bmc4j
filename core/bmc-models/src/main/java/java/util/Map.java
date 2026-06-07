package java.util;

/**
 * Minimal BMC model of {@link java.util.Map} — flattened interface declaring the members our
 * {@link HashMap} model supports. Omitted members fall back to JBMC's nondet stub.
 */
public interface Map<K, V> {
    int size();

    boolean isEmpty();

    V get(Object key);

    V put(K key, V value);

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    V remove(Object key);

    /**
     * Inserts {@code (key, value)} only if {@code key} is absent (or mapped to {@code null}),
     * returning the previous value (or {@code null}). Common enough to model directly — left
     * unmodeled it resolves to a JBMC nondet stub.
     */
    V putIfAbsent(K key, V value);

    void clear();

    V getOrDefault(Object key, V defaultValue);

    /** Snapshot of the keys (a {@code HashSet}). Read views; mutation-through-view isn't modeled. */
    Set<K> keySet();

    /** Snapshot of the values (an {@code ArrayList}, dup-preserving). */
    Collection<V> values();

    /** Snapshot of the entries (a {@code HashSet} of {@link Entry}). */
    Set<Map.Entry<K, V>> entrySet();

    /** A key/value pair, as yielded by {@link #entrySet()}. */
    interface Entry<K, V> {
        K getKey();

        V getValue();
    }

    // Immutable factories (java.util.Map.of) — pairs as alternating key/value arguments.

    static <K, V> Map<K, V> of() {
        return new HashMap<>();
    }

    static <K, V> Map<K, V> of(K k1, V v1) {
        HashMap<K, V> m = new HashMap<>();
        m.put(k1, v1);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        HashMap<K, V> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        HashMap<K, V> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }
}
