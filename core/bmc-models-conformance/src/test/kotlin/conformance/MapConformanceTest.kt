package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
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
        Arb.bind(key, Arb.int(-9..9)) { k, d -> MapOp("getOrDefault($k,$d)", "getOrDefault", arrayOf(OBJECT, OBJECT), arrayOf(k, d)) },
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

    test("HashMap(negative capacity) throws like the JDK") {
        val real = runCatching { java.util.HashMap<Any?, Any?>(-1) }
        val model = runCatching { bmcref.java.util.HashMap<Any?, Any?>(-1) }
        model.exceptionOrNull()?.javaClass shouldBe real.exceptionOrNull()?.javaClass
    }
})
