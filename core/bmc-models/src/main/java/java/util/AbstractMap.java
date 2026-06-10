package java.util;

/**
 * Minimal BMC model of {@link java.util.AbstractMap} — the skeletal {@link Map} base a user map
 * extends, overriding the single abstract primitive {@link #entrySet()} (and, for a mutable map,
 * {@link #put}). The DERIVED read surface ({@code size}/{@code isEmpty}/{@code get}/{@code containsKey}/
 * {@code containsValue}/{@code keySet}/{@code values}) is modeled here over {@code entrySet()}, exactly
 * as the JDK does, so a user {@code class MyMap extends AbstractMap<…>} held through {@link Map}
 * DEVIRTUALIZES to one sound body instead of leaving the interface method an opaque nondet stub (a
 * false refutation on a havoc artifact).
 *
 * <p>{@link #entrySet()} stays ABSTRACT so JBMC resolves it to the user's override. The derived bodies
 * walk that entry set's iterator (the only primitive {@code AbstractMap} exposes — unlike
 * {@link AbstractList}, there is no by-index surface to prefer). Bounded by the proof's {@code unwind}.
 * The concrete {@link HashMap}/{@link TreeMap} models implement {@link Map} directly (array-backed) and
 * do NOT extend this skeleton, so a {@code HashMap} instance still resolves to its own model; this
 * skeleton is only what a USER subclass devirtualizes through.
 */
public abstract class AbstractMap<K, V> implements Map<K, V> {

    protected AbstractMap() {
    }

    @Override
    public abstract Set<Map.Entry<K, V>> entrySet();

    @Override
    public int size() {
        return entrySet().size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, V> e = it.next();
            K k = e.getKey();
            if (key == null ? k == null : key.equals(k)) {
                return e.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (it.hasNext()) {
            K k = it.next().getKey();
            if (key == null ? k == null : key.equals(k)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (it.hasNext()) {
            V v = it.next().getValue();
            if (value == null ? v == null : value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return containsKey(key) ? get(key) : defaultValue;
    }

    @Override
    public Set<K> keySet() {
        HashSet<K> keys = new HashSet<>();
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (it.hasNext()) {
            keys.add(it.next().getKey());
        }
        return keys;
    }

    @Override
    public Collection<V> values() {
        ArrayList<V> vals = new ArrayList<>();
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (it.hasNext()) {
            vals.add(it.next().getValue());
        }
        return vals;
    }

    /** Default {@code put} is unsupported (the JDK's skeletal base throws); a mutable subclass overrides it. */
    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }
}
