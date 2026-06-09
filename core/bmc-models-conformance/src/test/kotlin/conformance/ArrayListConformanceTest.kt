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
            v.map { Op("removeFirstOccurrence($it)", "removeFirstOccurrence", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("removeLastOccurrence($it)", "removeLastOccurrence", arrayOf(OBJECT), arrayOf(it)) },
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

    // --- ArrayList SequencedCollection head/tail ops (Java 21+) -------------------------------------
    // getFirst/getLast/addFirst/addLast/removeFirst/removeLast + lastIndexOf over the backing array,
    // differentially vs the JDK ArrayList — including the empty-list NoSuchElementException on the
    // get/remove ops and the interplay with the inherited List surface (addFirst then get(0), addLast
    // then get(last)). The LinkedList model inherits these unchanged, so this also pins LinkedList's
    // reconciled (inherited, not overridden) head/tail behavior.
    test("ArrayList SequencedCollection head/tail ops conform") {
        val v: Arb<Int?> = Arb.int(-3..5).orNull(0.1)
        val seqOp: Arb<Op> = Arb.choice(
            v.map { Op("addFirst($it)", "addFirst", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("addLast($it)", "addLast", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("add($it)", "add", arrayOf(OBJECT), arrayOf(it)) },
            v.map { Op("lastIndexOf($it)", "lastIndexOf", arrayOf(OBJECT), arrayOf(it)) },
            Arb.constant(Op("getFirst", "getFirst", arrayOf(), arrayOf())),
            Arb.constant(Op("getLast", "getLast", arrayOf(), arrayOf())),
            Arb.constant(Op("removeFirst", "removeFirst", arrayOf(), arrayOf())),
            Arb.constant(Op("removeLast", "removeLast", arrayOf(), arrayOf())),
        )
        checkAll(Arb.list(seqOp, 0..40)) { ops ->
            // Run identical op sequences against the JDK ArrayList and LinkedList, and both models.
            for ((real, model) in listOf(
                { java.util.ArrayList<Any?>() } to { bmcref.java.util.ArrayList<Any?>() },
                { java.util.LinkedList<Any?>() } to { bmcref.java.util.LinkedList<Any?>() },
            )) {
                val r = real(); val m = model()
                ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
                assertSameElements(r, m)
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

    // --- positional add / addAll(int,Collection) / containsAll -------------------------------------
    // add(index,e) and addAll(index,c) shift the tail right; containsAll reuses contains. Compared vs
    // the JDK ArrayList/LinkedList incl. out-of-range index exceptions (index in -1..size+1).
    test("ArrayList/LinkedList positional add / addAll(int) / containsAll conform") {
        val seedAndArgs = Arb.bind(
            Arb.list(Arb.int(-3..5), 0..15),
            Arb.int(-1..16),
            Arb.list(Arb.int(-3..5), 0..6),
        ) { seed, idx, src -> Triple(seed, idx, src) }
        checkAll(seedAndArgs) { (seed, idx, src) ->
            for ((real, model) in listOf(
                { java.util.ArrayList<Any?>() } to { bmcref.java.util.ArrayList<Any?>() },
                { java.util.LinkedList<Any?>() } to { bmcref.java.util.LinkedList<Any?>() },
            )) {
                // containsAll: every element of src present?
                run {
                    val r = real(); val m = model()
                    for (x in seed) { call(r, "add", arrayOf(OBJECT), x); call(m, "add", arrayOf(OBJECT), x) }
                    assertEquivalent("containsAll",
                        call(r, "containsAll", arrayOf(java.util.Collection::class.java), java.util.ArrayList<Any?>(src)),
                        call(m, "containsAll", arrayOf(bmcref.java.util.Collection::class.java), bmcref.java.util.ArrayList<Any?>().also { for (x in src) it.add(x) }))
                }
                // add(index, element): same exception or same resulting elements.
                run {
                    val r = real(); val m = model()
                    for (x in seed) { call(r, "add", arrayOf(OBJECT), x); call(m, "add", arrayOf(OBJECT), x) }
                    assertEquivalent("add($idx,99)",
                        call(r, "add", arrayOf(INT, OBJECT), idx, 99),
                        call(m, "add", arrayOf(INT, OBJECT), idx, 99))
                    assertSameElements(r, m)
                }
                // addAll(index, collection): same exception or same resulting elements + boolean return.
                run {
                    val r = real(); val m = model()
                    for (x in seed) { call(r, "add", arrayOf(OBJECT), x); call(m, "add", arrayOf(OBJECT), x) }
                    assertEquivalent("addAll($idx,src)",
                        call(r, "addAll", arrayOf(INT, java.util.Collection::class.java), idx, java.util.ArrayList<Any?>(src)),
                        call(m, "addAll", arrayOf(INT, bmcref.java.util.Collection::class.java), idx, bmcref.java.util.ArrayList<Any?>().also { for (x in src) it.add(x) }))
                    assertSameElements(r, m)
                }
            }
        }
    }

    // replaceAll(UnaryOperator) maps each element in place; take a lambda directly (SAM type differs).
    test("ArrayList replaceAll conforms") {
        checkAll(Arb.list(Arb.int(-3..5), 0..20)) { seed ->
            val r = java.util.ArrayList<Int>(); val m = bmcref.java.util.ArrayList<Int>()
            for (x in seed) { r.add(x); m.add(x) }
            r.replaceAll { it * 2 - 1 }
            m.replaceAll { it * 2 - 1 }
            (call(m, "size", arrayOf()).getOrThrow() as Int) shouldBe r.size
            for (i in 0 until r.size) call(m, "get", arrayOf(INT), i).getOrThrow() shouldBe r[i]
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

    // --- view ops (subList / reversed / listIterator) + capacity ops + parallelStream --------------
    // The live views (subList window, reversed view, listIterator cursor) write through to the backing
    // list exactly like the JDK's AbstractList views; the capacity ops are observable no-ops; and
    // parallelStream is the sequential stream. Compared element-by-element vs the JDK ArrayList on a
    // real JVM (this is the differential axis the @BmcModelConforms reasons reference — under JBMC the
    // returned-view mutation is a devirtualization artifact, so it is pinned HERE rather than as a proof).
    test("ArrayList view ops (subList/reversed/listIterator) + capacity ops conform") {
        checkAll(Arb.list(Arb.int(-3..5), 0..20)) { seed ->
            // The model's view/iterator objects implement the RELOCATED interfaces (bmcref.java.util.*),
            // not the JDK ones, so they're driven reflectively via call(...) — never cast to a JDK type.
            // READS are compared element-by-element vs the JDK view; WRITE-THROUGH is validated on the
            // model alone (mutate the view, observe it in the parent) — the JDK's own view-mutability is
            // path/version-dependent (reversed() can return an unmodifiable view) and not the property
            // under test here.

            // subList: a live forward window — reads match the JDK window; set writes through to parent.
            run {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                if (r.size >= 2) {
                    val rSub = r.subList(1, r.size)
                    val mSub = call(m, "subList", arrayOf(INT, INT), 1, r.size).getOrThrow()!!
                    assertEquivalent("subList.size", call(rSub, "size", arrayOf()), call(mSub, "size", arrayOf()))
                    for (i in 0 until (r.size - 1)) {
                        assertEquivalent("subList.get[$i]", call(rSub, "get", arrayOf(INT), i), call(mSub, "get", arrayOf(INT), i))
                    }
                    // write-through: model subList index 0 == parent index 1.
                    call(mSub, "set", arrayOf(INT, OBJECT), 0, 99).getOrThrow()
                    (call(m, "get", arrayOf(INT), 1).getOrThrow()) shouldBe 99
                }
            }
            // reversed(): a live reverse view — reads are the JDK reverse order; set writes through.
            run {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                val rRev = r.reversed()
                val mRev = call(m, "reversed", arrayOf()).getOrThrow()!!
                assertEquivalent("reversed.size", call(rRev, "size", arrayOf()), call(mRev, "size", arrayOf()))
                for (i in 0 until r.size) {
                    assertEquivalent("reversed.get[$i]", call(rRev, "get", arrayOf(INT), i), call(mRev, "get", arrayOf(INT), i))
                }
                if (r.size >= 1) {
                    // write-through: model reversed index 0 == parent LAST index.
                    call(mRev, "set", arrayOf(INT, OBJECT), 0, 77).getOrThrow()
                    (call(m, "get", arrayOf(INT), r.size - 1).getOrThrow()) shouldBe 77
                }
            }
            // listIterator(): bidirectional by-index cursor — next() reads match the JDK; set writes through.
            run {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                val rIt = r.listIterator()
                val mIt = call(m, "listIterator", arrayOf()).getOrThrow()!!
                var i = 0
                while (rIt.hasNext()) {
                    assertEquivalent("listIterator.next", call(rIt, "next", arrayOf()), call(mIt, "next", arrayOf()))
                    // write-through: set replaces the element just returned (index i).
                    call(mIt, "set", arrayOf(OBJECT), 42).getOrThrow()
                    (call(m, "get", arrayOf(INT), i).getOrThrow()) shouldBe 42
                    i++
                }
                assertEquivalent("listIterator.hasPrevious", call(rIt, "hasPrevious", arrayOf()), call(mIt, "hasPrevious", arrayOf()))
            }
            // ensureCapacity / trimToSize: observable no-ops.
            run {
                val r = java.util.ArrayList<Int>(seed)
                val m = modelList(seed)
                r.ensureCapacity(128); call(m, "ensureCapacity", arrayOf(INT), 128)
                r.trimToSize(); call(m, "trimToSize", arrayOf())
                assertSameElements(r, m)
            }
            // parallelStream(): the sequential stream — same element count.
            run {
                val m = modelList(seed)
                val mStream = call(m, "parallelStream", arrayOf()).getOrThrow()!!
                val mCount = mStream.javaClass.getMethod("count").invoke(mStream) as Long
                mCount shouldBe seed.size.toLong()
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
