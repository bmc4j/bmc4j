package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

private class SetOp(val desc: String, val method: String, val types: Array<Class<*>>, val args: Array<Any?>) {
    fun on(t: Any) = call(t, method, types, *args)
    override fun toString() = desc
}

private val setOp: Arb<SetOp> = run {
    // Small, collision-dense domain incl. negatives + null: elements are stored opaquely (compared
    // by equals), so what exercises the model is repeats/dedup/removal, not magnitude.
    val e: Arb<Int?> = Arb.int(-3..5).orNull(0.15)
    Arb.choice(
        e.map { SetOp("add($it)", "add", arrayOf(OBJECT), arrayOf(it)) },
        e.map { SetOp("contains($it)", "contains", arrayOf(OBJECT), arrayOf(it)) },
        e.map { SetOp("remove($it)", "remove", arrayOf(OBJECT), arrayOf(it)) },
        Arb.constant(SetOp("clear", "clear", arrayOf(), arrayOf())),
    )
}

private suspend fun checkSet(real: () -> Any, model: () -> Any) {
    checkAll(Arb.list(setOp, 0..40)) { ops ->
        val r = real()
        val m = model()
        ops.forEachIndexed { i, op -> assertEquivalent("op[$i]=$op", op.on(r), op.on(m)) }
        assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
        for (e in 0..4) {
            assertEquivalent("contains($e)", call(r, "contains", arrayOf(OBJECT), e), call(m, "contains", arrayOf(OBJECT), e))
        }
    }
}

/** Differential conformance for the Set models (dedup add / contains / remove). Iteration order is
 *  not modeled, so it isn't compared. */
class SetConformanceTest : FunSpec({

    test("HashSet conforms") {
        checkSet({ java.util.HashSet<Any?>() }, { bmcref.java.util.HashSet<Any?>() })
    }

    test("LinkedHashSet conforms") {
        checkSet({ java.util.LinkedHashSet<Any?>() }, { bmcref.java.util.LinkedHashSet<Any?>() })
    }

    // Copy constructor: dedups the source via equals (same surface for the LinkedHashSet model).
    test("HashSet(Collection) dedups the source like the JDK") {
        val elem: Arb<Int?> = Arb.int(-3..5).orNull(0.15)
        checkAll(Arb.list(elem, 0..30)) { items ->
            val rSrc = java.util.ArrayList<Any?>()
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in items) { rSrc.add(x); mSrc.add(x) }
            val r = java.util.HashSet<Any?>(rSrc)
            val m = bmcref.java.util.HashSet<Any?>(mSrc)
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            for (e in -3..5) {
                assertEquivalent("contains($e)", call(r, "contains", arrayOf(OBJECT), e), call(m, "contains", arrayOf(OBJECT), e))
            }
        }
    }

    test("LinkedHashSet(Collection) dedups the source like the JDK") {
        val elem: Arb<Int?> = Arb.int(-3..5).orNull(0.15)
        checkAll(Arb.list(elem, 0..30)) { items ->
            val rSrc = java.util.ArrayList<Any?>()
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in items) { rSrc.add(x); mSrc.add(x) }
            val r = java.util.LinkedHashSet<Any?>(rSrc)
            val m = bmcref.java.util.LinkedHashSet<Any?>(mSrc)
            assertEquivalent("size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
            for (e in -3..5) {
                assertEquivalent("contains($e)", call(r, "contains", arrayOf(OBJECT), e), call(m, "contains", arrayOf(OBJECT), e))
            }
        }
    }

    // --- stream() adapter ---------------------------------------------------------------------------
    // The Set models' stream() is a thin ListStream over the (deduped) elements. Differentially: the
    // stream's count() equals the set size, and toList()'s elements match the set as a multiset (set
    // iteration order isn't modeled, so compare order-independently). Driven over both Set models.
    test("HashSet/LinkedHashSet stream() conforms (count + element multiset)") {
        val elem: Arb<Int?> = Arb.int(-3..5).orNull(0.15)
        checkAll(Arb.list(elem, 0..30)) { items ->
            for ((real, model) in listOf(
                { java.util.HashSet<Any?>() } to { bmcref.java.util.HashSet<Any?>() },
                { java.util.LinkedHashSet<Any?>() } to { bmcref.java.util.LinkedHashSet<Any?>() },
            )) {
                val r = real(); val m = model()
                for (x in items) { call(r, "add", arrayOf(OBJECT), x); call(m, "add", arrayOf(OBJECT), x) }
                val rStream = call(r, "stream", arrayOf()).getOrThrow()!!
                val mStream = call(m, "stream", arrayOf()).getOrThrow()!!
                assertEquivalent("stream.count", call(rStream, "count", arrayOf()), call(mStream, "count", arrayOf()))
                // toList contents as an order-independent multiset (model returns a relocated List).
                val rList = call(call(r, "stream", arrayOf()).getOrThrow()!!, "toList", arrayOf()).getOrThrow() as java.util.List<*>
                val mModelList = call(call(m, "stream", arrayOf()).getOrThrow()!!, "toList", arrayOf()).getOrThrow()!!
                val mn = call(mModelList, "size", arrayOf()).getOrThrow() as Int
                val mElems = (0 until mn).map { call(mModelList, "get", arrayOf(INT), it).getOrThrow() }
                mElems.groupingBy { it }.eachCount() shouldBe rList.toList().groupingBy { it }.eachCount()
            }
        }
    }

    test("HashSet(negative capacity) throws like the JDK") {
        val real = runCatching { java.util.HashSet<Any?>(-1) }
        val model = runCatching { bmcref.java.util.HashSet<Any?>(-1) }
        model.exceptionOrNull()?.javaClass shouldBe real.exceptionOrNull()?.javaClass
    }
})
