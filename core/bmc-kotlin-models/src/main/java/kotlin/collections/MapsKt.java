package kotlin.collections;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.ListSequence;
import kotlin.sequences.Sequence;
import org.cprover.CProver;

/**
 * Clean model of Kotlin's {@code MapsKt} facade for the map factories ({@code mapOf}/{@code
 * mutableMapOf}/{@code emptyMap}), building bmc4j's bounded {@code HashMap} model from Pairs instead
 * of routing through kotlin-stdlib internals JBMC stubs.
 *
 * <p>The whole {@code MapsKt} surface is now accounted for PER MEMBER (no class-level
 * {@code @BmcModelTail} catch-all): the bounded map-building / association / snapshot ops are modeled
 * with real delegating bodies over the bounded {@code HashMap}/{@code LinkedHashMap}/{@code TreeMap}
 * models; the inline lambda-taking extensions (filter/map/getOrPut/…) and the genuine walls
 * (comparator-ordered {@code TreeMap}, the {@code MapWithDefault} wrapper, {@code Map.Entry} factories
 * with no modeled entry type, the internal read-only optimizer) carry a per-member loud
 * {@code @BmcUnmodelable}.
 */
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

    // ---- buildMap { } : the read-only map builder.
    //   MapsKt.buildMap:(Lkotlin/jvm/functions/Function1;)Ljava/util/Map;
    //   MapsKt.buildMap:(ILkotlin/jvm/functions/Function1;)Ljava/util/Map;                (capacity hint)
    // buildMap is INLINE, so a Kotlin call site inlines its body: createMapBuilder() (a fresh builder),
    // the user builder action, then build(map) to seal it read-only — so the INLINE path actually reaches
    // createMapBuilder/build (modeled below), not buildMap. This buildMap facade JVM method is the NON-
    // inline / Java reach: allocate the bounded HashMap model, run the concrete (devirtualized) builder
    // lambda on it, return it. Backs onto the bounded HashMap model — matching the established mapOf/
    // mutableMapOf factories (unordered; the real builder is insertion-ordered, but the existing map
    // factories already model as HashMap, so this stays consistent). Capacity hint ignored (fixed backing).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> buildMap(Function1<? super Map<K, V>, kotlin.Unit> builderAction) {
        HashMap<K, V> builder = new HashMap<>();
        builderAction.invoke(builder);
        return build(builder);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> buildMap(int capacity, Function1<? super Map<K, V>, kotlin.Unit> builderAction) {
        HashMap<K, V> builder = new HashMap<>();
        builderAction.invoke(builder);
        return build(builder);
    }

    // ---- createMapBuilder() / createMapBuilder(int) / build(Map): the INLINE buildMap { } body's
    //   MapsKt.createMapBuilder:()Ljava/util/Map;
    //   MapsKt.createMapBuilder:(I)Ljava/util/Map;
    //   MapsKt.build:(Ljava/util/Map;)Ljava/util/Map;
    // building blocks. createMapBuilder returns a bounded HashMap builder; build returns it unchanged
    // (the real seal-to-read-only is the READ observable only — post-seal write rejection NOT modeled,
    // matching the read-observable precedent). createMapBuilder is what the inlined `buildMap { … }` call
    // site reaches; without it the path nondet-stubs (silently unsound). Capacity hint ignored.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> createMapBuilder() {
        return new HashMap<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> createMapBuilder(int capacity) {
        return new HashMap<>();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> build(Map<K, V> builder) {
        return builder;
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
        // Build through the TreeMap copy-constructor: it concrete-backs the source map's iteration (reads
        // the bmc4j HashMap model's backing by index) instead of going through the source's entry-set
        // iterator, whose interface-typed virtual dispatch is devirtualization-fragile under JBMC — the
        // empty source's zero-iteration copy could not be proven to add nothing and fell to a false UNKNOWN.
        return new TreeMap<>(map);
    }

    // ---- sortedMapOf(pairs[]): MapsKt.sortedMapOf:([Lkotlin/Pair;)Ljava/util/SortedMap; — a NEW
    // natural-ordering SortedMap (TreeMap) of the given pairs (keys must be Comparable), exactly the JDK's
    // TreeMap-fill. Sibling of toSortedMap(Map) above and built over the same bounded natural-ordering
    // TreeMap model. The comparator overload stays a loud wall (below). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> SortedMap<K, V> sortedMapOf(Pair<? extends K, ? extends V>[] pairs) {
        TreeMap<K, V> out = new TreeMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    // ---- hashMapOf(pairs[]) / linkedMapOf(pairs[]): a NEW HashMap / LinkedHashMap of the given pairs
    //   MapsKt.hashMapOf:([Lkotlin/Pair;)Ljava/util/HashMap;
    //   MapsKt.linkedMapOf:([Lkotlin/Pair;)Ljava/util/LinkedHashMap;
    // (later wins on key collision; LinkedHashMap preserves first-insertion key order). Both back onto
    // bmc4j's bounded models — matching the mapOf/mutableMapOf factories. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> HashMap<K, V> hashMapOf(Pair<? extends K, ? extends V>[] pairs) {
        HashMap<K, V> out = new HashMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> LinkedHashMap<K, V> linkedMapOf(Pair<? extends K, ? extends V>[] pairs) {
        LinkedHashMap<K, V> out = new LinkedHashMap<>();
        for (Pair<? extends K, ? extends V> p : pairs) {
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    // ---- putAll(map, pairs[]) / putAll(map, iterable<pair>) / putAll(map, sequence<pair>): MUTATE the
    //   MapsKt.putAll:(Ljava/util/Map;[Lkotlin/Pair;)V
    //   MapsKt.putAll:(Ljava/util/Map;Ljava/lang/Iterable;)V
    //   MapsKt.putAll:(Ljava/util/Map;Lkotlin/sequences/Sequence;)V
    // receiver in place, putting each pair (later wins on collision); returns void. The receiver is the
    // CONCRETE bounded map model (put() devirtualizes); the sequence is drained via its concrete backing.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> void putAll(Map<? super K, ? super V> map, Pair<? extends K, ? extends V>[] pairs) {
        for (Pair<? extends K, ? extends V> p : pairs) {
            map.put(p.getFirst(), p.getSecond());
        }
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> void putAll(Map<? super K, ? super V> map,
            java.lang.Iterable<? extends Pair<? extends K, ? extends V>> pairs) {
        for (Iterator<? extends Pair<? extends K, ? extends V>> it = pairs.iterator(); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            map.put(p.getFirst(), p.getSecond());
        }
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> void putAll(Map<? super K, ? super V> map,
            Sequence<? extends Pair<? extends K, ? extends V>> pairs) {
        for (Iterator<? extends Pair<? extends K, ? extends V>> it = seqIter(pairs); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            map.put(p.getFirst(), p.getSecond());
        }
    }

    // ---- toMap(src, destination): build into the SUPPLIED destination map and RETURN it (the M-typed
    //   MapsKt.toMap:(Ljava/lang/Iterable;Ljava/util/Map;)Ljava/util/Map;
    //   MapsKt.toMap:([Lkotlin/Pair;Ljava/util/Map;)Ljava/util/Map;
    //   MapsKt.toMap:(Ljava/util/Map;Ljava/util/Map;)Ljava/util/Map;
    //   MapsKt.toMap:(Lkotlin/sequences/Sequence;)Ljava/util/Map;
    //   MapsKt.toMap:(Lkotlin/sequences/Sequence;Ljava/util/Map;)Ljava/util/Map;
    // destination overloads of the snapshot family above). Each puts the source entries/pairs into the
    // destination (the CONCRETE bounded map) and returns it; the no-destination Sequence form allocates a
    // fresh HashMap. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V, M extends Map<? super K, ? super V>> M toMap(
            java.lang.Iterable<? extends Pair<? extends K, ? extends V>> pairs, M destination) {
        for (Iterator<? extends Pair<? extends K, ? extends V>> it = pairs.iterator(); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            destination.put(p.getFirst(), p.getSecond());
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V, M extends Map<? super K, ? super V>> M toMap(
            Pair<? extends K, ? extends V>[] pairs, M destination) {
        for (Pair<? extends K, ? extends V> p : pairs) {
            destination.put(p.getFirst(), p.getSecond());
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V, M extends Map<? super K, ? super V>> M toMap(
            Map<? extends K, ? extends V> map, M destination) {
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            destination.put(e.getKey(), e.getValue());
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> toMap(Sequence<? extends Pair<? extends K, ? extends V>> pairs) {
        HashMap<K, V> out = new HashMap<>();
        for (Iterator<? extends Pair<? extends K, ? extends V>> it = seqIter(pairs); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V, M extends Map<? super K, ? super V>> M toMap(
            Sequence<? extends Pair<? extends K, ? extends V>> pairs, M destination) {
        for (Iterator<? extends Pair<? extends K, ? extends V>> it = seqIter(pairs); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            destination.put(p.getFirst(), p.getSecond());
        }
        return destination;
    }

    // ---- toSingletonMap(map): MapsKt.toSingletonMap:(Ljava/util/Map;)Ljava/util/Map; — an internal
    // single-entry snapshot copy (the size==1 specialization toMap(Map) routes to). Observable here is a
    // fresh map with the receiver's single entry; modeled as the same bounded-HashMap copy. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> toSingletonMap(Map<? extends K, ? extends V> map) {
        return copyOf(map);
    }

    // ---- any(map) / none(map): MapsKt.any / none :(Ljava/util/Map;)Z — the no-predicate emptiness
    //   MapsKt.any:(Ljava/util/Map;)Z
    //   MapsKt.none:(Ljava/util/Map;)Z
    // checks (any == !isEmpty, none == isEmpty). The lambda-taking siblings any{}/none{} are inline and
    // stay loud walls below. (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> boolean any(Map<? extends K, ? extends V> map) {
        return !map.isEmpty();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> boolean none(Map<? extends K, ? extends V> map) {
        return map.isEmpty();
    }

    // ---- asSequence(map): MapsKt.asSequence:(Ljava/util/Map;)Lkotlin/sequences/Sequence; — a Sequence
    // over the map's entries, eagerly snapshotted into bmc4j's bounded ListSequence so downstream Sequence
    // ops analyse over the bounded model (mirrors CollectionsKt.asSequence). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    @SuppressWarnings("unchecked")
    public static <K, V> Sequence<Map.Entry<K, V>> asSequence(Map<? extends K, ? extends V> map) {
        ArrayList<Map.Entry<K, V>> entries = new ArrayList<>();
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            entries.add((Map.Entry<K, V>) e);   // reuse the bounded map model's own entry objects
        }
        return new ListSequence<>(entries);
    }

    // ---- plus(map, sequence<pair>) / minus(map, sequence<key>): the Sequence-arg twins of the pair/
    //   MapsKt.plus:(Ljava/util/Map;Lkotlin/sequences/Sequence;)Ljava/util/Map;
    //   MapsKt.minus:(Ljava/util/Map;Lkotlin/sequences/Sequence;)Ljava/util/Map;
    // map/iterable plus/minus above. A NEW map = receiver with the sequence's pairs put / keys removed;
    // receiver untouched. The sequence is drained via its concrete backing (seqIter). (Non-inline.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> plus(Map<? extends K, ? extends V> map,
            Sequence<? extends Pair<? extends K, ? extends V>> pairs) {
        HashMap<K, V> out = copyOf(map);
        for (Iterator<? extends Pair<? extends K, ? extends V>> it = seqIter(pairs); it.hasNext(); ) {
            Pair<? extends K, ? extends V> p = it.next();
            out.put(p.getFirst(), p.getSecond());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <K, V> Map<K, V> minus(Map<? extends K, ? extends V> map, Sequence<? extends K> keys) {
        HashMap<K, V> out = copyOf(map);
        for (Iterator<? extends K> it = seqIter(keys); it.hasNext(); ) {
            out.remove(it.next());
        }
        return out;
    }

    /**
     * Drain a model {@link Sequence} via its concrete backing {@link ArrayList} snapshot's iterator
     * rather than the virtual {@code Sequence.iterator()} — {@link ListSequence} is the sole {@code final}
     * implementor, so the {@code checkcast} is sound and the concrete {@code ArrayList.iterator()}
     * resolves where the interface dispatch on the {@code Sequence}-typed parameter is fragile. Mirrors
     * {@code SequencesKt}'s {@code seqIter}/{@code backing} pattern.
     */
    @SuppressWarnings("unchecked")
    private static <T> Iterator<T> seqIter(Sequence<? extends T> source) {
        CProver.assume(source instanceof ListSequence);
        return (Iterator<T>) ((ListSequence<? extends T>) source).backingList().iterator();
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

    // --- structural walls (loud stubs; reaching one demotes to a member-named UNKNOWN) ----------------

    @BmcUnmodelable(reason = "comparator-ordered TreeMap — bmc4j's TreeMap model is natural-ordering only "
            + "(no comparator constructor; comparator() is null), so a custom-comparator sort cannot be "
            + "modeled soundly; sibling of toSortedMap(Map,Comparator). Loud UNKNOWN under JBMC")
    public static java.util.SortedMap sortedMapOf(java.util.Comparator a0, kotlin.Pair[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.sortedMapOf(java.util.Comparator,kotlin.Pair[]) — comparator-ordered TreeMap; bmc4j's TreeMap model is natural-ordering only");
    }

    @BmcUnmodelable(reason = "mapEntryOf / copy build a java.util.AbstractMap.SimpleEntry — bmc4j has no "
            + "modeled standalone Map.Entry type (entries exist only inside the bounded map models), so the "
            + "returned entry would route to an unmodeled JVM type; loud-if-reached")
    public static java.util.Map.Entry mapEntryOf(java.lang.Object a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapEntryOf(java.lang.Object,java.lang.Object) — builds a standalone Map.Entry; bmc4j has no modeled standalone entry type");
    }

    @BmcUnmodelable(reason = "copy builds a standalone java.util.AbstractMap.SimpleEntry — bmc4j has no "
            + "modeled standalone Map.Entry type; loud-if-reached")
    public static java.util.Map.Entry copy(java.util.Map.Entry a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.copy(java.util.Map$Entry) — builds a standalone Map.Entry; bmc4j has no modeled standalone entry type");
    }

    @BmcUnmodelable(reason = "withDefault / withDefaultMutable wrap the map in a kotlin-stdlib MapWithDefault "
            + "delegating wrapper whose default-on-miss behaviour is an internal stdlib type bmc4j does not "
            + "model; the wrapper would route through stdlib internals JBMC stubs; loud-if-reached")
    public static java.util.Map withDefault(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.withDefault(java.util.Map,kotlin.jvm.functions.Function1) — MapWithDefault wrapper; not modeled");
    }

    @BmcUnmodelable(reason = "withDefaultMutable wraps the map in a kotlin-stdlib MutableMapWithDefault "
            + "delegating wrapper (internal stdlib type bmc4j does not model); loud-if-reached")
    public static java.util.Map withDefaultMutable(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.withDefaultMutable(java.util.Map,kotlin.jvm.functions.Function1) — MutableMapWithDefault wrapper; not modeled");
    }

    @BmcUnmodelable(reason = "getOrImplicitDefaultNullable is the internal accessor behind a MapWithDefault's "
            + "default-on-miss lookup; it dispatches on the (unmodeled) MapWithDefault wrapper interface, so "
            + "no sound bounded body exists; loud-if-reached")
    public static java.lang.Object getOrImplicitDefaultNullable(java.util.Map a0, java.lang.Object a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrImplicitDefaultNullable(java.util.Map,java.lang.Object) — internal MapWithDefault accessor; not modeled");
    }

    @BmcUnmodelable(reason = "internal kotlin-stdlib read-only-map size-optimizer (singleton/empty "
            + "specialization) on the map-build path; not reachable from idiomatic user code and its real "
            + "body routes through stdlib singleton-collection internals that JBMC stubs; loud-if-reached")
    public static java.util.Map optimizeReadOnlyMap(java.util.Map a0) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.optimizeReadOnlyMap(java.util.Map) — internal kotlin-stdlib read-only-map size-optimizer");
    }

}
