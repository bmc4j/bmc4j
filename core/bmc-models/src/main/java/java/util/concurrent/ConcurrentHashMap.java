package java.util.concurrent;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

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
@BmcModelTail(reason = "exotic remainder absorbed from the HashMap backing surface — out of scope for the bounded concurrent-map model; all loud under JBMC")
public class ConcurrentHashMap<K, V> extends HashMap<K, V> {

    public ConcurrentHashMap() {
        super();
    }

    public ConcurrentHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    /** Package-private bridge to the inherited insertion-ordered key accessor, for the KeySetView. */
    K keyAtIndex(int i) {
        return keyAt(i);
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

    // --- legacy / size aliases --------------------------------------------------------------------

    /** Legacy Hashtable alias for {@link #containsValue(Object)}. */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /** {@code size()} as a {@code long} (CHM's wide-count accessor). */
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public long mappingCount() {
        return size();
    }

    /**
     * Legacy Hashtable-style {@link Enumeration} over the keys, like {@code ConcurrentHashMap.keys()}.
     * A bounded snapshot walked by index in insertion order — concrete backing, no interface dispatch
     * (the same pattern as {@code Collections.enumeration}).
     */
    @BmcModelConforms("differential (MapConformanceTest keys()/elements() enumerations)")
    public Enumeration<K> keys() {
        Object[] snapshot = new Object[size()];
        for (int i = 0; i < size(); i++) {
            snapshot[i] = keyAt(i);
        }
        return new ArrayEnumeration<>(snapshot, size());
    }

    /** Legacy Hashtable-style {@link Enumeration} over the values, like {@code ConcurrentHashMap.elements()}. */
    @BmcModelConforms("differential (MapConformanceTest keys()/elements() enumerations)")
    public Enumeration<V> elements() {
        Object[] snapshot = new Object[size()];
        for (int i = 0; i < size(); i++) {
            snapshot[i] = valueAt(i);
        }
        return new ArrayEnumeration<>(snapshot, size());
    }

    /** Concrete index-walked {@link Enumeration} over a snapshot array (no interface dispatch). */
    private static final class ArrayEnumeration<T> implements Enumeration<T> {

        private final Object[] elements;
        private final int count;
        private int cursor;

        ArrayEnumeration(Object[] elements, int count) {
            this.elements = elements;
            this.count = count;
        }

        @Override
        public boolean hasMoreElements() {
            return cursor < count;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T nextElement() {
            if (cursor >= count) {
                throw new NoSuchElementException();
            }
            return (T) elements[cursor++];
        }
    }

    /**
     * The {@link Set} view of this map's keys, like {@code ConcurrentHashMap.keySet()}. A bounded
     * {@link KeySetView} snapshot over the backing keys — the same concrete-backing pattern as
     * {@link HashMap#keySet()} (which returns a bounded HashSet snapshot), but the named CHM view type
     * so {@code keys()}-style consumers and the {@code keySet(mappedValue)} default carry through.
     */
    @Override
    @BmcModelConforms("differential (MapConformanceTest keySet/values/entrySet snapshot)")
    public Set<K> keySet() {
        return new KeySetView<>(this, null);
    }

    /**
     * A {@link KeySetView} over the keys whose {@code add(key)} installs {@code mappedValue}, like
     * {@code ConcurrentHashMap.keySet(mappedValue)}. The snapshot Set surface (contains/iterate/remove)
     * is bounded over the backing keys; {@code add} writes {@code (key, mappedValue)} into this map.
     */
    @BmcModelConforms("differential (ConcurrencyConformanceTest CHM keySet view)")
    public KeySetView<K, V> keySet(V mappedValue) {
        if (mappedValue == null) {
            throw new NullPointerException();
        }
        return new KeySetView<>(this, mappedValue);
    }

    /**
     * A fresh, empty key set backed by a CHM, like {@code ConcurrentHashMap.newKeySet()}. A bounded
     * {@link KeySetView} over a fresh backing map (mapped value {@code Boolean.TRUE}, JDK semantics) —
     * the add/contains/remove/iterate surface a proof exercises is observably a Set.
     */
    @BmcModelConforms("differential — used as a bounded Set (SetConformanceTest covers the Set surface)")
    public static <K> KeySetView<K, Boolean> newKeySet() {
        return new KeySetView<>(new ConcurrentHashMap<K, Boolean>(), Boolean.TRUE);
    }

    @BmcModelConforms("differential — used as a bounded Set (SetConformanceTest covers the Set surface)")
    public static <K> KeySetView<K, Boolean> newKeySet(int initialCapacity) {
        return new KeySetView<>(new ConcurrentHashMap<K, Boolean>(initialCapacity), Boolean.TRUE);
    }

    /**
     * Bounded {@link Set} view of a CHM's keys, returned by {@link #keySet()}/{@link #keySet(Object)}/
     * {@link #newKeySet()}. Modeled as a {@link HashSet} snapshot of the backing keys (inheriting the
     * audited bounded Set surface — contains/remove/iterate/forEach), the same concrete-backing pattern
     * as {@link HashMap#keySet()}. It carries the owning map + default {@code mappedValue} so
     * {@code add(key)} installs {@code (key, mappedValue)} into the backing map (NPE when no default was
     * set, matching the JDK); {@code getMap()}/{@code getMappedValue()} expose the view's identity.
     */
    public static final class KeySetView<K, V> extends HashSet<K> {

        private final ConcurrentHashMap<K, V> map;
        private final V mappedValue;

        KeySetView(ConcurrentHashMap<K, V> map, V mappedValue) {
            this.map = map;
            this.mappedValue = mappedValue;
            for (int i = 0; i < map.size(); i++) {
                super.add(map.keyAtIndex(i));
            }
        }

        /** The map backing this view. */
        public ConcurrentHashMap<K, V> getMap() {
            return map;
        }

        /** The default value {@link #add(Object)} maps an added key to, or null if none was set. */
        public V getMappedValue() {
            return mappedValue;
        }

        /**
         * Add {@code key} to the view, installing {@code (key, mappedValue)} into the backing map.
         * Throws {@link UnsupportedOperationException} when no default mapped value was set (JDK: a
         * {@code keySet()} view with no default rejects add), and {@link NullPointerException} on a null
         * key (CHM rejects nulls).
         */
        @Override
        public boolean add(K key) {
            if (mappedValue == null) {
                throw new UnsupportedOperationException();
            }
            if (key == null) {
                throw new NullPointerException();
            }
            boolean absent = map.get(key) == null;
            map.put(key, mappedValue);
            return super.add(key) || absent;
        }
    }

    // --- parallel bulk operations, modeled SEQUENTIALLY -------------------------------------------
    // On the single thread BMC analyzes, the parallelismThreshold is irrelevant: the JDK runs every
    // bulk op sequentially below the threshold anyway, and one thread is always "below" any threshold.
    // So each op is exactly its sequential definition — a single bounded scan of the backing arrays in
    // insertion order. forEach* visit each element (optionally through a transformer, skipping null
    // transforms — JDK semantics); search* return the first non-null function result (or null);
    // reduce* fold left over the (transformed) elements, returning null on an empty map for the
    // object-returning forms, or the supplied basis for the primitive forms. Functional args are plain
    // SAM calls (bmc4j desugars the lambda so JBMC devirtualizes apply/accept), like the inherited
    // HashMap functional ops. The threshold arg is read and ignored (documented equivalence).

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public void forEach(long parallelismThreshold, BiConsumer<? super K, ? super V> action) {
        for (int i = 0; i < size(); i++) {
            action.accept(keyAt(i), valueAt(i));
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> void forEach(long parallelismThreshold,
            BiFunction<? super K, ? super V, ? extends U> transformer, Consumer<? super U> action) {
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(keyAt(i), valueAt(i));
            if (u != null) {
                action.accept(u);
            }
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public void forEachKey(long parallelismThreshold, Consumer<? super K> action) {
        for (int i = 0; i < size(); i++) {
            action.accept(keyAt(i));
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> void forEachKey(long parallelismThreshold,
            Function<? super K, ? extends U> transformer, Consumer<? super U> action) {
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(keyAt(i));
            if (u != null) {
                action.accept(u);
            }
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public void forEachValue(long parallelismThreshold, Consumer<? super V> action) {
        for (int i = 0; i < size(); i++) {
            action.accept(valueAt(i));
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> void forEachValue(long parallelismThreshold,
            Function<? super V, ? extends U> transformer, Consumer<? super U> action) {
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(valueAt(i));
            if (u != null) {
                action.accept(u);
            }
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public void forEachEntry(long parallelismThreshold, Consumer<? super Map.Entry<K, V>> action) {
        for (int i = 0; i < size(); i++) {
            action.accept(new Tuple<>(keyAt(i), valueAt(i)));
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> void forEachEntry(long parallelismThreshold,
            Function<Map.Entry<K, V>, ? extends U> transformer, Consumer<? super U> action) {
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(new Tuple<>(keyAt(i), valueAt(i)));
            if (u != null) {
                action.accept(u);
            }
        }
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U search(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> searchFunction) {
        for (int i = 0; i < size(); i++) {
            U u = searchFunction.apply(keyAt(i), valueAt(i));
            if (u != null) {
                return u;
            }
        }
        return null;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U searchKeys(long parallelismThreshold, Function<? super K, ? extends U> searchFunction) {
        for (int i = 0; i < size(); i++) {
            U u = searchFunction.apply(keyAt(i));
            if (u != null) {
                return u;
            }
        }
        return null;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U searchValues(long parallelismThreshold, Function<? super V, ? extends U> searchFunction) {
        for (int i = 0; i < size(); i++) {
            U u = searchFunction.apply(valueAt(i));
            if (u != null) {
                return u;
            }
        }
        return null;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U searchEntries(long parallelismThreshold, Function<Map.Entry<K, V>, ? extends U> searchFunction) {
        for (int i = 0; i < size(); i++) {
            U u = searchFunction.apply(new Tuple<>(keyAt(i), valueAt(i)));
            if (u != null) {
                return u;
            }
        }
        return null;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U reduce(long parallelismThreshold,
            BiFunction<? super K, ? super V, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        U acc = null;
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(keyAt(i), valueAt(i));
            if (u == null) {
                continue;
            }
            acc = acc == null ? u : reducer.apply(acc, u);
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public K reduceKeys(long parallelismThreshold, BiFunction<? super K, ? super K, ? extends K> reducer) {
        K acc = null;
        for (int i = 0; i < size(); i++) {
            K k = keyAt(i);
            acc = acc == null ? k : reducer.apply(acc, k);
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U reduceKeys(long parallelismThreshold,
            Function<? super K, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        U acc = null;
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(keyAt(i));
            if (u == null) {
                continue;
            }
            acc = acc == null ? u : reducer.apply(acc, u);
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public V reduceValues(long parallelismThreshold, BiFunction<? super V, ? super V, ? extends V> reducer) {
        V acc = null;
        for (int i = 0; i < size(); i++) {
            V v = valueAt(i);
            acc = acc == null ? v : reducer.apply(acc, v);
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U reduceValues(long parallelismThreshold,
            Function<? super V, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        U acc = null;
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(valueAt(i));
            if (u == null) {
                continue;
            }
            acc = acc == null ? u : reducer.apply(acc, u);
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public Map.Entry<K, V> reduceEntries(long parallelismThreshold,
            BiFunction<Map.Entry<K, V>, Map.Entry<K, V>, ? extends Map.Entry<K, V>> reducer) {
        Map.Entry<K, V> acc = null;
        for (int i = 0; i < size(); i++) {
            Map.Entry<K, V> e = new Tuple<>(keyAt(i), valueAt(i));
            acc = acc == null ? e : reducer.apply(acc, e);
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public <U> U reduceEntries(long parallelismThreshold,
            Function<Map.Entry<K, V>, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        U acc = null;
        for (int i = 0; i < size(); i++) {
            U u = transformer.apply(new Tuple<>(keyAt(i), valueAt(i)));
            if (u == null) {
                continue;
            }
            acc = acc == null ? u : reducer.apply(acc, u);
        }
        return acc;
    }

    // Primitive-result reductions: fold a per-element primitive projection from the supplied basis.

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public int reduceToInt(long parallelismThreshold, ToIntBiFunction<? super K, ? super V> transformer,
            int basis, IntBinaryOperator reducer) {
        int acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsInt(acc, transformer.applyAsInt(keyAt(i), valueAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public long reduceToLong(long parallelismThreshold, ToLongBiFunction<? super K, ? super V> transformer,
            long basis, LongBinaryOperator reducer) {
        long acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsLong(acc, transformer.applyAsLong(keyAt(i), valueAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public double reduceToDouble(long parallelismThreshold, ToDoubleBiFunction<? super K, ? super V> transformer,
            double basis, DoubleBinaryOperator reducer) {
        double acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsDouble(acc, transformer.applyAsDouble(keyAt(i), valueAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public int reduceKeysToInt(long parallelismThreshold, ToIntFunction<? super K> transformer,
            int basis, IntBinaryOperator reducer) {
        int acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsInt(acc, transformer.applyAsInt(keyAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public long reduceKeysToLong(long parallelismThreshold, ToLongFunction<? super K> transformer,
            long basis, LongBinaryOperator reducer) {
        long acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsLong(acc, transformer.applyAsLong(keyAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public double reduceKeysToDouble(long parallelismThreshold, ToDoubleFunction<? super K> transformer,
            double basis, DoubleBinaryOperator reducer) {
        double acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsDouble(acc, transformer.applyAsDouble(keyAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public int reduceValuesToInt(long parallelismThreshold, ToIntFunction<? super V> transformer,
            int basis, IntBinaryOperator reducer) {
        int acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsInt(acc, transformer.applyAsInt(valueAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public long reduceValuesToLong(long parallelismThreshold, ToLongFunction<? super V> transformer,
            long basis, LongBinaryOperator reducer) {
        long acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsLong(acc, transformer.applyAsLong(valueAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public double reduceValuesToDouble(long parallelismThreshold, ToDoubleFunction<? super V> transformer,
            double basis, DoubleBinaryOperator reducer) {
        double acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsDouble(acc, transformer.applyAsDouble(valueAt(i)));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public int reduceEntriesToInt(long parallelismThreshold, ToIntFunction<Map.Entry<K, V>> transformer,
            int basis, IntBinaryOperator reducer) {
        int acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsInt(acc, transformer.applyAsInt(new Tuple<>(keyAt(i), valueAt(i))));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public long reduceEntriesToLong(long parallelismThreshold, ToLongFunction<Map.Entry<K, V>> transformer,
            long basis, LongBinaryOperator reducer) {
        long acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsLong(acc, transformer.applyAsLong(new Tuple<>(keyAt(i), valueAt(i))));
        }
        return acc;
    }

    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.concurrenthashmap)")
    public double reduceEntriesToDouble(long parallelismThreshold, ToDoubleFunction<Map.Entry<K, V>> transformer,
            double basis, DoubleBinaryOperator reducer) {
        double acc = basis;
        for (int i = 0; i < size(); i++) {
            acc = reducer.applyAsDouble(acc, transformer.applyAsDouble(new Tuple<>(keyAt(i), valueAt(i))));
        }
        return acc;
    }

    /** Immutable key/value pair handed to the bulk-op entry transformers/consumers. */
    private static final class Tuple<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        Tuple(K key, V value) {
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
