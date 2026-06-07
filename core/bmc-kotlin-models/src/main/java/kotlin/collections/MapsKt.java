package kotlin.collections;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotNeeded;

import java.util.HashMap;
import java.util.Map;
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

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void all(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.all(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void any(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.any(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void count(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.count(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filter(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filter(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterKeys(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterKeys(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNot(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterNot(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNotTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterNotTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterValues(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.filterValues(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMap(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMap(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequence(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMapSequence(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapSequenceTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMapSequenceTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.flatMapTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.forEach(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void getOrElseNullable(java.util.Map a0, java.lang.Object a1, kotlin.jvm.functions.Function0 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrElseNullable(java.util.Map,java.lang.Object,kotlin.jvm.functions.Function0) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void getOrPut(java.util.Map a0, java.lang.Object a1, kotlin.jvm.functions.Function0 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrPut(java.util.Map,java.lang.Object,kotlin.jvm.functions.Function0) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void getOrPut(java.util.concurrent.ConcurrentMap a0, java.lang.Object a1, kotlin.jvm.functions.Function0 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.getOrPut(java.util.concurrent.ConcurrentMap,java.lang.Object,kotlin.jvm.functions.Function0) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void map(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.map(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapKeys(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapKeys(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapKeysTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapKeysTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNull(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapNotNull(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNullTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapNotNullTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapTo(java.util.Map a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapTo(java.util.Map,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapValues(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapValues(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapValuesTo(java.util.Map a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.mapValuesTo(java.util.Map,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void none(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.none(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEach(java.util.Map a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.onEach(java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void onEachIndexed(java.util.Map a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.collections.MapsKt.onEachIndexed(java.util.Map,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

}
