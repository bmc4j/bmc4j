package java.util;

import java.util.function.BiConsumer;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * BMC model of {@link java.util.EnumMap} — an enum-keyed map, modeled over the same fixed-capacity
 * parallel key/value arrays + linear lookup as the {@link HashMap} model (which it extends to inherit
 * the audited get/remove/containsKey/containsValue/getOrDefault/compute/merge/replace/putAll/
 * remove(key,value) surface). Sound and bounded — lookups unwind to the current size, so keep maps
 * within the proof's {@code unwind} bound. Capacity is the inherited HashMap capacity.
 *
 * <p>Two behaviours differ from a plain HashMap and are modeled here:
 * <ul>
 *   <li><b>Null keys rejected.</b> {@code put(null, v)} throws {@link NullPointerException}, like the
 *       real EnumMap (an enum key is never null). The overriding {@code put(K extends Enum, V)} also
 *       gives the model the real {@code put(Enum,Object)} erased signature.</li>
 *   <li><b>Ordinal iteration order.</b> The real EnumMap iterates its keys in {@code Enum.ordinal()}
 *       order, not insertion order. {@code keySet}/{@code values}/{@code entrySet}/{@code forEach} sort
 *       the present keys by {@code ordinal()} (a sound, total int read on each enum key) before
 *       producing the snapshot, so iteration matches the JDK.</li>
 * </ul>
 *
 * <p>The {@code EnumMap(Class)} key-type constructor stores nothing observable in this fixed-capacity
 * model (the key type is only a sizing/validation hint); it is exactly a fresh empty map. The
 * {@code EnumMap(EnumMap)} and {@code EnumMap(Map)} copy constructors insert the source mappings.
 *
 * <p>Every real EnumMap member is classified per-member: the EnumMap-specific overrides below carry
 * {@code @BmcModelConforms}, and the remaining surface (including {@code clone()}) resolves up the
 * modeled {@link HashMap} chain to HashMap's per-member decisions. There is no class-level catch-all.
 */
public class EnumMap<K extends Enum<K>, V> extends HashMap<K, V> {

    /**
     * Key-type constructor ({@code new EnumMap<>(MyEnum.class)}). The key type is a sizing/validation
     * hint only; in this fixed-capacity model it stores nothing observable, so this is a fresh empty
     * map. A null key type throws NPE, like the JDK.
     */
    public EnumMap(Class<K> keyType) {
        super();
        if (keyType == null) {
            throw new NullPointerException();
        }
    }

    /** Copy constructor from another EnumMap — a new map with the same mappings. */
    public EnumMap(EnumMap<K, ? extends V> m) {
        super();
        putAll(m);
    }

    /**
     * Copy constructor from any Map — a new map with the same mappings (the JDK requires it to be an
     * EnumMap or non-empty; an empty non-EnumMap yields an empty map, which this reproduces).
     */
    public EnumMap(Map<K, ? extends V> m) {
        super();
        putAll(m);
    }

    /**
     * Associate {@code value} with the enum {@code key}. Overrides HashMap's {@code put} to (a) reject a
     * null key with {@link NullPointerException} (an enum key is never null) and (b) carry the real
     * {@code put(Enum,Object)} erased signature. Returns the prior value, or null.
     */
    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.enummap)")
    public V put(K key, V value) {
        if (key == null) {
            throw new NullPointerException();
        }
        return super.put(key, value);
    }

    // --- ordinal-ordered iteration snapshots ----------------------------------------------------
    // The real EnumMap iterates in Enum.ordinal() order. ordinal() is a sound total int read on each
    // (non-null) enum key, so we snapshot the present keys into an index array sorted ascending by
    // ordinal (insertion sort over the bounded size), then build the views in that order.

    /** Insertion indices of the present entries, ordered ascending by their key's {@code ordinal()}. */
    private int[] orderByOrdinal() {
        int n = size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // insertion sort by ordinal (small n; quadratic but bounded).
        for (int i = 1; i < n; i++) {
            int cur = order[i];
            int curOrd = keyAt(cur).ordinal();
            int j = i - 1;
            while (j >= 0 && keyAt(order[j]).ordinal() > curOrd) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = cur;
        }
        return order;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.enummap)")
    public Set<K> keySet() {
        int[] order = orderByOrdinal();
        LinkedHashSet<K> ks = new LinkedHashSet<>();
        for (int idx : order) {
            ks.add(keyAt(idx));
        }
        return ks;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.enummap)")
    public Collection<V> values() {
        int[] order = orderByOrdinal();
        ArrayList<V> vs = new ArrayList<>();
        for (int idx : order) {
            vs.add(valueAt(idx));
        }
        return vs;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.enummap)")
    public Set<Map.Entry<K, V>> entrySet() {
        int[] order = orderByOrdinal();
        LinkedHashSet<Map.Entry<K, V>> es = new LinkedHashSet<>();
        for (int idx : order) {
            es.add(new EnumEntry<>(keyAt(idx), valueAt(idx)));
        }
        return es;
    }

    @Override
    @BmcModelConforms("differential (MapConformanceTest) + @BmcProof (proofs.enummap)")
    public void forEach(BiConsumer<? super K, ? super V> action) {
        int[] order = orderByOrdinal();
        for (int idx : order) {
            action.accept(keyAt(idx), valueAt(idx));
        }
    }

    /** Immutable key/value pair returned by {@link #entrySet()}. */
    private static final class EnumEntry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private final V value;

        EnumEntry(K key, V value) {
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
