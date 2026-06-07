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

/** One reflective operation applied identically to the real list and the model. */
private class Op(val desc: String, val method: String, val types: Array<Class<*>>, val args: Array<Any?>) {
    fun on(target: Any) = call(target, method, types, *args)
    override fun toString() = desc
}

private val value: Arb<Int?> = Arb.int(-3..5).orNull(0.1)  // small, collision-dense domain incl. negatives + null
private val index: Arb<Int> = Arb.int(-1..8)               // includes out-of-range, to compare exceptions

private val anOp: Arb<Op> = Arb.choice(
    value.map { Op("add($it)", "add", arrayOf(OBJECT), arrayOf(it)) },
    index.map { Op("get($it)", "get", arrayOf(INT), arrayOf(it)) },
    Arb.bind(index, value) { i, v -> Op("set($i,$v)", "set", arrayOf(INT, OBJECT), arrayOf(i, v)) },
    index.map { Op("remove($it)", "remove", arrayOf(INT), arrayOf(it)) },
    value.map { Op("removeObj($it)", "remove", arrayOf(OBJECT), arrayOf(it)) },  // Collection.remove(Object)
    value.map { Op("indexOf($it)", "indexOf", arrayOf(OBJECT), arrayOf(it)) },
    value.map { Op("contains($it)", "contains", arrayOf(OBJECT), arrayOf(it)) },
    Arb.constant(Op("clear", "clear", arrayOf(), arrayOf())),
)

private suspend fun checkList(real: () -> Any, model: () -> Any) {
    checkAll(Arb.list(anOp, 0..40)) { ops ->
        val r = real()
        val m = model()
        ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
        // Final state: size then element-by-element.
        assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
        val n = call(r, "size", arrayOf()).getOrThrow() as Int
        for (idx in 0 until n) {
            assertEquivalent("get[$idx]", call(r, "get", arrayOf(INT), idx), call(m, "get", arrayOf(INT), idx))
        }
    }
}

/**
 * Differential conformance for the List models vs the JDK. Sequences are capped at 40 ops (well
 * under the model's documented capacity of 64 — crossing that bound is a separate concern), so
 * within-bound behavior must match exactly. LinkedList is an array-backed model of the same surface.
 */
class ArrayListConformanceTest : FunSpec({

    test("ArrayList conforms") {
        checkList({ java.util.ArrayList<Any?>() }, { bmcref.java.util.ArrayList<Any?>() })
    }

    test("LinkedList conforms") {
        checkList({ java.util.LinkedList<Any?>() }, { bmcref.java.util.LinkedList<Any?>() })
    }

    test("CopyOnWriteArrayList conforms (sequential)") {
        checkList({ java.util.concurrent.CopyOnWriteArrayList<Any?>() }, { bmcref.java.util.concurrent.CopyOnWriteArrayList<Any?>() })
    }

    // Copy constructor: new list holds the source's elements in iteration order (same surface for
    // the LinkedList model). Source is built element-by-element so real and model see identical input.
    test("ArrayList(Collection) copies in order like the JDK") {
        checkAll(Arb.list(value, 0..30)) { items ->
            val rSrc = java.util.ArrayList<Any?>()
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in items) { rSrc.add(x); mSrc.add(x) }
            val r = java.util.ArrayList<Any?>(rSrc)
            val m = bmcref.java.util.ArrayList<Any?>(mSrc)
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            val n = call(r, "size", arrayOf()).getOrThrow() as Int
            for (idx in 0 until n) {
                assertEquivalent("get[$idx]", call(r, "get", arrayOf(INT), idx), call(m, "get", arrayOf(INT), idx))
            }
        }
    }

    test("LinkedList(Collection) copies in order like the JDK") {
        checkAll(Arb.list(value, 0..30)) { items ->
            val rSrc = java.util.ArrayList<Any?>()
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in items) { rSrc.add(x); mSrc.add(x) }
            val r = java.util.LinkedList<Any?>(rSrc)
            val m = bmcref.java.util.LinkedList<Any?>(mSrc)
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            val n = call(r, "size", arrayOf()).getOrThrow() as Int
            for (idx in 0 until n) {
                assertEquivalent("get[$idx]", call(r, "get", arrayOf(INT), idx), call(m, "get", arrayOf(INT), idx))
            }
        }
    }

    // Divergence ledger — "everything else is a bug": ArrayList(int) must reject a negative capacity
    // like the JDK (IllegalArgumentException), not silently succeed.
    test("ArrayList(negative capacity) throws like the JDK") {
        val real = runCatching { java.util.ArrayList<Any?>(-1) }
        val model = runCatching { bmcref.java.util.ArrayList<Any?>(-1) }
        model.exceptionOrNull()?.javaClass shouldBe real.exceptionOrNull()?.javaClass
    }

    // --- OUT-OF-DOMAIN: fixed-capacity bound (documented CAPACITY = 64) ----------------------------
    // Bounded-model loud-failure, NOT JDK parity: the JDK grows unboundedly and SUCCEEDS; the
    // fixed-capacity model must FAIL LOUDLY (the backing-array write throws) when adds exceed its
    // documented capacity, rather than silently corrupting/dropping. We add past 64 and assert the
    // model throws while the JDK does not.
    test("adding past the documented capacity fails LOUDLY (bounded-model loud-failure)") {
        val m = bmcref.java.util.ArrayList<Any?>()
        val r = java.util.ArrayList<Any?>()
        val overflow = runCatching {
            for (i in 0 until 70) {        // 70 > CAPACITY (64)
                m.add(i)
            }
        }
        for (i in 0 until 70) r.add(i)     // JDK grows — succeeds
        r.size shouldBe 70
        overflow.isFailure shouldBe true   // model is loud past its bound
    }
})
