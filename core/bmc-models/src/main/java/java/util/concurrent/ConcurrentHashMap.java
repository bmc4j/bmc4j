package java.util.concurrent;

import java.util.HashMap;

/**
 * Sequential BMC model of {@link java.util.concurrent.ConcurrentHashMap} — functionally a HashMap
 * (the bmc4j bounded map model). Concurrency isn't verified; this just lets logic proofs over code
 * that stores state in a concurrent map go through.
 *
 * <p>Unlike HashMap, ConcurrentHashMap rejects null keys and values (NPE) — modeled here so a proof
 * over code that puts/looks up a null in a concurrent map sees the real failure, not a silent pass.
 */
public class ConcurrentHashMap<K, V> extends HashMap<K, V> {

    public ConcurrentHashMap() {
        super();
    }

    public ConcurrentHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return super.put(key, value);
    }

    @Override
    public V get(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return super.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.remove(key);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.getOrDefault(key, defaultValue);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        if (containsKey(key)) {
            return get(key);
        }
        put(key, value);
        return null;
    }
}
