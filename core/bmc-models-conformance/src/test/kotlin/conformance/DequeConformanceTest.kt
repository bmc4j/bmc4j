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
import io.kotest.property.checkAll

/** One reflective operation applied identically to the real ArrayDeque and the model. */
private class DOp(val desc: String, val method: String, val types: Array<Class<*>>, val args: Array<Any?>) {
    fun on(target: Any) = call(target, method, types, *args)
    override fun toString() = desc
}

private val dValue: Arb<Int> = Arb.int(-3..5)   // non-null: ArrayDeque forbids null elements

// The full double-ended + Queue/Stack op surface. Each op is applied to BOTH the JDK ArrayDeque and
// the relocated model; outcomes (value or conforming exception) must match step-for-step.
private val aDequeOp: Arb<DOp> = Arb.choice(
    dValue.map { DOp("addFirst($it)", "addFirst", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("addLast($it)", "addLast", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("offerFirst($it)", "offerFirst", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("offerLast($it)", "offerLast", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("push($it)", "push", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("add($it)", "add", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("offer($it)", "offer", arrayOf(OBJECT), arrayOf(it)) },
    Arb.constant(DOp("removeFirst", "removeFirst", arrayOf(), arrayOf())),
    Arb.constant(DOp("removeLast", "removeLast", arrayOf(), arrayOf())),
    Arb.constant(DOp("pollFirst", "pollFirst", arrayOf(), arrayOf())),
    Arb.constant(DOp("pollLast", "pollLast", arrayOf(), arrayOf())),
    Arb.constant(DOp("pop", "pop", arrayOf(), arrayOf())),
    Arb.constant(DOp("poll", "poll", arrayOf(), arrayOf())),
    Arb.constant(DOp("remove", "remove", arrayOf(), arrayOf())),
    Arb.constant(DOp("peekFirst", "peekFirst", arrayOf(), arrayOf())),
    Arb.constant(DOp("peekLast", "peekLast", arrayOf(), arrayOf())),
    Arb.constant(DOp("getFirst", "getFirst", arrayOf(), arrayOf())),
    Arb.constant(DOp("getLast", "getLast", arrayOf(), arrayOf())),
    Arb.constant(DOp("peek", "peek", arrayOf(), arrayOf())),
    Arb.constant(DOp("element", "element", arrayOf(), arrayOf())),
    dValue.map { DOp("removeObj($it)", "remove", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("removeFirstOccurrence($it)", "removeFirstOccurrence", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("removeLastOccurrence($it)", "removeLastOccurrence", arrayOf(OBJECT), arrayOf(it)) },
    dValue.map { DOp("contains($it)", "contains", arrayOf(OBJECT), arrayOf(it)) },
    Arb.constant(DOp("clear", "clear", arrayOf(), arrayOf())),
)

/** Compare the deques' full ordered contents (head→tail) via toArray(), plus size. */
private fun assertSameDeque(real: Any, model: Any) {
    assertEquivalent("size", call(real, "size", arrayOf()), call(model, "size", arrayOf()))
    val ra = call(real, "toArray", arrayOf()).getOrThrow() as Array<*>
    val ma = call(model, "toArray", arrayOf()).getOrThrow() as Array<*>
    ma.toList() shouldBe ra.toList()
}

/**
 * Differential conformance for the bounded ArrayDeque model vs the JDK ArrayDeque: head/tail
 * insertion+removal, the Queue (FIFO) and Stack (LIFO) aliases, occurrence removal, membership,
 * and the ordered contents. Insertion order is part of the contract here (unlike a Set), so the
 * head→tail element sequence is compared exactly.
 */
class DequeConformanceTest : FunSpec({

    test("ArrayDeque conforms over a random op sequence (ends, Queue/Stack aliases, occurrence removal)") {
        checkAll(Arb.list(aDequeOp, 0..40)) { ops ->
            val r = java.util.ArrayDeque<Any?>()
            val m = bmcref.java.util.ArrayDeque<Any?>()
            ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
            assertSameDeque(r, m)
        }
    }

    // FIFO: a queue built with offer/add then drained with poll comes out in insertion order.
    test("ArrayDeque FIFO: offer-then-poll yields insertion order") {
        checkAll(Arb.list(dValue, 0..20)) { items ->
            val r = java.util.ArrayDeque<Any?>()
            val m = bmcref.java.util.ArrayDeque<Any?>()
            for (x in items) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }
            for (k in items.indices) {
                assertEquivalent("poll[$k]", call(r, "poll", arrayOf()), call(m, "poll", arrayOf()))
            }
            assertEquivalent("emptyPoll", call(r, "poll", arrayOf()), call(m, "poll", arrayOf()))
        }
    }

    // LIFO: a stack built with push then drained with pop comes out in reverse insertion order.
    test("ArrayDeque LIFO: push-then-pop yields reverse insertion order") {
        checkAll(Arb.list(dValue, 0..20)) { items ->
            val r = java.util.ArrayDeque<Any?>()
            val m = bmcref.java.util.ArrayDeque<Any?>()
            for (x in items) { call(r, "push", arrayOf(OBJECT), x); call(m, "push", arrayOf(OBJECT), x) }
            for (k in items.indices) {
                assertEquivalent("pop[$k]", call(r, "pop", arrayOf()), call(m, "pop", arrayOf()))
            }
        }
    }

    // addFirst/addLast positions: after interleaved end-insertion, the head/tail and ordered contents match.
    test("ArrayDeque addFirst/addLast positions conform") {
        val pairs = Arb.bind(Arb.list(dValue, 0..12), Arb.list(dValue, 0..12)) { a, b -> a to b }
        checkAll(pairs) { (fronts, backs) ->
            val r = java.util.ArrayDeque<Any?>()
            val m = bmcref.java.util.ArrayDeque<Any?>()
            for (x in fronts) { call(r, "addFirst", arrayOf(OBJECT), x); call(m, "addFirst", arrayOf(OBJECT), x) }
            for (x in backs) { call(r, "addLast", arrayOf(OBJECT), x); call(m, "addLast", arrayOf(OBJECT), x) }
            assertEquivalent("getFirst", call(r, "getFirst", arrayOf()), call(m, "getFirst", arrayOf()))
            assertEquivalent("getLast", call(r, "getLast", arrayOf()), call(m, "getLast", arrayOf()))
            assertSameDeque(r, m)
        }
    }

    // Empty-deque getFirst/getLast/removeFirst/removeLast/element/pop throw NoSuchElementException on both.
    test("ArrayDeque throwing accessors on empty match the JDK (NoSuchElementException)") {
        val r = java.util.ArrayDeque<Any?>()
        val m = bmcref.java.util.ArrayDeque<Any?>()
        for (method in listOf("getFirst", "getLast", "removeFirst", "removeLast", "element", "pop", "remove")) {
            assertSameException(
                runCatching { call(r, method, arrayOf()).getOrThrow() },
                runCatching { call(m, method, arrayOf()).getOrThrow() },
            )
        }
        // peek*/poll* return null on empty (no throw) on both.
        for (method in listOf("peekFirst", "peekLast", "peek", "pollFirst", "pollLast", "poll")) {
            assertEquivalent(method, call(r, method, arrayOf()), call(m, method, arrayOf()))
        }
    }

    // null elements are rejected (NPE) on both.
    test("ArrayDeque rejects null elements like the JDK") {
        val r = java.util.ArrayDeque<Any?>()
        val m = bmcref.java.util.ArrayDeque<Any?>()
        for (method in listOf("add", "addFirst", "addLast", "offer", "offerFirst", "offerLast", "push")) {
            assertSameException(
                runCatching { call(r, method, arrayOf(OBJECT), null).getOrThrow() },
                runCatching { call(m, method, arrayOf(OBJECT), null).getOrThrow() },
            )
        }
    }

    // Copy constructor: holds the source's elements in iteration order.
    test("ArrayDeque(Collection) copies in iteration order like the JDK") {
        checkAll(Arb.list(dValue, 0..20)) { items ->
            val rSrc = java.util.ArrayList<Any?>(items)
            val mSrc = bmcref.java.util.ArrayList<Any?>().also { for (x in items) it.add(x) }
            val r = java.util.ArrayDeque<Any?>(rSrc)
            val m = bmcref.java.util.ArrayDeque<Any?>(mSrc)
            assertSameDeque(r, m)
        }
    }

    // descendingIterator walks tail→head; compare the produced sequence.
    test("ArrayDeque descendingIterator walks tail-to-head like the JDK") {
        checkAll(Arb.list(dValue, 0..20)) { items ->
            val r = java.util.ArrayDeque<Any?>()
            val m = bmcref.java.util.ArrayDeque<Any?>()
            for (x in items) { call(r, "addLast", arrayOf(OBJECT), x); call(m, "addLast", arrayOf(OBJECT), x) }
            val rIt = call(r, "descendingIterator", arrayOf()).getOrThrow()!!
            val mIt = call(m, "descendingIterator", arrayOf()).getOrThrow()!!
            val n = call(r, "size", arrayOf()).getOrThrow() as Int
            for (k in 0 until n) {
                assertEquivalent("desc[$k]", call(rIt, "next", arrayOf()), call(mIt, "next", arrayOf()))
            }
        }
    }

    // removeIf / forEach (lambdas through the deque), and bulk addAll/removeAll/retainAll.
    test("ArrayDeque removeIf/forEach conform (lambdas)") {
        checkAll(Arb.list(dValue, 0..20)) { items ->
            val r = java.util.ArrayDeque<Int>()
            val m = bmcref.java.util.ArrayDeque<Int>()
            for (x in items) { r.addLast(x); m.addLast(x) }
            val rChanged = r.removeIf { it < 0 }
            val mChanged = m.removeIf { it < 0 }
            mChanged shouldBe rChanged
            val rSum = intArrayOf(0); val mSum = intArrayOf(0)
            r.forEach { rSum[0] += it }
            m.forEach { mSum[0] += it }
            mSum[0] shouldBe rSum[0]
            assertSameDeque(r, m)
        }
    }

    test("ArrayDeque bulk ops conform (addAll/removeAll/retainAll)") {
        val seedAndSource = Arb.bind(Arb.list(dValue, 0..16), Arb.list(dValue, 0..8)) { a, b -> a to b }
        checkAll(seedAndSource) { (seed, src) ->
            for (method in listOf("addAll", "removeAll", "retainAll")) {
                val r = java.util.ArrayDeque<Any?>(); val m = bmcref.java.util.ArrayDeque<Any?>()
                for (x in seed) { r.addLast(x); m.addLast(x) }
                val rSrc = java.util.ArrayList<Any?>(src)
                val mSrc = bmcref.java.util.ArrayList<Any?>().also { for (x in src) it.add(x) }
                assertEquivalent("$method.changed",
                    call(r, method, arrayOf(java.util.Collection::class.java), rSrc),
                    call(m, method, arrayOf(bmcref.java.util.Collection::class.java), mSrc))
                assertSameDeque(r, m)
            }
        }
    }

    // stream()/parallelStream() count + ordered contents.
    test("ArrayDeque stream() conforms (count + ordered elements)") {
        checkAll(Arb.list(dValue, 0..20)) { items ->
            val r = java.util.ArrayDeque<Any?>(); val m = bmcref.java.util.ArrayDeque<Any?>()
            for (x in items) { r.addLast(x); m.addLast(x) }
            assertEquivalent("stream.count",
                call(call(r, "stream", arrayOf()).getOrThrow()!!, "count", arrayOf()),
                call(call(m, "stream", arrayOf()).getOrThrow()!!, "count", arrayOf()))
            val rList = call(call(r, "stream", arrayOf()).getOrThrow()!!, "toList", arrayOf()).getOrThrow() as java.util.List<*>
            val mList = call(call(m, "stream", arrayOf()).getOrThrow()!!, "toList", arrayOf()).getOrThrow()!!
            val mn = call(mList, "size", arrayOf()).getOrThrow() as Int
            val mElems = (0 until mn).map { call(mList, "get", arrayOf(INT), it).getOrThrow() }
            mElems shouldBe rList.toList()
        }
    }
})
