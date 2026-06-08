package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
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
