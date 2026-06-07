package java.util.concurrent;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.ConcurrentHashMap} — functionally a HashMap
 * (the bmc4j bounded map model). Concurrency isn't verified; this just lets logic proofs over code
 * that stores state in a concurrent map go through.
 *
 * <p>Unlike HashMap, ConcurrentHashMap rejects null keys and values (NPE) — modeled here so a proof
 * over code that puts/looks up a null in a concurrent map sees the real failure, not a silent pass.
 */
@BmcModelTail(reason = "the functional-arg map ops (compute*/merge/forEach/replaceAll) and the entire parallel bulk surface (forEachKey/reduce*/search*/reduceToInt/… across keys/values/entries with parallelismThreshold), plus newKeySet/mappingCount/elements/keys/contains(value-alias) — out of scope for a sequential bounded model; all loud under JBMC")
public class ConcurrentHashMap<K, V> extends HashMap<K, V> {

    public ConcurrentHashMap() {
        super();
    }

    public ConcurrentHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return super.put(key, value);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V get(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.get(key);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public boolean containsKey(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.containsKey(key);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return super.containsValue(value);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V remove(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.remove(key);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V getOrDefault(Object key, V defaultValue) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.getOrDefault(key, defaultValue);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
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

    // The functional-arg ops: ConcurrentHashMap rejects null keys and null mapping functions (NPE),
    // and (unlike HashMap) never stores null — a null function result removes the mapping. The
    // superclass logic is otherwise correct here because CHM never holds a null value, so its
    // "present" test (get != null) coincides with containsKey. We only add the null guards.

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new NullPointerException();
        }
        return super.computeIfAbsent(key, mappingFunction);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        return super.computeIfPresent(key, remappingFunction);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        return super.compute(key, remappingFunction);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        return super.merge(key, value, remappingFunction);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public V replace(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return super.replace(key, value);
    }

    @Override
    @BmcModelConforms("inherits the HashMap model surface")
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        return super.replace(key, oldValue, newValue);
    }
}
