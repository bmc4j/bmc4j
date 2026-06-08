package kotlin.collections;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kotlin.Pair;

/**
 * Clean model of Kotlin's {@code MapsKt} facade for the map factories ({@code mapOf}/{@code
 * mutableMapOf}/{@code emptyMap}), building bmc4j's bounded {@code HashMap} model from Pairs instead
 * of routing through kotlin-stdlib internals JBMC stubs.
 */
@BmcModelTail(reason = "exotic MapsKt facade remainder — kotlin-stdlib's Map extension functions "
        + "(getOrPut/mapKeys/filterValues/etc.) the bounded proofs do not exercise; loud under JBMC if reached")
public final class MapsKt {

    private MapsKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> emptyMap() {
        return new HashMap<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> mapOf(Pair<? extends K, ? extends V> pair) {
        HashMap<K, V> m = new HashMap<>();
        m.put(pair.getFirst(), pair.getSecond());
        return m;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> mapOf(Pair<? extends K, ? extends V>[] pairs) {
        HashMap<K, V> m = new HashMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            m.put(p.getFirst(), p.getSecond());
        }
        return m;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> mutableMapOf() {
        return new HashMap<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
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
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int mapCapacity(int expectedSize) {
        if (expectedSize < 0) {
            return expectedSize; // stdlib returns it unchanged for the (unreachable here) negative case
        }
        if (expectedSize < 3) {
            return expectedSize + 1;
        }
        return expectedSize + expectedSize / 3;
    }

    // ---- getValue(map, key): MapsKt.getValue:(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object; —
    // returns the value for `key` or throws NoSuchElementException if absent (the strict accessor
    // behind Kotlin's `map.getValue(k)` and delegated properties). (Real chain nondet-stubs — REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> V getValue(Map<K, ? extends V> map, K key) {
        if (!map.containsKey(key)) {
            throw new java.util.NoSuchElementException("Key " + key + " is missing in the map.");
        }
        return map.get(key);
    }

    // ---- plus(map, pair) / plus(map, pairs[]) / plus(map, map) / plus(map, iterable<pair>): a NEW map
    //   MapsKt.plus:(Ljava/util/Map;Lkotlin/Pair;)Ljava/util/Map;
    //   MapsKt.plus:(Ljava/util/Map;[Lkotlin/Pair;)Ljava/util/Map;
    //   MapsKt.plus:(Ljava/util/Map;Ljava/util/Map;)Ljava/util/Map;
    //   MapsKt.plus:(Ljava/util/Map;Ljava/lang/Iterable;)Ljava/util/Map;
    // Kotlin contract: copy the receiver, then put the added entries (later wins on key collision),
    // returning a NEW map; receiver untouched. (Real chain nondet-stubs — probed REFUTED.)
    // NOTE: the HashMap model's putAll is a loud Unmodelable stub — copy entries explicitly via entrySet.
    private static <K, V> HashMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
        HashMap<K, V> out = new HashMap<>();
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> plus(Map<? extends K, ? extends V> map, Pair<? extends K, ? extends V> pair) {
        HashMap<K, V> out = copyOf(map);
        out.put(pair.getFirst(), pair.getSecond());
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> plus(Map<? extends K, ? extends V> map, Pair<? extends K, ? extends V>[] pairs) {
        HashMap<K, V> out = copyOf(map);
        for (Pair<? extends K, ? extends V> p : pairs) {
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> plus(Map<? extends K, ? extends V> map, Map<? extends K, ? extends V> other) {
        HashMap<K, V> out = copyOf(map);
        for (Map.Entry<? extends K, ? extends V> e : other.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> plus(Map<? extends K, ? extends V> map,
            java.lang.Iterable<? extends Pair<? extends K, ? extends V>> pairs) {
        HashMap<K, V> out = copyOf(map);
        for (java.util.Iterator<? extends Pair<? extends K, ? extends V>> it = pairs.iterator(); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    // ---- minus(map, key) / minus(map, keys[]) / minus(map, iterable<key>): a NEW map with the listed
    //   MapsKt.minus:(Ljava/util/Map;Ljava/lang/Object;)Ljava/util/Map;
    //   MapsKt.minus:(Ljava/util/Map;[Ljava/lang/Object;)Ljava/util/Map;
    //   MapsKt.minus:(Ljava/util/Map;Ljava/lang/Iterable;)Ljava/util/Map;
    // key(s) removed; receiver untouched. (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> minus(Map<? extends K, ? extends V> map, K key) {
        HashMap<K, V> out = copyOf(map);
        out.remove(key);
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> minus(Map<? extends K, ? extends V> map, K[] keys) {
        HashMap<K, V> out = copyOf(map);
        for (K k : keys) {
            out.remove(k);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> minus(Map<? extends K, ? extends V> map, java.lang.Iterable<? extends K> keys) {
        HashMap<K, V> out = copyOf(map);
        for (java.util.Iterator<? extends K> it = keys.iterator(); it.hasNext(); ) {
            out.remove(it.next());
        }
        return out;
    }

    // ---- toList(map): MapsKt.toList:(Ljava/util/Map;)Ljava/util/List; — a NEW list of (key,value)
    // Pairs in entry-iteration order. (Real chain nondet-stubs — probed REFUTED.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> java.util.List<Pair<K, V>> toList(Map<? extends K, ? extends V> map) {
        java.util.ArrayList<Pair<K, V>> out = new java.util.ArrayList<>();
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            out.add(new Pair<>(e.getKey(), e.getValue()));
        }
        return out;
    }

    // ---- toMap(map) / toMutableMap(map): a NEW snapshot copy.
    //   MapsKt.toMap:(Ljava/util/Map;)Ljava/util/Map;
    //   MapsKt.toMutableMap:(Ljava/util/Map;)Ljava/util/Map;
    // Kotlin contract: a fresh map with the same entries (toMutableMap is the mutable twin; same
    // observable here since the bounded HashMap model is mutable). Receiver untouched. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> toMap(Map<? extends K, ? extends V> map) {
        return copyOf(map);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> toMutableMap(Map<? extends K, ? extends V> map) {
        return copyOf(map);
    }

    // ---- toMap(Iterable<Pair>) / toMap(Pair[]): build a NEW map from the pairs (later wins on key
    //   MapsKt.toMap:(Ljava/lang/Iterable;)Ljava/util/Map;
    //   MapsKt.toMap:([Lkotlin/Pair;)Ljava/util/Map;
    // collision), in pair order. (Non-inline; the destination-Map overloads stay in the tail.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> toMap(java.lang.Iterable<? extends Pair<? extends K, ? extends V>> pairs) {
        HashMap<K, V> out = new HashMap<>();
        for (java.util.Iterator<? extends Pair<? extends K, ? extends V>> it = pairs.iterator(); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> toMap(Pair<? extends K, ? extends V>[] pairs) {
        HashMap<K, V> out = new HashMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    // ---- toSortedMap(map): MapsKt.toSortedMap:(Ljava/util/Map;)Ljava/util/SortedMap; — a NEW SortedMap
    // (TreeMap) holding all of the receiver's entries, sorted by key NATURAL ordering (keys must be
    // Comparable, exactly the JDK's `new TreeMap<>(map)`). This is a NON-inline stdlib function (real JVM
    // body in MapsKt__MapsJVMKt) whose call nondet-stubbed under JBMC (the real chain routes through
    // kotlin-stdlib internals → UNKNOWN), so it needs a real bmc4j model. Built over bmc4j's bounded,
    // natural-ordering TreeMap model: copy the entries in, return the TreeMap as the SortedMap the JDK
    // contract promises. The receiver is untouched (a fresh map is returned).
    //
    // NOTE: the comparator overload `toSortedMap(Map, Comparator)` is deliberately LEFT IN THE TAIL
    // (below, @BmcUnmodelable) — bmc4j's TreeMap model is natural-ordering ONLY (no comparator-taking
    // constructor; comparator() is always null), so it cannot honor a custom key order. Modeling that
    // overload here would silently sort by natural order instead of the supplied comparator → unsound.
    // It stays a loud UNKNOWN until/unless the TreeMap model grows a real comparator backing.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> SortedMap<K, V> toSortedMap(Map<? extends K, ? extends V> map) {
        TreeMap<K, V> out = new TreeMap<>();
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---

    @BmcUnmodelable(reason = "comparator-ordered TreeMap — bmc4j's TreeMap model is natural-ordering only "
            + "(no comparator constructor; comparator() is null), so a custom-comparator sort cannot be "
            + "modeled soundly; loud UNKNOWN under JBMC until the TreeMap model grows a comparator backing")
    public static SortedMap toSortedMap(java.util.Map a0, java.util.Comparator a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.toSortedMap(java.util.Map,java.util.Comparator) — comparator-ordered TreeMap; bmc4j's TreeMap model is natural-ordering only");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void all(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.all(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void any(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.any(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void count(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.count(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filter(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filter(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterKeys(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterKeys(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNot(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterNot(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNotTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterNotTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterValues(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterValues(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMap(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMap(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequence(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMapSequence(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequenceTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMapSequenceTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMapTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.forEach(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void getOrElseNullable(java.util.Map a0, java.lang.Object a1, kotlin.jvm.functions.Function0 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrElseNullable(java.util.Map,java.lang.Object,kotlin.jvm.functions.Function0) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void getOrPut(java.util.Map a0, java.lang.Object a1, kotlin.jvm.functions.Function0 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrPut(java.util.Map,java.lang.Object,kotlin.jvm.functions.Function0) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void getOrPut(java.util.concurrent.ConcurrentMap a0, java.lang.Object a1, kotlin.jvm.functions.Function0 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrPut(java.util.concurrent.ConcurrentMap,java.lang.Object,kotlin.jvm.functions.Function0) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void map(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.map(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapKeys(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapKeys(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapKeysTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapKeysTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNull(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapNotNull(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNullTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapNotNullTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapValues(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapValues(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapValuesTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapValuesTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void none(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.none(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEach(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.onEach(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEachIndexed(java.util.Map a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.onEachIndexed(java.util.Map,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

}
