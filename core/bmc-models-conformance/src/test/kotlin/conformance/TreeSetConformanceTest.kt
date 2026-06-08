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
import io.kotest.property.checkAll

/**
 * Differential conformance for the [java.util.TreeSet] model (composed over the TreeMap model: a set is
 * a map to a dummy value). Compares add/remove/contains/size/clear over an op-sequence, the copy
 * constructor, the bulk/functional ops, the navigable single-element surface (first/last/ceiling/floor/
 * higher/lower/pollFirst/pollLast), and — because a TreeSet's iteration IS sorted (unlike HashSet) —
 * the ASCENDING and DESCENDING element order, all vs the real JDK TreeSet.
 *
 * Honors the documented divergences MIRRORED from the TreeMap model: non-null elements only (the
 * array-backed map does not reproduce the JDK's null-element NullPointerException), natural ordering by
 * the SIGN of compareTo, and comparator() == null.
 */
private class TSetOp(val desc: String, val method: String, val types: Array<Class<*>>, val args: Array<Any?>) {
    fun on(t: Any) = call(t, method, types, *args)
    override fun toString() = desc
}

private val tsetOp: Arb<TSetOp> = run {
    // Small, collision-dense Comparable domain incl. negatives (no null — TreeSet's null divergence is
    // documented + unexercised). Repeats/dedup/removal are what exercise the model.
    val e: Arb<Int> = Arb.int(-3..5)
    Arb.choice(
        e.map { TSetOp("add($it)", "add", arrayOf(OBJECT), arrayOf(it)) },
        e.map { TSetOp("contains($it)", "contains", arrayOf(OBJECT), arrayOf(it)) },
        e.map { TSetOp("remove($it)", "remove", arrayOf(OBJECT), arrayOf(it)) },
        Arb.constant(TSetOp("clear", "clear", arrayOf(), arrayOf())),
    )
}

/** Drain a relocated-model iterator (from iterator()/descendingIterator()) into a list via hasNext/next. */
private fun drain(iterator: Any): List<Any?> {
    val out = ArrayList<Any?>()
    while (call(iterator, "hasNext", arrayOf()).getOrThrow() == true) {
        out.add(call(iterator, "next", arrayOf()).getOrThrow())
    }
    return out
}

/** The MODEL set's elements in ascending order, read via its iterator() (relocated type → reflective). */
private fun modelAscending(model: Any): List<Any?> = drain(call(model, "iterator", arrayOf()).getOrThrow()!!)

/** The MODEL set's elements in descending order, read via its descendingIterator(). */
private fun modelDescending(model: Any): List<Any?> = drain(call(model, "descendingIterator", arrayOf()).getOrThrow()!!)

class TreeSetConformanceTest : FunSpec({

    test("TreeSet conforms on add/remove/contains/size (+ sorted iteration order)") {
        checkAll(Arb.list(tsetOp, 0..40)) { ops ->
            val r = java.util.TreeSet<Any?>()
            val m = bmcref.java.util.TreeSet<Any?>()
            ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            assertEquivalent("isEmpty", call(r, "isEmpty", arrayOf()), call(m, "isEmpty", arrayOf()))
            for (e in -4..6) {
                assertEquivalent("contains($e)", call(r, "contains", arrayOf(OBJECT), e), call(m, "contains", arrayOf(OBJECT), e))
            }
            // TreeSet iterates in ascending order — compare the ordered element list element-by-element.
            modelAscending(m) shouldBe r.toList()
            // descendingIterator yields the reverse order.
            modelDescending(m) shouldBe r.descendingIterator().asSequence().toList()
        }
    }

    // Copy constructor: dedups + sorts the source (by compareTo sign), like the JDK.
    test("TreeSet(Collection) dedups+sorts the source like the JDK") {
        checkAll(Arb.list(Arb.int(-3..5), 0..30)) { items ->
            val rSrc = java.util.ArrayList<Any?>()
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in items) { rSrc.add(x); mSrc.add(x) }
            val r = java.util.TreeSet<Any?>(rSrc)
            val m = bmcref.java.util.TreeSet<Any?>(mSrc)
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            modelAscending(m) shouldBe r.toList()
        }
    }

    // --- NavigableSet single-element navigation (comparator/first/last/ceiling/floor/higher/lower) --
    // Build identical sets, then compare every navigation op across probe elements spanning below/
    // within/above the element range vs the JDK TreeSet — including the empty-set split (first/last
    // throw NoSuchElementException; the bound family returns null) and comparator() == null.
    test("TreeSet NavigableSet navigation conforms") {
        checkAll(Arb.list(Arb.int(-4..6), 0..25)) { items ->
            val r = java.util.TreeSet<Any?>()
            val m = bmcref.java.util.TreeSet<Any?>()
            for (x in items) { r.add(x); m.add(x) }
            assertEquivalent("comparator", call(r, "comparator", arrayOf()), call(m, "comparator", arrayOf()))
            // first/last: equal value, or the SAME exception (NoSuchElementException when empty).
            assertEquivalent("first", call(r, "first", arrayOf()), call(m, "first", arrayOf()))
            assertEquivalent("last", call(r, "last", arrayOf()), call(m, "last", arrayOf()))
            for (probe in -6..8) {
                for (op in listOf("ceiling", "floor", "higher", "lower")) {
                    assertEquivalent("$op($probe)", call(r, op, arrayOf(OBJECT), probe), call(m, op, arrayOf(OBJECT), probe))
                }
            }
        }
    }

    // pollFirst/pollLast: read-and-remove the min/max element. Drain both in lockstep alternating ends;
    // after each step the polled element AND the remaining ordered element list must match the JDK.
    test("TreeSet pollFirst/pollLast conforms") {
        checkAll(Arb.list(Arb.int(-4..6), 0..20), Arb.list(Arb.boolean(), 0..25)) { items, polls ->
            val r = java.util.TreeSet<Any?>()
            val m = bmcref.java.util.TreeSet<Any?>()
            for (x in items) { r.add(x); m.add(x) }
            for ((i, first) in polls.withIndex()) {
                val op = if (first) "pollFirst" else "pollLast"
                assertEquivalent("poll[$i]=$op", call(r, op, arrayOf()), call(m, op, arrayOf()))
                assertEquivalent("poll[$i].size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
                modelAscending(m) shouldBe r.toList()
            }
        }
    }

    // --- bulk ops (addAll/removeAll/retainAll) ------------------------------------------------------
    test("TreeSet bulk ops conform (addAll/removeAll/retainAll)") {
        val seedAndSource = Arb.bind(
            Arb.list(Arb.int(-3..5), 0..20),
            Arb.list(Arb.int(-3..5), 0..8),
        ) { a, b -> a to b }
        checkAll(seedAndSource) { (seed, src) ->
            fun seeded(make: () -> Any): Any { val s = make(); for (x in seed) call(s, "add", arrayOf(OBJECT), x); return s }
            for (method in listOf("addAll", "removeAll", "retainAll")) {
                val r = seeded { java.util.TreeSet<Any?>() }
                val m = seeded { bmcref.java.util.TreeSet<Any?>() }
                val rSrc = java.util.ArrayList<Any?>(src)
                val mSrc = bmcref.java.util.ArrayList<Any?>()
                for (x in src) mSrc.add(x)
                assertEquivalent("$method.changed",
                    call(r, method, arrayOf(java.util.Collection::class.java), rSrc),
                    call(m, method, arrayOf(bmcref.java.util.Collection::class.java), mSrc))
                assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
                modelAscending(m) shouldBe (r as java.util.TreeSet<*>).toList()
            }
        }
    }

    // removeIf/forEach take a lambda (exercised directly). forEach visits in ascending order; sum is
    // order-independent but the resulting membership/order after removeIf must match.
    test("TreeSet removeIf/forEach conform (lambdas)") {
        checkAll(Arb.list(Arb.int(-3..5), 0..20)) { seed ->
            val r = java.util.TreeSet<Int>(); val m = bmcref.java.util.TreeSet<Int>()
            for (x in seed) { r.add(x); m.add(x) }
            (m.removeIf { it < 0 }) shouldBe r.removeIf { it < 0 }
            val rSum = intArrayOf(0); val mSum = intArrayOf(0)
            r.forEach { rSum[0] += it }
            m.forEach { mSum[0] += it }
            mSum[0] shouldBe rSum[0]
            (call(m, "size", arrayOf()).getOrThrow() as Int) shouldBe r.size
            modelAscending(m) shouldBe r.toList()
        }
    }

    // stream(): count == size, and the (sorted) element list matches the JDK in order.
    test("TreeSet stream() conforms (count + ordered elements)") {
        checkAll(Arb.list(Arb.int(-3..5), 0..30)) { items ->
            val r = java.util.TreeSet<Any?>(); val m = bmcref.java.util.TreeSet<Any?>()
            for (x in items) { r.add(x); m.add(x) }
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
