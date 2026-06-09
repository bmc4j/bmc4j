package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Differential conformance for the bounded PriorityQueue model vs the JDK PriorityQueue. The model
 * backs an UNORDERED array and selects the least element by a linear scan, so iteration order is NOT
 * modeled (the JDK's iterator is heap-array order, also unspecified) — only the CONTRACT is compared:
 * the head (peek) is the least element, successive polls come out in non-decreasing order, size, and
 * membership. Driven under BOTH a lambda Comparator and natural order, over Integer and String.
 */
class PriorityQueueConformanceTest : FunSpec({

    // Drain both queues fully via poll and compare the emitted sequences — this is the PQ contract
    // (least-first), independent of internal storage order.
    fun assertSamePollSequence(real: Any, model: Any) {
        val n = call(real, "size", arrayOf()).getOrThrow() as Int
        assertEquivalent("size", call(real, "size", arrayOf()), call(model, "size", arrayOf()))
        for (k in 0 until n) {
            assertEquivalent("poll[$k]", call(real, "poll", arrayOf()), call(model, "poll", arrayOf()))
        }
        // Both empty now: peek/poll null, size 0.
        assertEquivalent("peekAfterDrain", call(real, "peek", arrayOf()), call(model, "peek", arrayOf()))
        assertEquivalent("pollAfterDrain", call(real, "poll", arrayOf()), call(model, "poll", arrayOf()))
    }

    // --- natural order (no comparator), Integer elements ----------------------------------------
    test("PriorityQueue natural-order Int: poll emits non-decreasing, peek is the minimum") {
        checkAll(Arb.list(Arb.int(-20..20), 0..24)) { items ->
            val r = java.util.PriorityQueue<Any?>()
            val m = bmcref.java.util.PriorityQueue<Any?>()
            for (x in items) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }
            // peek is the minimum (and does not remove).
            assertEquivalent("peek", call(r, "peek", arrayOf()), call(m, "peek", arrayOf()))
            assertEquivalent("sizeAfterPeek", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            assertSamePollSequence(r, m)
        }
    }

    // --- natural order, String elements (builtin Comparable via the model's instanceof ladder) ---
    test("PriorityQueue natural-order String: poll emits lexicographic non-decreasing") {
        val words = Arb.element("", "a", "b", "c", "aa", "ab", "ba", "bb", "abc", "cab")
        checkAll(Arb.list(words, 0..16)) { items ->
            val r = java.util.PriorityQueue<Any?>()
            val m = bmcref.java.util.PriorityQueue<Any?>()
            for (x in items) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }
            assertEquivalent("peek", call(r, "peek", arrayOf()), call(m, "peek", arrayOf()))
            assertSamePollSequence(r, m)
        }
    }

    // --- lambda Comparator (reverse order) -------------------------------------------------------
    test("PriorityQueue with a lambda Comparator (reverse): poll emits non-increasing") {
        checkAll(Arb.list(Arb.int(-20..20), 0..24)) { items ->
            val rPq = java.util.PriorityQueue<Any?>(Comparator<Any?> { a, b -> (b as Int).compareTo(a as Int) })
            val mPq = bmcref.java.util.PriorityQueue<Any?>(Comparator<Any?> { a, b -> (b as Int).compareTo(a as Int) })
            for (x in items) { call(rPq, "offer", arrayOf(OBJECT), x); call(mPq, "offer", arrayOf(OBJECT), x) }
            assertEquivalent("peek", call(rPq, "peek", arrayOf()), call(mPq, "peek", arrayOf()))
            assertSamePollSequence(rPq, mPq)
        }
    }

    // --- peek does not remove --------------------------------------------------------------------
    test("PriorityQueue peek does not remove (size unchanged), element/remove on empty throw") {
        checkAll(Arb.list(Arb.int(-10..10), 1..16)) { items ->
            val r = java.util.PriorityQueue<Any?>(); val m = bmcref.java.util.PriorityQueue<Any?>()
            for (x in items) { r.offer(x); call(m, "offer", arrayOf(OBJECT), x) }
            assertEquivalent("peek1", call(r, "peek", arrayOf()), call(m, "peek", arrayOf()))
            assertEquivalent("peek2", call(r, "peek", arrayOf()), call(m, "peek", arrayOf()))
            assertEquivalent("sizeUnchanged", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
        }
    }

    test("PriorityQueue element/remove on empty throw NoSuchElementException like the JDK") {
        val r = java.util.PriorityQueue<Any?>(); val m = bmcref.java.util.PriorityQueue<Any?>()
        for (method in listOf("element", "remove")) {
            assertSameException(
                runCatching { call(r, method, arrayOf()).getOrThrow() },
                runCatching { call(m, method, arrayOf()).getOrThrow() },
            )
        }
        // peek/poll return null on empty.
        assertEquivalent("peek", call(r, "peek", arrayOf()), call(m, "peek", arrayOf()))
        assertEquivalent("poll", call(r, "poll", arrayOf()), call(m, "poll", arrayOf()))
    }

    // --- size / contains / clear / remove(Object) ------------------------------------------------
    test("PriorityQueue size/contains/remove(Object)/clear conform") {
        checkAll(Arb.list(Arb.int(-5..5), 0..20)) { items ->
            val r = java.util.PriorityQueue<Any?>(); val m = bmcref.java.util.PriorityQueue<Any?>()
            for (x in items) { r.offer(x); call(m, "offer", arrayOf(OBJECT), x) }
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            for (e in -5..5) {
                assertEquivalent("contains($e)", call(r, "contains", arrayOf(OBJECT), e), call(m, "contains", arrayOf(OBJECT), e))
            }
            // remove(Object) the first occurrence of a present value (if any), compare boolean + size.
            val v = items.firstOrNull() ?: 0
            assertEquivalent("removeObj($v)", call(r, "remove", arrayOf(OBJECT), v), call(m, "remove", arrayOf(OBJECT), v))
            assertEquivalent("sizeAfterRemove", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            call(r, "clear", arrayOf()); call(m, "clear", arrayOf())
            assertEquivalent("emptyAfterClear", call(r, "isEmpty", arrayOf()), call(m, "isEmpty", arrayOf()))
        }
    }

    // --- comparator() accessor -------------------------------------------------------------------
    test("PriorityQueue comparator() returns null for natural order, the comparator otherwise") {
        // natural order → null on both
        val rn = java.util.PriorityQueue<Any?>()
        val mn = bmcref.java.util.PriorityQueue<Any?>()
        (call(rn, "comparator", arrayOf()).getOrThrow() == null) shouldBe true
        (call(mn, "comparator", arrayOf()).getOrThrow() == null) shouldBe true
        // comparator-provided → non-null on both
        val cmp = Comparator<Any?> { a, b -> (a as Int).compareTo(b as Int) }
        val rc = java.util.PriorityQueue<Any?>(cmp)
        val mc = bmcref.java.util.PriorityQueue<Any?>(cmp)
        (call(rc, "comparator", arrayOf()).getOrThrow() != null) shouldBe true
        (call(mc, "comparator", arrayOf()).getOrThrow() != null) shouldBe true
    }

    // --- copy constructor (natural order) --------------------------------------------------------
    test("PriorityQueue(Collection) is a natural-order queue over the source") {
        checkAll(Arb.list(Arb.int(-10..10), 0..20)) { items ->
            val rSrc = java.util.ArrayList<Any?>(items)
            val mSrc = bmcref.java.util.ArrayList<Any?>().also { for (x in items) it.add(x) }
            val r = java.util.PriorityQueue<Any?>(rSrc)
            val m = bmcref.java.util.PriorityQueue<Any?>(mSrc)
            assertEquivalent("peek", call(r, "peek", arrayOf()), call(m, "peek", arrayOf()))
            assertSamePollSequence(r, m)
        }
    }
})
