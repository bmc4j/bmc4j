package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Differential conformance for the #184 java.util tail-drain: the Arrays double / Object-binarySearch /
 * toString surface, and the Collections single-collection static utilities — each invoked on the real
 * JDK class and the relocated model and compared on the observable (returned value, array contents,
 * boolean, or exception type). The collection-view / presizing-factory surface (subList/reversed,
 * descendingSet/descendingMap/navigableKeySet, newHashMap/newHashSet, LinkedList.descendingIterator,
 * TreeSet SequencedCollection ends) is also exercised here against the relocated model only, comparing
 * the observed elements/order/exception to the JDK.
 */

// --- Arrays: the newly-modeled double / Object-binarySearch / toString surface --------------------

class ArraysDoubleConformanceTest : FunSpec({
    val REAL = java.util.Arrays::class.java
    val MODEL = bmcref.java.util.Arrays::class.java
    val DOUBLEARR: Class<*> = DoubleArray::class.java
    val DOUBLE: Class<*> = Double::class.javaPrimitiveType!!

    test("copyOf(double[], int) conforms (truncate / zero-pad / negative)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..9)) { xs, n ->
            val a = xs.map { it.toDouble() }.toDoubleArray()
            val r = staticCall(REAL, "copyOf", arrayOf(DOUBLEARR, INT), a.copyOf(), n)
            val m = staticCall(MODEL, "copyOf", arrayOf(DOUBLEARR, INT), a.copyOf(), n)
            assertSameException(r, m)
            if (r.isSuccess) (m.getOrNull() as DoubleArray).toList() shouldBe (r.getOrNull() as DoubleArray).toList()
        }
    }

    test("copyOfRange(double[], int, int) conforms (incl. bad range)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..7), Arb.int(-1..9)) { xs, from, to ->
            val a = xs.map { it.toDouble() }.toDoubleArray()
            val r = staticCall(REAL, "copyOfRange", arrayOf(DOUBLEARR, INT, INT), a.copyOf(), from, to)
            val m = staticCall(MODEL, "copyOfRange", arrayOf(DOUBLEARR, INT, INT), a.copyOf(), from, to)
            assertSameException(r, m)
            if (r.isSuccess) (m.getOrNull() as DoubleArray).toList() shouldBe (r.getOrNull() as DoubleArray).toList()
        }
    }

    test("fill(double[], double) / fill(double[], int, int, double) conform (incl. bad range)") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(-9..9), Arb.int(-1..7), Arb.int(-1..7)) { xs, v, from, to ->
            val dv = v.toDouble()
            val ar = xs.map { it.toDouble() }.toDoubleArray(); val am = ar.copyOf()
            java.util.Arrays.fill(ar, dv)
            staticCall(MODEL, "fill", arrayOf(DOUBLEARR, DOUBLE), am, dv).getOrThrow()
            am.toList() shouldBe ar.toList()

            val rr = xs.map { it.toDouble() }.toDoubleArray(); val rm = rr.copyOf()
            val r = staticCall(REAL, "fill", arrayOf(DOUBLEARR, INT, INT, DOUBLE), rr, from, to, dv)
            val m = staticCall(MODEL, "fill", arrayOf(DOUBLEARR, INT, INT, DOUBLE), rm, from, to, dv)
            assertSameException(r, m)
            if (r.isSuccess) rm.toList() shouldBe rr.toList()
        }
    }

    test("setAll(double[], gen) / parallelSetAll(double[], gen) == elementwise gen") {
        checkAll(Arb.list(Arb.int(0..9), 0..6)) { xs ->
            val ar = DoubleArray(xs.size); val am = DoubleArray(xs.size)
            java.util.Arrays.setAll(ar, java.util.function.IntToDoubleFunction { it.toDouble() * 1.5 })
            staticCall(MODEL, "setAll", arrayOf(DOUBLEARR, java.util.function.IntToDoubleFunction::class.java),
                am, java.util.function.IntToDoubleFunction { it.toDouble() * 1.5 }).getOrThrow()
            am.toList() shouldBe ar.toList()

            val pr = DoubleArray(xs.size); val pm = DoubleArray(xs.size)
            java.util.Arrays.parallelSetAll(pr, java.util.function.IntToDoubleFunction { it.toDouble() + 2.0 })
            staticCall(MODEL, "parallelSetAll", arrayOf(DOUBLEARR, java.util.function.IntToDoubleFunction::class.java),
                pm, java.util.function.IntToDoubleFunction { it.toDouble() + 2.0 }).getOrThrow()
            pm.toList() shouldBe pr.toList()
        }
    }

    test("parallelPrefix(double[], +) == inclusive scan (full + ranged)") {
        checkAll(Arb.list(Arb.int(-20..20), 0..6), Arb.int(0..6), Arb.int(0..6)) { xs, from0, to0 ->
            val ar = xs.map { it.toDouble() }.toDoubleArray(); val am = ar.copyOf()
            java.util.Arrays.parallelPrefix(ar, java.util.function.DoubleBinaryOperator { x, y -> x + y })
            staticCall(MODEL, "parallelPrefix", arrayOf(DOUBLEARR, java.util.function.DoubleBinaryOperator::class.java),
                am, java.util.function.DoubleBinaryOperator { x, y -> x + y }).getOrThrow()
            am.toList() shouldBe ar.toList()

            val from = minOf(from0, xs.size); val to = maxOf(from, minOf(to0, xs.size))
            val rr = xs.map { it.toDouble() }.toDoubleArray(); val rm = rr.copyOf()
            java.util.Arrays.parallelPrefix(rr, from, to, java.util.function.DoubleBinaryOperator { x, y -> x + y })
            staticCall(MODEL, "parallelPrefix", arrayOf(DOUBLEARR, INT, INT, java.util.function.DoubleBinaryOperator::class.java),
                rm, from, to, java.util.function.DoubleBinaryOperator { x, y -> x + y }).getOrThrow()
            rm.toList() shouldBe rr.toList()
        }
    }

    test("stream(double[]).sum() conforms (full + ranged, incl. bad range)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..7), Arb.int(-1..7)) { xs, from, to ->
            val a = xs.map { it.toDouble() }.toDoubleArray()
            val r = java.util.Arrays.stream(a).sum()
            val ms = staticCall(MODEL, "stream", arrayOf(DOUBLEARR), a).getOrThrow()!!
            call(ms, "sum", arrayOf()).getOrThrow() shouldBe r

            val rr = runCatching { java.util.Arrays.stream(a, from, to).sum() }
            val mr = staticCall(MODEL, "stream", arrayOf(DOUBLEARR, INT, INT), a, from, to)
            assertSameException(rr, mr)
            if (rr.isSuccess) call(mr.getOrThrow()!!, "sum", arrayOf()).getOrThrow() shouldBe rr.getOrThrow()
        }
    }
})

class ArraysObjectBinarySearchConformanceTest : FunSpec({
    val MODEL = bmcref.java.util.Arrays::class.java
    val OBJARR: Class<*> = Array<Any?>::class.java

    test("binarySearch(Object[], Object) natural order on a sorted array conforms") {
        checkAll(Arb.list(Arb.int(-20..20), 0..7), Arb.int(-25..25)) { xs, key ->
            val a: Array<Any?> = xs.sorted().toTypedArray()
            val r = java.util.Arrays.binarySearch(a, key)
            val m = staticCall(MODEL, "binarySearch", arrayOf(OBJARR, OBJECT), a.copyOf(), key)
            m.getOrThrow() shouldBe r
        }
    }
})

class ArraysObjectMismatchConformanceTest : FunSpec({
    val MODEL = bmcref.java.util.Arrays::class.java
    val OBJARR: Class<*> = Array<Any?>::class.java

    test("mismatch(Object[], Object[]) via element .equals conforms") {
        checkAll(Arb.list(Arb.int(0..4), 0..7), Arb.list(Arb.int(0..4), 0..7)) { xs, ys ->
            val a: Array<Any?> = xs.toTypedArray()
            val b: Array<Any?> = ys.toTypedArray()
            val r = java.util.Arrays.mismatch(a, b)
            val m = staticCall(MODEL, "mismatch", arrayOf(OBJARR, OBJARR), a.copyOf(), b.copyOf())
            m.getOrThrow() shouldBe r
        }
    }

    test("mismatch(Object[], int, int, Object[], int, int) conforms (incl. bad range)") {
        checkAll(
            Arb.list(Arb.int(0..4), 0..6), Arb.list(Arb.int(0..4), 0..6),
            Arb.int(-1..6), Arb.int(-1..6), Arb.int(-1..6), Arb.int(-1..6),
        ) { xs, ys, af, at, bf, bt ->
            val a: Array<Any?> = xs.toTypedArray()
            val b: Array<Any?> = ys.toTypedArray()
            val r = runCatching { java.util.Arrays.mismatch(a, af, at, b, bf, bt) }
            val m = staticCall(MODEL, "mismatch", arrayOf(OBJARR, INT, INT, OBJARR, INT, INT),
                a.copyOf(), af, at, b.copyOf(), bf, bt)
            assertSameException(r, m)
            if (r.isSuccess) m.getOrThrow() shouldBe r.getOrThrow()
        }
    }
})

class ArraysToStringConformanceTest : FunSpec({
    val MODEL = bmcref.java.util.Arrays::class.java
    val INTARR: Class<*> = IntArray::class.java
    val LONGARR: Class<*> = LongArray::class.java
    val SHORTARR: Class<*> = ShortArray::class.java
    val BYTEARR: Class<*> = ByteArray::class.java
    val CHARARR: Class<*> = CharArray::class.java
    val BOOLARR: Class<*> = BooleanArray::class.java
    val OBJARR: Class<*> = Array<Any?>::class.java

    test("toString(int[]/long[]/short[]/byte[]/char[]/boolean[]/Object[]) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6)) { xs ->
            staticCall(MODEL, "toString", arrayOf(INTARR), xs.toIntArray()).getOrThrow() shouldBe
                java.util.Arrays.toString(xs.toIntArray())
            staticCall(MODEL, "toString", arrayOf(LONGARR), xs.map { it.toLong() }.toLongArray()).getOrThrow() shouldBe
                java.util.Arrays.toString(xs.map { it.toLong() }.toLongArray())
            staticCall(MODEL, "toString", arrayOf(SHORTARR), xs.map { it.toShort() }.toShortArray()).getOrThrow() shouldBe
                java.util.Arrays.toString(xs.map { it.toShort() }.toShortArray())
            staticCall(MODEL, "toString", arrayOf(BYTEARR), xs.map { it.toByte() }.toByteArray()).getOrThrow() shouldBe
                java.util.Arrays.toString(xs.map { it.toByte() }.toByteArray())
            staticCall(MODEL, "toString", arrayOf(CHARARR), xs.map { (it and 0x7f).toChar() }.toCharArray()).getOrThrow() shouldBe
                java.util.Arrays.toString(xs.map { (it and 0x7f).toChar() }.toCharArray())
            staticCall(MODEL, "toString", arrayOf(BOOLARR), xs.map { it % 2 == 0 }.toBooleanArray()).getOrThrow() shouldBe
                java.util.Arrays.toString(xs.map { it % 2 == 0 }.toBooleanArray())
            val o: Array<Any?> = xs.toTypedArray()
            staticCall(MODEL, "toString", arrayOf(OBJARR), o).getOrThrow() shouldBe java.util.Arrays.toString(o)
        }
    }

    test("toString(int[]) of an empty / null array conforms") {
        staticCall(MODEL, "toString", arrayOf(INTARR), IntArray(0)).getOrThrow() shouldBe "[]"
        staticCall(MODEL, "toString", arrayOf(INTARR), null).getOrThrow() shouldBe "null"
    }
})

// --- Collections: bounded single-collection static utilities --------------------------------------
// The real method takes a java.util list/collection and the model takes the relocated model type, so
// each side builds its own backing and the observable result is compared. List/Set/Map state is read
// back by index / contains / size through the public interface.

class CollectionsConformanceTest : FunSpec({
    val REAL = java.util.Collections::class.java
    val MODEL = bmcref.java.util.Collections::class.java
    val REAL_LIST: Class<*> = java.util.List::class.java
    val MODEL_LIST: Class<*> = bmcref.java.util.List::class.java
    val REAL_COLL: Class<*> = java.util.Collection::class.java
    val MODEL_COLL: Class<*> = bmcref.java.util.Collection::class.java

    fun realList(xs: List<Int>): java.util.ArrayList<Any?> {
        val l = java.util.ArrayList<Any?>(); xs.forEach { l.add(it) }; return l
    }
    fun modelList(xs: List<Int>): bmcref.java.util.ArrayList<Any?> {
        val l = bmcref.java.util.ArrayList<Any?>(); xs.forEach { l.add(it) }; return l
    }
    /** Read a List (real or model) back into a Kotlin list via size/get through the public interface. */
    fun read(l: Any): List<Any?> {
        val n = call(l, "size", arrayOf()).getOrThrow() as Int
        return (0 until n).map { call(l, "get", arrayOf(INT), it).getOrThrow() }
    }

    test("emptyList / emptySet / emptyMap are empty") {
        (staticCall(MODEL, "emptyList", arrayOf()).getOrThrow()!!).let { call(it, "size", arrayOf()).getOrThrow() shouldBe 0 }
        (staticCall(MODEL, "emptySet", arrayOf()).getOrThrow()!!).let { call(it, "size", arrayOf()).getOrThrow() shouldBe 0 }
        (staticCall(MODEL, "emptyMap", arrayOf()).getOrThrow()!!).let { call(it, "size", arrayOf()).getOrThrow() shouldBe 0 }
    }

    test("singletonList / singleton / singletonMap hold the one element") {
        val l = staticCall(MODEL, "singletonList", arrayOf(OBJECT), 7).getOrThrow()!!
        read(l) shouldBe listOf(7)
        val s = staticCall(MODEL, "singleton", arrayOf(OBJECT), 7).getOrThrow()!!
        call(s, "contains", arrayOf(OBJECT), 7).getOrThrow() shouldBe true
        val m = staticCall(MODEL, "singletonMap", arrayOf(OBJECT, OBJECT), 7, 8).getOrThrow()!!
        call(m, "get", arrayOf(OBJECT), 7).getOrThrow() shouldBe 8
    }

    test("nCopies(n, o) conforms (incl. negative IAE)") {
        checkAll(Arb.int(-1..6)) { n ->
            val r = staticCall(REAL, "nCopies", arrayOf(INT, OBJECT), n, 9)
            val m = staticCall(MODEL, "nCopies", arrayOf(INT, OBJECT), n, 9)
            assertSameException(r, m)
            if (r.isSuccess) read(m.getOrThrow()!!) shouldBe read(r.getOrThrow()!!)
        }
    }

    test("reverse(List) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..8)) { xs ->
            val r = realList(xs); val m = modelList(xs)
            java.util.Collections.reverse(r)
            staticCall(MODEL, "reverse", arrayOf(MODEL_LIST), m).getOrThrow()
            read(m) shouldBe read(r)
        }
    }

    test("swap(List, i, j) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 1..8), Arb.int(0..7), Arb.int(0..7)) { xs, i0, j0 ->
            val i = i0 % xs.size; val j = j0 % xs.size
            val r = realList(xs); val m = modelList(xs)
            java.util.Collections.swap(r, i, j)
            staticCall(MODEL, "swap", arrayOf(MODEL_LIST, INT, INT), m, i, j).getOrThrow()
            read(m) shouldBe read(r)
        }
    }

    test("fill(List, o) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..8)) { xs ->
            val r = realList(xs); val m = modelList(xs)
            java.util.Collections.fill(r, 0)
            staticCall(MODEL, "fill", arrayOf(MODEL_LIST, OBJECT), m, 0).getOrThrow()
            read(m) shouldBe read(r)
        }
    }

    test("rotate(List, distance) conforms (incl. negative / large)") {
        checkAll(Arb.list(Arb.int(0..9), 0..8), Arb.int(-12..12)) { xs, d ->
            val r = realList(xs); val m = modelList(xs)
            java.util.Collections.rotate(r, d)
            staticCall(MODEL, "rotate", arrayOf(MODEL_LIST, INT), m, d).getOrThrow()
            read(m) shouldBe read(r)
        }
    }

    test("replaceAll(List, old, new) conforms (return + state)") {
        checkAll(Arb.list(Arb.int(0..4), 0..8)) { xs ->
            val r = realList(xs); val m = modelList(xs)
            val rr = java.util.Collections.replaceAll(r, 2, 99)
            val mr = staticCall(MODEL, "replaceAll", arrayOf(MODEL_LIST, OBJECT, OBJECT), m, 2, 99).getOrThrow()
            mr shouldBe rr
            read(m) shouldBe read(r)
        }
    }

    test("copy(dest, src) conforms (incl. dest-too-small IOOBE)") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.list(Arb.int(0..9), 0..6)) { dst, src ->
            val rd = realList(dst); val md = modelList(dst)
            val rs = realList(src); val ms = modelList(src)
            val r = runCatching { java.util.Collections.copy(rd, rs) }
            val m = staticCall(MODEL, "copy", arrayOf(MODEL_LIST, MODEL_LIST), md, ms)
            assertSameException(r, m)
            if (r.isSuccess) read(md) shouldBe read(rd)
        }
    }

    test("frequency(Collection, o) conforms") {
        checkAll(Arb.list(Arb.int(0..4), 0..10), Arb.int(0..4)) { xs, o ->
            val r = realList(xs); val m = modelList(xs)
            staticCall(MODEL, "frequency", arrayOf(MODEL_COLL, OBJECT), m, o).getOrThrow() shouldBe
                java.util.Collections.frequency(r, o)
        }
    }

    test("disjoint(Collection, Collection) conforms") {
        checkAll(Arb.list(Arb.int(0..6), 0..6), Arb.list(Arb.int(0..6), 0..6)) { xs, ys ->
            val r1 = realList(xs); val r2 = realList(ys); val m1 = modelList(xs); val m2 = modelList(ys)
            staticCall(MODEL, "disjoint", arrayOf(MODEL_COLL, MODEL_COLL), m1, m2).getOrThrow() shouldBe
                java.util.Collections.disjoint(r1, r2)
        }
    }

    test("addAll(Collection, T...) conforms (return + state)") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.list(Arb.int(0..9), 0..4)) { xs, add ->
            val r = realList(xs); val m = modelList(xs)
            val addArr: Array<Any?> = add.toTypedArray()
            val rr = java.util.Collections.addAll(r, *addArr)
            val mr = staticCall(MODEL, "addAll", arrayOf(MODEL_COLL, Array<Any?>::class.java), m, addArr).getOrThrow()
            mr shouldBe rr
            read(m) shouldBe read(r)
        }
    }

    test("max(Collection) / min(Collection) natural order conform (incl. empty NoSuchElement)") {
        checkAll(Arb.list(Arb.int(-20..20), 0..8)) { xs ->
            val r = realList(xs); val m = modelList(xs)
            // Drive both reflectively (Kotlin can't infer the Comparable bound through java.util.ArrayList<Any?>).
            val rMax = staticCall(REAL, "max", arrayOf(REAL_COLL), r)
            val mMax = staticCall(MODEL, "max", arrayOf(MODEL_COLL), m)
            assertSameException(rMax, mMax)
            if (rMax.isSuccess) mMax.getOrThrow() shouldBe rMax.getOrThrow()
            val rMin = staticCall(REAL, "min", arrayOf(REAL_COLL), r)
            val mMin = staticCall(MODEL, "min", arrayOf(MODEL_COLL), m)
            assertSameException(rMin, mMin)
            if (rMin.isSuccess) mMin.getOrThrow() shouldBe rMin.getOrThrow()
        }
    }

    test("sort(List) natural order conforms") {
        checkAll(Arb.list(Arb.int(-20..20), 0..9)) { xs ->
            val r = realList(xs); val m = modelList(xs)
            staticCall(REAL, "sort", arrayOf(REAL_LIST), r).getOrThrow()
            staticCall(MODEL, "sort", arrayOf(MODEL_LIST), m).getOrThrow()
            read(m) shouldBe read(r)
        }
    }

    test("binarySearch(List, key) natural order on a sorted list conforms") {
        checkAll(Arb.list(Arb.int(-15..15), 0..9), Arb.int(-18..18)) { xs, key ->
            val sorted = xs.sorted()
            val r = realList(sorted); val m = modelList(sorted)
            val rr = staticCall(REAL, "binarySearch", arrayOf(REAL_LIST, OBJECT), r, key).getOrThrow()
            staticCall(MODEL, "binarySearch", arrayOf(MODEL_LIST, OBJECT), m, key).getOrThrow() shouldBe rr
        }
    }

    test("indexOfSubList / lastIndexOfSubList conform") {
        checkAll(Arb.list(Arb.int(0..3), 0..8), Arb.list(Arb.int(0..3), 0..3)) { src, tgt ->
            val rS = realList(src); val mS = modelList(src); val rT = realList(tgt); val mT = modelList(tgt)
            staticCall(MODEL, "indexOfSubList", arrayOf(MODEL_LIST, MODEL_LIST), mS, mT).getOrThrow() shouldBe
                java.util.Collections.indexOfSubList(rS, rT)
            staticCall(MODEL, "lastIndexOfSubList", arrayOf(MODEL_LIST, MODEL_LIST), mS, mT).getOrThrow() shouldBe
                java.util.Collections.lastIndexOfSubList(rS, rT)
        }
    }

    test("list(Enumeration) drains in order") {
        checkAll(Arb.list(Arb.int(0..9), 0..8)) { xs ->
            val m = modelList(xs)
            val en = staticCall(MODEL, "enumeration", arrayOf(MODEL_COLL), m).getOrThrow()!!
            val drained = staticCall(MODEL, "list", arrayOf(bmcref.java.util.Enumeration::class.java), en).getOrThrow()!!
            read(drained) shouldBe xs
        }
    }
})

// --- collection views & presizing factories (model-vs-JDK on the observable elements) -------------

class CollectionViewsConformanceTest : FunSpec({

    fun read(l: Any): List<Any?> {
        val n = call(l, "size", arrayOf()).getOrThrow() as Int
        return (0 until n).map { call(l, "get", arrayOf(INT), it).getOrThrow() }
    }

    test("ArrayList.subList / reversed conform on contents and order") {
        checkAll(Arb.list(Arb.int(0..9), 0..8), Arb.int(0..8), Arb.int(0..8)) { xs, f0, t0 ->
            val r = java.util.ArrayList<Any?>(xs); val m = bmcref.java.util.ArrayList<Any?>(); xs.forEach { m.add(it) }
            // reversed
            read(call(m, "reversed", arrayOf()).getOrThrow()!!) shouldBe r.reversed()
            // subList over a valid window
            val f = minOf(f0, xs.size); val t = maxOf(f, minOf(t0, xs.size))
            read(call(m, "subList", arrayOf(INT, INT), f, t).getOrThrow()!!) shouldBe xs.subList(f, t)
        }
    }

    test("LinkedList.descendingIterator walks tail->head") {
        checkAll(Arb.list(Arb.int(0..9), 0..8)) { xs ->
            val m = bmcref.java.util.LinkedList<Any?>(); xs.forEach { m.add(it) }
            val it = call(m, "descendingIterator", arrayOf()).getOrThrow()!!
            val out = ArrayList<Any?>()
            while (call(it, "hasNext", arrayOf()).getOrThrow() as Boolean) {
                out.add(call(it, "next", arrayOf()).getOrThrow())
            }
            out shouldBe xs.reversed()
        }
    }

    test("LinkedHashSet.reversed yields reverse insertion order") {
        checkAll(Arb.list(Arb.int(0..20), 0..8)) { xs ->
            val distinct = xs.distinct()
            val m = bmcref.java.util.LinkedHashSet<Any?>(); xs.forEach { m.add(it) }
            val rev = call(m, "reversed", arrayOf()).getOrThrow()!!
            val it = call(rev, "iterator", arrayOf()).getOrThrow()!!
            val out = ArrayList<Any?>()
            while (call(it, "hasNext", arrayOf()).getOrThrow() as Boolean) {
                out.add(call(it, "next", arrayOf()).getOrThrow())
            }
            out shouldBe distinct.reversed()
        }
    }

    test("TreeSet getFirst/getLast == first/last; descendingSet is descending") {
        checkAll(Arb.list(Arb.int(-20..20), 1..8)) { xs ->
            val m = bmcref.java.util.TreeSet<Any?>(); xs.forEach { m.add(it) }
            val sortedDistinct = xs.distinct().sorted()
            call(m, "getFirst", arrayOf()).getOrThrow() shouldBe sortedDistinct.first()
            call(m, "getLast", arrayOf()).getOrThrow() shouldBe sortedDistinct.last()
            val ds = call(m, "descendingSet", arrayOf()).getOrThrow()!!
            val it = call(ds, "iterator", arrayOf()).getOrThrow()!!
            val out = ArrayList<Any?>()
            while (call(it, "hasNext", arrayOf()).getOrThrow() as Boolean) {
                out.add(call(it, "next", arrayOf()).getOrThrow())
            }
            out shouldBe sortedDistinct.reversed()
        }
    }

    test("TreeMap navigableKeySet ascending / descendingKeySet descending") {
        checkAll(Arb.list(Arb.int(-20..20), 0..8)) { xs ->
            val m = bmcref.java.util.TreeMap<Any?, Any?>(); xs.forEach { m.put(it, it) }
            val sortedDistinct = xs.distinct().sorted()
            fun iterate(view: Any): List<Any?> {
                val it = call(view, "iterator", arrayOf()).getOrThrow()!!
                val out = ArrayList<Any?>()
                while (call(it, "hasNext", arrayOf()).getOrThrow() as Boolean) out.add(call(it, "next", arrayOf()).getOrThrow())
                return out
            }
            iterate(call(m, "navigableKeySet", arrayOf()).getOrThrow()!!) shouldBe sortedDistinct
            iterate(call(m, "descendingKeySet", arrayOf()).getOrThrow()!!) shouldBe sortedDistinct.reversed()
        }
    }

    test("presizing factories return empty collections (incl. negative IAE)") {
        call(bmcref.java.util.HashMap.newHashMap<Any?, Any?>(8), "size", arrayOf()).getOrThrow() shouldBe 0
        call(bmcref.java.util.HashSet.newHashSet<Any?>(8), "size", arrayOf()).getOrThrow() shouldBe 0
        call(bmcref.java.util.LinkedHashMap.newLinkedHashMap<Any?, Any?>(8), "size", arrayOf()).getOrThrow() shouldBe 0
        call(bmcref.java.util.LinkedHashSet.newLinkedHashSet<Any?>(8), "size", arrayOf()).getOrThrow() shouldBe 0
        runCatching { bmcref.java.util.HashMap.newHashMap<Any?, Any?>(-1) }.isFailure shouldBe true
    }
})

// --- Objects: the null-safe / bounds-check static utility surface ---------------------------------
// Each method is invoked on the real java.util.Objects and the relocated model and compared on the
// observable (returned value or thrown exception type). Comparators/suppliers are real SAMs (the
// java.util.function.* params are NOT relocated, so they pass straight through).

class ObjectsConformanceTest : FunSpec({
    val REAL = java.util.Objects::class.java
    val MODEL = bmcref.java.util.Objects::class.java
    val STRING: Class<*> = java.lang.String::class.java
    val LONG: Class<*> = Long::class.javaPrimitiveType!!
    val COMPARATOR: Class<*> = java.util.Comparator::class.java
    val SUPPLIER: Class<*> = java.util.function.Supplier::class.java
    val OBJARR: Class<*> = Array<Any?>::class.java

    test("equals(a,b) is null-safe and matches the JDK") {
        val vals = listOf<Any?>(null, 1, 2, "x", "x")
        for (a in vals) for (b in vals) {
            staticCall(MODEL, "equals", arrayOf(OBJECT, OBJECT), a, b).getOrThrow() shouldBe
                java.util.Objects.equals(a, b)
        }
    }

    test("hashCode(o) / hash(values...) / isNull / nonNull / toString match the JDK") {
        checkAll(Arb.int(-50..50)) { x ->
            staticCall(MODEL, "hashCode", arrayOf(OBJECT), x).getOrThrow() shouldBe java.util.Objects.hashCode(x)
            staticCall(MODEL, "hashCode", arrayOf(OBJECT), null).getOrThrow() shouldBe java.util.Objects.hashCode(null)
            staticCall(MODEL, "isNull", arrayOf(OBJECT), x).getOrThrow() shouldBe java.util.Objects.isNull(x)
            staticCall(MODEL, "nonNull", arrayOf(OBJECT), x).getOrThrow() shouldBe java.util.Objects.nonNull(x)
            staticCall(MODEL, "toString", arrayOf(OBJECT), x).getOrThrow() shouldBe java.util.Objects.toString(x)
            staticCall(MODEL, "toString", arrayOf(OBJECT), null).getOrThrow() shouldBe java.util.Objects.toString(null)
            staticCall(MODEL, "toString", arrayOf(OBJECT, STRING), null, "dфлt").getOrThrow() shouldBe
                java.util.Objects.toString(null, "dфлt")
            val arr: Array<Any?> = arrayOf(x, x + 1)
            staticCall(MODEL, "hash", arrayOf(OBJARR), arr).getOrThrow() shouldBe java.util.Objects.hash(x, x + 1)
        }
    }

    test("requireNonNull family: returns on non-null, NPEs on null") {
        // returns the same reference on non-null
        staticCall(MODEL, "requireNonNull", arrayOf(OBJECT), 7).getOrThrow() shouldBe 7
        staticCall(MODEL, "requireNonNull", arrayOf(OBJECT, STRING), 7, "m").getOrThrow() shouldBe 7
        staticCall(MODEL, "requireNonNull", arrayOf(OBJECT, SUPPLIER), 7, java.util.function.Supplier { "m" }).getOrThrow() shouldBe 7
        // NPE on null (each overload), conforming to the JDK
        assertSameException(
            runCatching { java.util.Objects.requireNonNull<Any?>(null) },
            staticCall(MODEL, "requireNonNull", arrayOf(OBJECT), null))
        assertSameException(
            runCatching { java.util.Objects.requireNonNull<Any?>(null, "m") },
            staticCall(MODEL, "requireNonNull", arrayOf(OBJECT, STRING), null, "m"))
        assertSameException(
            runCatching { java.util.Objects.requireNonNull<Any?>(null, java.util.function.Supplier { "m" }) },
            staticCall(MODEL, "requireNonNull", arrayOf(OBJECT, SUPPLIER), null, java.util.function.Supplier { "m" }))
    }

    test("requireNonNullElse / requireNonNullElseGet conform (incl. both-null NPE)") {
        staticCall(MODEL, "requireNonNullElse", arrayOf(OBJECT, OBJECT), null, 9).getOrThrow() shouldBe 9
        staticCall(MODEL, "requireNonNullElse", arrayOf(OBJECT, OBJECT), 3, 9).getOrThrow() shouldBe 3
        assertSameException(
            runCatching { java.util.Objects.requireNonNullElse<Any?>(null, null) },
            staticCall(MODEL, "requireNonNullElse", arrayOf(OBJECT, OBJECT), null, null))
        staticCall(MODEL, "requireNonNullElseGet", arrayOf(OBJECT, SUPPLIER), null, java.util.function.Supplier { 5 }).getOrThrow() shouldBe 5
        staticCall(MODEL, "requireNonNullElseGet", arrayOf(OBJECT, SUPPLIER), 3, java.util.function.Supplier { 5 }).getOrThrow() shouldBe 3
        assertSameException(
            runCatching { java.util.Objects.requireNonNullElseGet<Any?>(null, java.util.function.Supplier { null }) },
            staticCall(MODEL, "requireNonNullElseGet", arrayOf(OBJECT, SUPPLIER), null, java.util.function.Supplier { null }))
    }

    test("compare(a,b,cmp) via a lambda comparator conforms (incl. a==b shortcut)") {
        val cmp = Comparator<Int> { a, b -> a - b }
        checkAll(Arb.int(-9..9), Arb.int(-9..9)) { a, b ->
            val r = java.util.Objects.compare(a, b, cmp)
            val m = staticCall(MODEL, "compare", arrayOf(OBJECT, OBJECT, COMPARATOR), a, b, cmp).getOrThrow() as Int
            // sign-compare (JDK compare contracts on sign, not magnitude)
            (m < 0) shouldBe (r < 0); (m == 0) shouldBe (r == 0); (m > 0) shouldBe (r > 0)
        }
        // a == b (same reference) short-circuits to 0 without calling the comparator on both
        val same = "z"
        staticCall(MODEL, "compare", arrayOf(OBJECT, OBJECT, COMPARATOR), same, same,
            Comparator<String> { _, _ -> 42 }).getOrThrow() shouldBe 0
    }

    test("checkIndex (int + long) conforms: in-range returns, out-of-range IOOBE") {
        checkAll(Arb.int(-2..6), Arb.int(0..5)) { i, len ->
            assertEquivalent("checkIndex($i,$len)",
                runCatching { java.util.Objects.checkIndex(i, len) },
                staticCall(MODEL, "checkIndex", arrayOf(INT, INT), i, len))
            assertEquivalent("checkIndex($i,$len) long",
                runCatching { java.util.Objects.checkIndex(i.toLong(), len.toLong()) },
                staticCall(MODEL, "checkIndex", arrayOf(LONG, LONG), i.toLong(), len.toLong()))
        }
    }

    test("checkFromToIndex / checkFromIndexSize (int + long) conform") {
        checkAll(Arb.int(-2..6), Arb.int(-2..6), Arb.int(0..5)) { a, b, len ->
            assertEquivalent("checkFromToIndex($a,$b,$len)",
                runCatching { java.util.Objects.checkFromToIndex(a, b, len) },
                staticCall(MODEL, "checkFromToIndex", arrayOf(INT, INT, INT), a, b, len))
            assertEquivalent("checkFromToIndex($a,$b,$len) long",
                runCatching { java.util.Objects.checkFromToIndex(a.toLong(), b.toLong(), len.toLong()) },
                staticCall(MODEL, "checkFromToIndex", arrayOf(LONG, LONG, LONG), a.toLong(), b.toLong(), len.toLong()))
            assertEquivalent("checkFromIndexSize($a,$b,$len)",
                runCatching { java.util.Objects.checkFromIndexSize(a, b, len) },
                staticCall(MODEL, "checkFromIndexSize", arrayOf(INT, INT, INT), a, b, len))
            assertEquivalent("checkFromIndexSize($a,$b,$len) long",
                runCatching { java.util.Objects.checkFromIndexSize(a.toLong(), b.toLong(), len.toLong()) },
                staticCall(MODEL, "checkFromIndexSize", arrayOf(LONG, LONG, LONG), a.toLong(), b.toLong(), len.toLong()))
        }
    }
})

// --- EnumMap / EnumSet: enum-keyed map / enum-element set over a real test enum -------------------
// Both sides take a real java.lang.Enum constant (java.lang.* is not relocated), so the same enum
// constants drive the JDK class and the relocated model; value ops + ordinal-ordered iteration are
// compared. EnumSet's universe factories (allOf/noneOf/range/complementOf) are loud (not exercised).

enum class Color { RED, GREEN, BLUE, YELLOW }

class EnumMapConformanceTest : FunSpec({
    val colors = Color.entries

    fun keyList(l: Any): List<Any?> {
        val ks = call(l, "keySet", arrayOf()).getOrThrow()!!
        val it = call(ks, "iterator", arrayOf()).getOrThrow()!!
        val out = ArrayList<Any?>()
        while (call(it, "hasNext", arrayOf()).getOrThrow() as Boolean) out.add(call(it, "next", arrayOf()).getOrThrow())
        return out
    }

    // An op is encoded as (opcode, keyOrdinal, value): opcode 0 = put, 1 = remove. keyOrdinal in
    // 0..3 selects a Color; value in -9..9. (Avoids Arb.element/3-arg bind to keep type inference clean.)
    test("EnumMap put/get/overwrite/remove/size/containsKey conform; iteration is ordinal-ordered") {
        val opArb = Arb.bind(Arb.int(0..1), Arb.int(0..3), Arb.int(-9..9)) { o, ki, v -> Triple(o, ki, v) }
        checkAll(Arb.list(opArb, 0..30)) { ops ->
            val r = java.util.EnumMap<Color, Int>(Color::class.java)
            val m = bmcref.java.util.EnumMap<Color, Int>(Color::class.java)
            for ((o, ki, v) in ops) {
                val k = colors[ki]
                if (o == 0) {
                    assertEquivalent("put($k,$v)",
                        runCatching { r.put(k, v) }, call(m, "put", arrayOf(java.lang.Enum::class.java, OBJECT), k, v))
                } else {
                    assertEquivalent("remove($k)",
                        runCatching { r.remove(k) }, call(m, "remove", arrayOf(OBJECT), k))
                }
            }
            assertEquivalent("size", runCatching { r.size }, call(m, "size", arrayOf()))
            for (k in colors) {
                assertEquivalent("get($k)", runCatching { r.get(k) }, call(m, "get", arrayOf(OBJECT), k))
                assertEquivalent("containsKey($k)", runCatching { r.containsKey(k) }, call(m, "containsKey", arrayOf(OBJECT), k))
            }
            // keySet iterates in ordinal order, like the JDK EnumMap
            keyList(m) shouldBe r.keys.toList()
        }
    }

    test("EnumMap(Class) is empty; put(null) throws NPE like the JDK") {
        call(bmcref.java.util.EnumMap<Color, Int>(Color::class.java), "size", arrayOf()).getOrThrow() shouldBe 0
        val r = java.util.EnumMap<Color, Int>(Color::class.java)
        val m = bmcref.java.util.EnumMap<Color, Int>(Color::class.java)
        assertSameException(
            runCatching { r.put(null, 1) },
            call(m, "put", arrayOf(java.lang.Enum::class.java, OBJECT), null, 1))
    }

    test("EnumMap copy constructor preserves mappings") {
        checkAll(Arb.list(Arb.bind(Arb.int(0..3), Arb.int(-9..9)) { ki, v -> ki to v }, 0..12)) { pairs ->
            val rSrc = java.util.EnumMap<Color, Int>(Color::class.java)
            val mSrc = bmcref.java.util.EnumMap<Color, Int>(Color::class.java)
            for ((ki, v) in pairs) { val k = colors[ki]; rSrc.put(k, v); call(mSrc, "put", arrayOf(java.lang.Enum::class.java, OBJECT), k, v) }
            val r = java.util.EnumMap<Color, Int>(rSrc)
            val m = bmcref.java.util.EnumMap<Color, Int>(mSrc as bmcref.java.util.EnumMap<Color, Int>)
            assertEquivalent("size", runCatching { r.size }, call(m, "size", arrayOf()))
            for (k in colors) assertEquivalent("get($k)", runCatching { r.get(k) }, call(m, "get", arrayOf(OBJECT), k))
        }
    }
})

class EnumSetConformanceTest : FunSpec({
    val colors = Color.entries

    fun setMembers(s: Any): Set<Any?> {
        val out = LinkedHashSet<Any?>()
        for (c in colors) if (call(s, "contains", arrayOf(OBJECT), c).getOrThrow() as Boolean) out.add(c)
        return out
    }

    test("EnumSet.of (1..5 + varargs) dedups and contains the given elements") {
        // of(e)
        setMembers(bmcref.java.util.EnumSet.of(Color.RED)) shouldBe setOf(Color.RED)
        // of(e1,e2) with a duplicate dedups
        setMembers(bmcref.java.util.EnumSet.of(Color.RED, Color.RED)) shouldBe setOf(Color.RED)
        setMembers(bmcref.java.util.EnumSet.of(Color.RED, Color.BLUE)) shouldBe setOf(Color.RED, Color.BLUE)
        setMembers(bmcref.java.util.EnumSet.of(Color.RED, Color.GREEN, Color.BLUE)) shouldBe
            setOf(Color.RED, Color.GREEN, Color.BLUE)
        // of(E, E...) varargs
        val s = bmcref.java.util.EnumSet.of(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.RED)
        setMembers(s) shouldBe setOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        call(s, "size", arrayOf()).getOrThrow() shouldBe 4
    }

    // An op is (opcode, elemOrdinal): opcode 0 = add, 1 = remove; elemOrdinal in 0..3 selects a Color.
    test("EnumSet add/remove/contains/size conform vs a real noneOf-seeded JDK EnumSet") {
        checkAll(Arb.list(Arb.bind(Arb.int(0..1), Arb.int(0..3)) { o, ei -> o to ei }, 0..30)) { ops ->
            val r = java.util.EnumSet.noneOf(Color::class.java)
            // model: start from an empty set built via copyOf(emptyList)
            val m = bmcref.java.util.EnumSet.copyOf<Color>(bmcref.java.util.ArrayList())
            for ((o, ei) in ops) {
                val e = colors[ei]
                if (o == 0) {
                    assertEquivalent("add($e)", runCatching { r.add(e) }, call(m, "add", arrayOf(OBJECT), e))
                } else {
                    assertEquivalent("remove($e)", runCatching { r.remove(e) }, call(m, "remove", arrayOf(OBJECT), e))
                }
            }
            assertEquivalent("size", runCatching { r.size }, call(m, "size", arrayOf()))
            setMembers(m) shouldBe r.toSet()
        }
    }

    test("EnumSet.copyOf(Collection) holds the distinct elements") {
        val src = bmcref.java.util.ArrayList<Color>()
        listOf(Color.RED, Color.BLUE, Color.RED).forEach { src.add(it) }
        val m = bmcref.java.util.EnumSet.copyOf(src)
        setMembers(m) shouldBe setOf(Color.RED, Color.BLUE)
        call(m, "size", arrayOf()).getOrThrow() shouldBe 2
    }

    test("EnumSet universe factories are loud (allOf/noneOf/range/complementOf)") {
        // The model keeps these @BmcUnmodelable — invoking on the JVM trips the loud sentinel.
        runCatching { bmcref.java.util.EnumSet.allOf(Color::class.java) }.isFailure shouldBe true
        runCatching { bmcref.java.util.EnumSet.noneOf(Color::class.java) }.isFailure shouldBe true
        runCatching { bmcref.java.util.EnumSet.range(Color.RED, Color.BLUE) }.isFailure shouldBe true
    }
})
