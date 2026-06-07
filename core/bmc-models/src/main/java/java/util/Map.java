package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    /**
     * If {@code key} has no present (non-null) mapping, compute one with {@code mappingFunction} and
     * install it unless the result is null. A null result leaves the key absent and returns null.
     */
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

    /**
     * If {@code key} is present (non-null), recompute with {@code remappingFunction}; a null result
     * removes the mapping. Absent keys are left untouched (returns null).
     */
    V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

    /**
     * Recompute the mapping for {@code key} (passing the current value, or null if absent); a null
     * result removes the mapping (and returns null), otherwise installs and returns the new value.
     */
    V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

    /**
     * If absent (or null) install {@code value}; otherwise merge the existing value with {@code value}
     * via {@code remappingFunction}. A null merge result removes the mapping.
     */
    V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);

    /** Apply {@code action} to each mapping, in iteration order. */
    void forEach(BiConsumer<? super K, ? super V> action);

    /** Replace the value for {@code key} only if it is currently present; returns the prior value. */
    V replace(K key, V value);

    /** Replace only if {@code key} currently maps to {@code oldValue}; returns whether it replaced. */
    boolean replace(K key, V oldValue, V newValue);

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
