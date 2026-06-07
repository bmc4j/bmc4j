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

/** A relocated-model ArrayList holding the given elements (built element-by-element, no copy-ctor). */
private fun modelList(items: List<Int>): bmcref.java.util.ArrayList<Int> {
    val l = bmcref.java.util.ArrayList<Int>()
    for (x in items) l.add(x)
    return l
}

/** Assert the real list and the relocated model hold the same elements, in the same order. */
private fun assertSameElements(real: Any, model: Any) {
    val rn = call(real, "size", arrayOf()).getOrThrow() as Int
    assertEquivalent("size", call(real, "size", arrayOf()), call(model, "size", arrayOf()))
    for (i in 0 until rn) {
        assertEquivalent("get[$i]", call(real, "get", arrayOf(INT), i), call(model, "get", arrayOf(INT), i))
    }
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

    // --- LinkedList Deque/Queue surface ------------------------------------------------------------
    // The deque/queue ops (addFirst/addLast/get/peek/poll/removeFirst/removeLast/push/pop/offer) over
    // the same bounded backing array, differentially vs the JDK LinkedList — including the empty-list
    // exception/null split (getFirst/removeFirst/pop throw NoSuchElementException; peek/poll → null)
    // and the interplay with the inherited List surface (addFirst then get(0), addLast then get(last)).
    test("LinkedList Deque/Queue surface conforms") {
        val v: Arb<Int?> = Arb.int(-3..5).orNull(0.1)
        val dequeOp: Arb<Op> = Arb.choice(
            v.map { Op("addFirst($it)", "addFirst", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("addLast($it)", "addLast", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("offer($it)", "offer", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("offerFirst($it)", "offerFirst", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("offerLast($it)", "offerLast", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("push($it)", "push", arrayOf(OBJECT), arrayOf(it)) },
            Arb.constant(Op("getFirst", "getFirst", arrayOf(), arrayOf())),
            Arb.constant(Op("getLast", "getLast", arrayOf(), arrayOf())),
            Arb.constant(Op("peek", "peek", arrayOf(), arrayOf())),
            Arb.constant(Op("peekFirst", "peekFirst", arrayOf(), arrayOf())),
            Arb.constant(Op("peekLast", "peekLast", arrayOf(), arrayOf())),
            Arb.constant(Op("element", "element", arrayOf(), arrayOf())),
            Arb.constant(Op("poll", "poll", arrayOf(), arrayOf())),
            Arb.constant(Op("pollFirst", "pollFirst", arrayOf(), arrayOf())),
            Arb.constant(Op("pollLast", "pollLast", arrayOf(), arrayOf())),
            Arb.constant(Op("removeFirst", "removeFirst", arrayOf(), arrayOf())),
            Arb.constant(Op("removeLast", "removeLast", arrayOf(), arrayOf())),
            Arb.constant(Op("pop", "pop", arrayOf(), arrayOf())),
            Arb.constant(Op("remove", "remove", arrayOf(), arrayOf())),
        )
        checkAll(Arb.list(dequeOp, 0..40)) { ops ->
            val r = java.util.LinkedList<Any?>()
            val m = bmcref.java.util.LinkedList<Any?>()
            ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            val n = call(r, "size", arrayOf()).getOrThrow() as Int
            for (idx in 0 until n) {
                assertEquivalent("get[$idx]", call(r, "get", arrayOf(INT), idx), call(m, "get", arrayOf(INT), idx))
            }
        }
    }

    // --- bulk ops (addAll / removeAll / retainAll / removeIf / forEach / toArray) -------------------
    // Build two seed lists identically, then apply a bulk op against a source collection and compare
    // the resulting elements + the boolean "changed" return, vs the JDK ArrayList/LinkedList.
    test("ArrayList/LinkedList bulk ops conform") {
        val seedAndSource = Arb.bind(
            Arb.list(Arb.int(-3..5), 0..20),
            Arb.list(Arb.int(-3..5), 0..8),
        ) { a, b -> a to b }
        checkAll(seedAndSource) { (seed, src) ->
            // addAll appends the source collection.
            run {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                assertEquivalent("addAll.changed",
                    call(r, "addAll", arrayOf(java.util.Collection::class.java), java.util.ArrayList<Int>(src)),
                    call(m, "addAll", arrayOf(bmcref.java.util.Collection::class.java), modelList(src)))
                assertSameElements(r, m)
            }
            // removeAll / retainAll vs a source collection.
            for (method in listOf("removeAll", "retainAll")) {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                assertEquivalent("$method.changed",
                    call(r, method, arrayOf(java.util.Collection::class.java), java.util.ArrayList<Int>(src)),
                    call(m, method, arrayOf(bmcref.java.util.Collection::class.java), modelList(src)))
                assertSameElements(r, m)
            }
            // toArray contents in order.
            run {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                val ra = call(r, "toArray", arrayOf()).getOrThrow() as Array<*>
                val ma = call(m, "toArray", arrayOf()).getOrThrow() as Array<*>
                ma.toList() shouldBe ra.toList()
            }
        }
    }

    // removeIf/forEach take a lambda; exercise them directly (not via reflection) since the SAM type
    // differs between the JDK and the relocated model.
    test("ArrayList removeIf/forEach conform") {
        checkAll(Arb.list(Arb.int(-3..5), 0..20)) { seed ->
            val r = java.util.ArrayList<Int>(); val m = bmcref.java.util.ArrayList<Int>()
            for (x in seed) { r.add(x); m.add(x) }
            val rChanged = r.removeIf { it < 0 }
            val mChanged = m.removeIf { it < 0 }
            mChanged shouldBe rChanged
            val rSum = intArrayOf(0); val mSum = intArrayOf(0)
            r.forEach { rSum[0] += it }
            m.forEach { mSum[0] += it }
            mSum[0] shouldBe rSum[0]
            (call(m, "size", arrayOf()).getOrThrow() as Int) shouldBe r.size
            for (i in 0 until r.size) call(m, "get", arrayOf(INT), i).getOrThrow() shouldBe r[i]
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
