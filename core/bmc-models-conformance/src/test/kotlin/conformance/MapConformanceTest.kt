package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

private class MapOp(val desc: String, val method: String, val types: Array<Class<*>>, val args: Array<Any?>) {
    fun on(t: Any) = call(t, method, types, *args)
    override fun toString() = desc
}

private fun mapOp(allowNullKey: Boolean): Arb<MapOp> {
    // Small, collision-dense key domain incl. negatives (and null where allowed): what exercises the
    // model is repeated keys (overwrite/remove/lookup), not magnitude.
    val key: Arb<Int?> = if (allowNullKey) Arb.int(-3..5).orNull(0.15) else Arb.int(-3..5).map { it as Int? }
    val value: Arb<Int?> = Arb.int(-9..9).orNull(0.1)
    return Arb.choice(
        Arb.bind(key, value) { k, v -> MapOp("put($k,$v)", "put", arrayOf(OBJECT, OBJECT), arrayOf(k, v)) },
        key.map { MapOp("get($it)", "get", arrayOf(OBJECT), arrayOf(it)) },
        key.map { MapOp("remove($it)", "remove", arrayOf(OBJECT), arrayOf(it)) },
        key.map { MapOp("containsKey($it)", "containsKey", arrayOf(OBJECT), arrayOf(it)) },
        value.map { MapOp("containsValue($it)", "containsValue", arrayOf(OBJECT), arrayOf(it)) },
        Arb.bind(key, value) { k, v -> MapOp("putIfAbsent($k,$v)", "putIfAbsent", arrayOf(OBJECT, OBJECT), arrayOf(k, v)) },
        Arb.bind(key, Arb.int(-9..9)) { k, d -> MapOp("getOrDefault($k,$d)", "getOrDefault", arrayOf(OBJECT, OBJECT), arrayOf(k, d)) },
        Arb.bind(key, value) { k, v -> MapOp("putIfAbsent($k,$v)", "putIfAbsent", arrayOf(OBJECT, OBJECT), arrayOf(k, v)) },
        Arb.bind(key, value) { k, v -> MapOp("replace($k,$v)", "replace", arrayOf(OBJECT, OBJECT), arrayOf(k, v)) },
        Arb.constant(MapOp("clear", "clear", arrayOf(), arrayOf())),
    )
}

private suspend fun checkMap(real: () -> Any, model: () -> Any, allowNullKey: Boolean) {
    checkAll(Arb.list(mapOp(allowNullKey), 0..40)) { ops ->
        val r = real()
        val m = model()
        ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
        assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
        for (k in 0..4) {
            assertEquivalent("get($k)", call(r, "get", arrayOf(OBJECT), k), call(m, "get", arrayOf(OBJECT), k))
        }
    }
}

/**
 * Differential conformance for the Map models. Value operations (put/get/remove/containsKey/
 * getOrDefault) must match the JDK; iteration order and TreeMap's sorted/null-rejecting semantics
 * are deliberately not modeled, so generators stay on the value surface (and non-null keys for
 * TreeMap, whose real null-key rejection the array-backed model doesn't reproduce).
 */
class MapConformanceTest : FunSpec({

    test("HashMap conforms") {
        checkMap({ java.util.HashMap<Any?, Any?>() }, { bmcref.java.util.HashMap<Any?, Any?>() }, allowNullKey = true)
    }

    test("LinkedHashMap conforms") {
        checkMap({ java.util.LinkedHashMap<Any?, Any?>() }, { bmcref.java.util.LinkedHashMap<Any?, Any?>() }, allowNullKey = true)
    }

    test("TreeMap conforms on value ops (non-null keys)") {
        checkMap({ java.util.TreeMap<Any?, Any?>() }, { bmcref.java.util.TreeMap<Any?, Any?>() }, allowNullKey = false)
    }

    test("ConcurrentHashMap conforms (incl. rejecting null keys/values)") {
        checkMap({ java.util.concurrent.ConcurrentHashMap<Any?, Any?>() }, { bmcref.java.util.concurrent.ConcurrentHashMap<Any?, Any?>() }, allowNullKey = true)
    }

    // --- TreeMap NavigableMap navigation (firstKey/lastKey/.../ceilingKey/floorKey/higher/lower) ----
    // Build identical maps (small, collision-dense Comparable keys), then compare every navigation op
    // across a range of probe keys vs the JDK TreeMap — including the empty-map split (firstKey/lastKey
    // throw NoSuchElementException; firstEntry/lastEntry/ceiling/floor/higher/lower return null) and
    // comparator() == null (natural ordering). Iteration order isn't modeled, but the navigation
    // RESULTS are total functions of the key set + ordering, so they must match exactly.
    test("TreeMap NavigableMap navigation conforms") {
        val entry = Arb.bind(Arb.int(-4..6), Arb.int(-9..9)) { k, v -> k to v }
        checkAll(Arb.list(entry, 0..25)) { pairs ->
            val r = java.util.TreeMap<Any?, Any?>()
            val m = bmcref.java.util.TreeMap<Any?, Any?>()
            for ((k, v) in pairs) { r.put(k, v); m.put(k, v) }

            // comparator() is null for natural ordering on both.
            assertEquivalent("comparator", call(r, "comparator", arrayOf()), call(m, "comparator", arrayOf()))
            // firstKey/lastKey: equal value, or the SAME exception (NoSuchElementException when empty).
            assertEquivalent("firstKey", call(r, "firstKey", arrayOf()), call(m, "firstKey", arrayOf()))
            assertEquivalent("lastKey", call(r, "lastKey", arrayOf()), call(m, "lastKey", arrayOf()))
            // firstEntry/lastEntry: null when empty, else key/value match (entry types differ by relocation).
            assertSameEntry("firstEntry", call(r, "firstEntry", arrayOf()), call(m, "firstEntry", arrayOf()))
            assertSameEntry("lastEntry", call(r, "lastEntry", arrayOf()), call(m, "lastEntry", arrayOf()))
            // ceiling/floor/higher/lower across probe keys spanning below/within/above the key range.
            for (probe in -6..8) {
                for (op in listOf("ceilingKey", "floorKey", "higherKey", "lowerKey")) {
                    assertEquivalent("$op($probe)", call(r, op, arrayOf(OBJECT), probe), call(m, op, arrayOf(OBJECT), probe))
                }
                // The entry-returning navigation family: null when no key qualifies, else key+value match.
                for (op in listOf("ceilingEntry", "floorEntry", "higherEntry", "lowerEntry")) {
                    assertSameEntry("$op($probe)", call(r, op, arrayOf(OBJECT), probe), call(m, op, arrayOf(OBJECT), probe))
                }
            }
        }
    }

    // pollFirstEntry/pollLastEntry: read-and-remove the min/max entry. Drain both maps in lockstep,
    // alternating which end we poll, and after each step the polled entry AND the remaining key set
    // (size + per-key value) must match the JDK — pinning that poll removes exactly the extreme mapping.
    test("TreeMap pollFirstEntry/pollLastEntry conforms") {
        val entry = Arb.bind(Arb.int(-4..6), Arb.int(-9..9)) { k, v -> k to v }
        checkAll(Arb.list(entry, 0..20), Arb.list(Arb.boolean(), 0..25)) { pairs, polls ->
            val r = java.util.TreeMap<Any?, Any?>()
            val m = bmcref.java.util.TreeMap<Any?, Any?>()
            for ((k, v) in pairs) { r.put(k, v); m.put(k, v) }
            for ((i, first) in polls.withIndex()) {
                val op = if (first) "pollFirstEntry" else "pollLastEntry"
                assertSameEntry("poll[$i]=$op", call(r, op, arrayOf()), call(m, op, arrayOf()))
                assertEquivalent("poll[$i].size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
                for (k in -4..6) {
                    assertEquivalent("poll[$i].get($k)", call(r, "get", arrayOf(OBJECT), k), call(m, "get", arrayOf(OBJECT), k))
                }
            }
        }
    }

    // keySet/values/entrySet snapshots: size matches the map, and key/value membership matches the JDK.
    test("keySet/values/entrySet snapshot the map like the JDK") {
        val entry = Arb.bind(Arb.int(-3..5), Arb.int(-9..9)) { k, v -> k to v }
        checkAll(Arb.list(entry, 0..30)) { pairs ->
            val r = java.util.HashMap<Any?, Any?>()
            val m = bmcref.java.util.HashMap<Any?, Any?>()
            for ((k, v) in pairs) { r.put(k, v); m.put(k, v) }
            val rKeys = call(r, "keySet", arrayOf()).getOrThrow()!!
            val mKeys = call(m, "keySet", arrayOf()).getOrThrow()!!
            val rVals = call(r, "values", arrayOf()).getOrThrow()!!
            val mVals = call(m, "values", arrayOf()).getOrThrow()!!
            assertEquivalent("keySet.size", call(rKeys, "size", arrayOf()), call(mKeys, "size", arrayOf()))
            assertEquivalent("values.size", call(rVals, "size", arrayOf()), call(mVals, "size", arrayOf()))
            assertEquivalent("entrySet.size",
                call(call(r, "entrySet", arrayOf()).getOrThrow()!!, "size", arrayOf()),
                call(call(m, "entrySet", arrayOf()).getOrThrow()!!, "size", arrayOf()))
            for (k in -3..5) {
                assertEquivalent("keySet.contains($k)", call(rKeys, "contains", arrayOf(OBJECT), k), call(mKeys, "contains", arrayOf(OBJECT), k))
            }
            for (v in -9..9) {
                assertEquivalent("values.contains($v)", call(rVals, "contains", arrayOf(OBJECT), v), call(mVals, "contains", arrayOf(OBJECT), v))
            }
        }
    }

    // Copy constructor: new map holds the source's mappings (same surface for LinkedHashMap/TreeMap).
    test("HashMap(Map) copies mappings like the JDK") {
        val entry = Arb.bind(Arb.int(-3..5), Arb.int(-9..9)) { k, v -> k to v }
        checkAll(Arb.list(entry, 0..30)) { pairs ->
            val rSrc = java.util.HashMap<Any?, Any?>()
            val mSrc = bmcref.java.util.HashMap<Any?, Any?>()
            for ((k, v) in pairs) { rSrc.put(k, v); mSrc.put(k, v) }
            val r = java.util.HashMap<Any?, Any?>(rSrc)
            val m = bmcref.java.util.HashMap<Any?, Any?>(mSrc)
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            for (k in -3..5) {
                assertEquivalent("get($k)", call(r, "get", arrayOf(OBJECT), k), call(m, "get", arrayOf(OBJECT), k))
            }
        }
    }

    // --- functional-arg ops (compute* / merge / forEach) -------------------------------------------
    // These take a real Function/BiFunction/BiConsumer (function interfaces aren't relocated), so the
    // lambdas pass straight through. We drive a sequence of functional ops on both maps and compare
    // each return + the final state, pinning the classic present-but-null / null-result-removes traps.
    // The lambda return domain includes null (which removes / leaves-absent), exercising those edges.
    test("HashMap functional-arg ops conform (compute/computeIfAbsent/computeIfPresent/merge/replace)") {
        val key = Arb.int(-2..3)
        val arg = Arb.int(-5..5)
        // null result (returned from a remap) is the removal trap; encode it as the sentinel for "null".
        val maybeNull = Arb.int(-5..6).map { if (it == 6) null else it }
        checkAll(Arb.list(Arb.bind(Arb.int(0..6), key, arg, maybeNull) { op, k, a, n -> listOf(op, k, a, n) }, 0..25)) { steps ->
            val r = java.util.HashMap<Int, Int?>()
            val m = bmcref.java.util.HashMap<Int, Int?>()
            for (step in steps) {
                val op = step[0]!!; val k = step[1]!!; val a = step[2]!!; val n = step[3]
                when (op) {
                    0 -> {
                        val rr = runCatching { r.computeIfAbsent(k) { n } }
                        val mm = runCatching { m.computeIfAbsent(k) { n } }
                        mm.getOrNull() shouldBe rr.getOrNull()
                    }
                    1 -> {
                        val rr = runCatching { r.computeIfPresent(k) { _, v -> if (n == null) null else v!! + n } }
                        val mm = runCatching { m.computeIfPresent(k) { _, v -> if (n == null) null else v!! + n } }
                        mm.getOrNull() shouldBe rr.getOrNull()
                    }
                    2 -> {
                        val rr = runCatching { r.compute(k) { _, v -> if (n == null) null else (v ?: 0) + n } }
                        val mm = runCatching { m.compute(k) { _, v -> if (n == null) null else (v ?: 0) + n } }
                        mm.getOrNull() shouldBe rr.getOrNull()
                    }
                    3 -> {
                        val rr = runCatching { r.merge(k, a) { old, value -> if (n == null) null else old!! + value!! } }
                        val mm = runCatching { m.merge(k, a) { old, value -> if (n == null) null else old!! + value!! } }
                        mm.getOrNull() shouldBe rr.getOrNull()
                    }
                    4 -> { r.putIfAbsent(k, a); m.putIfAbsent(k, a) }
                    5 -> { r.replace(k, a); m.replace(k, a) }
                    else -> { r.put(k, a); m.put(k, a) }
                }
                // After each step: same size and same value for every candidate key.
                (call(m, "size", arrayOf()).getOrThrow() as Int) shouldBe r.size
                for (kk in -2..3) m.get(kk) shouldBe r.get(kk)
            }
            // forEach visits the same key/value multiset (sum of values, order-independent).
            val rSum = intArrayOf(0); val mSum = intArrayOf(0)
            r.forEach { _, v -> rSum[0] += (v ?: 0) }
            m.forEach { _, v -> mSum[0] += (v ?: 0) }
            mSum[0] shouldBe rSum[0]
        }
    }

    test("ConcurrentHashMap functional-arg ops conform (merge/compute, null-result removal)") {
        val r = java.util.concurrent.ConcurrentHashMap<Int, Int>()
        val m = bmcref.java.util.concurrent.ConcurrentHashMap<Int, Int>()
        r.put(1, 10); m.put(1, 10)
        r.merge(1, 5) { a, b -> a + b }; m.merge(1, 5) { a, b -> a + b }
        m.get(1) shouldBe r.get(1)   // 15
        // merge returning null removes the key (CHM never stores null), on both.
        r.merge(1, 5) { _, _ -> null }; m.merge(1, 5) { _, _ -> null }
        m.containsKey(1) shouldBe r.containsKey(1)   // false
        // computeIfAbsent installs a non-null, returns it.
        m.computeIfAbsent(2) { 20 } shouldBe r.computeIfAbsent(2) { 20 }
        m.get(2) shouldBe r.get(2)
    }

    test("HashMap(negative capacity) throws like the JDK") {
        val real = runCatching { java.util.HashMap<Any?, Any?>(-1) }
        val model = runCatching { bmcref.java.util.HashMap<Any?, Any?>(-1) }
        model.exceptionOrNull()?.javaClass shouldBe real.exceptionOrNull()?.javaClass
    }
})
