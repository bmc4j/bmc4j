package java.util;

/**
 * Minimal BMC model of {@link java.util.Dictionary} — the legacy abstract key/value supertype of
 * {@link Hashtable}. Declares only the abstract operation surface the JDK does
 * ({@code size}/{@code isEmpty}/{@code keys}/{@code elements}/{@code get}/{@code put}/{@code remove}),
 * with no behavior of its own; the concrete {@link Hashtable} model supplies and audits every operation
 * over its bounded backing arrays. Abstract skeletal base (like {@link AbstractMap}) — exercised through
 * the Hashtable / Properties models, not directly.
 */
public abstract class Dictionary<K, V> {

    public Dictionary() {
    }

    public abstract int size();

    public abstract boolean isEmpty();

    public abstract Enumeration<K> keys();

    public abstract Enumeration<V> elements();

    public abstract V get(Object key);

    public abstract V put(K key, V value);

    public abstract V remove(Object key);
}
