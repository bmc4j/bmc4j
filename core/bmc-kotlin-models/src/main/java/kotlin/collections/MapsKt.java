package kotlin.collections;

import java.util.HashMap;
import java.util.Map;
import kotlin.Pair;

/**
 * Clean model of Kotlin's {@code MapsKt} facade for the map factories ({@code mapOf}/{@code
 * mutableMapOf}/{@code emptyMap}), building bmc4j's bounded {@code HashMap} model from Pairs instead
 * of routing through kotlin-stdlib internals JBMC stubs.
 */
public final class MapsKt {

    private MapsKt() {
    }

    public static <K, V> Map<K, V> emptyMap() {
        return new HashMap<>();
    }

    public static <K, V> Map<K, V> mapOf(Pair<? extends K, ? extends V> pair) {
        HashMap<K, V> m = new HashMap<>();
        m.put(pair.getFirst(), pair.getSecond());
        return m;
    }

    public static <K, V> Map<K, V> mapOf(Pair<? extends K, ? extends V>[] pairs) {
        HashMap<K, V> m = new HashMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            m.put(p.getFirst(), p.getSecond());
        }
        return m;
    }

    public static <K, V> Map<K, V> mutableMapOf() {
        return new HashMap<>();
    }

    public static <K, V> Map<K, V> mutableMapOf(Pair<? extends K, ? extends V>[] pairs) {
        HashMap<K, V> m = new HashMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            m.put(p.getFirst(), p.getSecond());
        }
        return m;
    }

    /**
     * Sizing helper Kotlin's INLINE {@code associate}/{@code associateBy}/{@code associateWith}
     * emit to pre-size their {@code LinkedHashMap}: {@code mapCapacity(expectedSize)} computes a load-
     * factor-padded initial capacity. The actual association loop is inlined over the bounded map
     * model (which ignores capacity, backing arrays are fixed at CAPACITY), so this only needs to be
     * a sound, non-negative passthrough — but if left as a nondet stub the capacity poisons the
     * {@code LinkedHashMap(int)} ctor. Mirrors the stdlib formula for small sizes.
     */
    public static int mapCapacity(int expectedSize) {
        if (expectedSize < 0) {
            return expectedSize; // stdlib returns it unchanged for the (unreachable here) negative case
        }
        if (expectedSize < 3) {
            return expectedSize + 1;
        }
        return expectedSize + expectedSize / 3;
    }
}
