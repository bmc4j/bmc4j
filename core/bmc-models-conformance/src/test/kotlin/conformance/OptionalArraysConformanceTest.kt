package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

/** Differential conformance for the Optional model (value + present flag). */
class OptionalConformanceTest : FunSpec({

    test("ofNullable / isPresent / orElse / get conform") {
        checkAll(Arb.int(0..9).orNull(0.25)) { v ->
            val r = java.util.Optional.ofNullable(v)
            val m = bmcref.java.util.Optional.ofNullable(v)
            assertEquivalent("isPresent", call(r, "isPresent", arrayOf()), call(m, "isPresent", arrayOf()))
            assertEquivalent("isEmpty", call(r, "isEmpty", arrayOf()), call(m, "isEmpty", arrayOf()))
            assertEquivalent("orElse(-1)", call(r, "orElse", arrayOf(OBJECT), -1), call(m, "orElse", arrayOf(OBJECT), -1))
            assertEquivalent("get", call(r, "get", arrayOf()), call(m, "get", arrayOf()))   // empty -> both throw
        }
    }

    test("of(null) throws like the JDK") {
        val r = staticCall(java.util.Optional::class.java, "of", arrayOf(OBJECT), null)
        val m = staticCall(bmcref.java.util.Optional::class.java, "of", arrayOf(OBJECT), null)
        assertSameException(r, m)
    }

    test("empty().get() throws like the JDK") {
        val r = runCatching { java.util.Optional.empty<Any?>().get() }
        val m = runCatching { bmcref.java.util.Optional.empty<Any?>().get() }
        assertSameException(r, m)
    }

    // --- added surface: orElseThrow(Supplier) / flatMap / ifPresentOrElse / or / stream ------------
    // Lambdas pass directly (functional interfaces aren't relocated). flatMap/or return Optionals of
    // different types (real vs model), so we compare the observable outcome (present? then value).
    test("orElseThrow(Supplier) / flatMap / ifPresentOrElse / or / stream conform") {
        checkAll(Arb.int(0..9).orNull(0.3)) { v ->
            val r = java.util.Optional.ofNullable(v)
            val m = bmcref.java.util.Optional.ofNullable(v)

            // orElseThrow(supplier): value when present, the supplied exception when empty.
            val rT = runCatching { r.orElseThrow { IllegalStateException("x") } }
            val mT = runCatching { m.orElseThrow { IllegalStateException("x") } }
            rT.getOrNull() shouldBe mT.getOrNull()
            (rT.exceptionOrNull()?.javaClass) shouldBe (mT.exceptionOrNull()?.javaClass)

            // flatMap: present -> mapper's Optional; empty -> empty. Mapper doubles into an Optional.
            val rF = r.flatMap { java.util.Optional.of(it!! * 2) }
            val mF = m.flatMap { bmcref.java.util.Optional.of(it!! * 2) }
            rF.isPresent shouldBe mF.isPresent
            if (rF.isPresent) rF.get() shouldBe mF.get()

            // ifPresentOrElse: exactly one branch runs.
            val rHit = intArrayOf(0, 0); val mHit = intArrayOf(0, 0)
            r.ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            m.ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            mHit.toList() shouldBe rHit.toList()

            // or: present -> this; empty -> the supplied Optional.
            val rO = r.or { java.util.Optional.of(-1) }
            val mO = m.or { bmcref.java.util.Optional.of(-1) }
            rO.isPresent shouldBe mO.isPresent
            rO.get() shouldBe mO.get()

            // stream: 0 or 1 element; compare counts and (when present) the single value.
            r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
        }
    }
})

/** Differential conformance for Arrays.asList (the one modeled Arrays method). */
class ArraysConformanceTest : FunSpec({

    test("Arrays.asList conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..6)) { xs ->
            val arr = xs.toTypedArray()
            // Real Arrays.asList returns the non-public java.util.Arrays$ArrayList; drive it through
            // the public java.util.List interface (reflecting its concrete class is denied). The
            // model returns a public class, so it goes through the reflective call helper.
            val r = java.util.Arrays.asList(*arr)   // Kotlin sees a List<Int>; calls go via the public interface
            val m = bmcref.java.util.Arrays.asList(*arr)
            r.size shouldBe (call(m, "size", arrayOf()).getOrThrow() as Int)
            for (i in xs.indices) {
                r[i] shouldBe call(m, "get", arrayOf(INT), i).getOrThrow()
            }
            for (q in 0..9) {
                r.contains(q) shouldBe call(m, "contains", arrayOf(OBJECT), q).getOrThrow()
            }
        }
    }
})

/**
 * Differential conformance for the bounded Arrays array-utility surface (copyOf/copyOfRange/fill/
 * equals/hashCode/sort/binarySearch/stream/setAll). The real {@code java.util.Arrays} and the
 * relocated {@code bmcref.java.util.Arrays} are different classes with the same static surface, so
 * each method is invoked on both and the observable (returned array contents, boolean, hash, or
 * exception type) is compared. Generators stay inside the small bounds the model documents.
 */
class ArraysUtilConformanceTest : FunSpec({

    val REAL = java.util.Arrays::class.java
    val MODEL = bmcref.java.util.Arrays::class.java
    val INTARR: Class<*> = IntArray::class.java
    val LONGARR: Class<*> = LongArray::class.java
    val OBJARR: Class<*> = Array<Any?>::class.java

    // --- copyOf ---------------------------------------------------------------------------------
    test("copyOf(int[], int) conforms (truncate / zero-pad / negative)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..9)) { xs, n ->
            val a = xs.toIntArray()
            val r = staticCall(REAL, "copyOf", arrayOf(INTARR, INT), a.copyOf(), n)
            val m = staticCall(MODEL, "copyOf", arrayOf(INTARR, INT), a.copyOf(), n)
            assertSameException(r, m)
            if (r.isSuccess) (m.getOrNull() as IntArray).toList() shouldBe (r.getOrNull() as IntArray).toList()
        }
    }

    test("copyOf(long[], int) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(0..9)) { xs, n ->
            val a = xs.map { it.toLong() }.toLongArray()
            val r = staticCall(REAL, "copyOf", arrayOf(LONGARR, INT), a.copyOf(), n)
            val m = staticCall(MODEL, "copyOf", arrayOf(LONGARR, INT), a.copyOf(), n)
            (m.getOrNull() as LongArray).toList() shouldBe (r.getOrNull() as LongArray).toList()
        }
    }

    test("copyOf(Object[], int) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(0..9)) { xs, n ->
            val a: Array<Any?> = xs.toTypedArray()
            val r = staticCall(REAL, "copyOf", arrayOf(OBJARR, INT), a.copyOf(), n)
            val m = staticCall(MODEL, "copyOf", arrayOf(OBJARR, INT), a.copyOf(), n)
            @Suppress("UNCHECKED_CAST")
            (m.getOrNull() as Array<Any?>).toList() shouldBe (r.getOrNull() as Array<Any?>).toList()
        }
    }

    // --- copyOfRange ----------------------------------------------------------------------------
    test("copyOfRange(int[], int, int) conforms (incl. out-of-range / from>to)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..7), Arb.int(-1..9)) { xs, from, to ->
            val a = xs.toIntArray()
            val r = staticCall(REAL, "copyOfRange", arrayOf(INTARR, INT, INT), a.copyOf(), from, to)
            val m = staticCall(MODEL, "copyOfRange", arrayOf(INTARR, INT, INT), a.copyOf(), from, to)
            assertSameException(r, m)
            if (r.isSuccess) (m.getOrNull() as IntArray).toList() shouldBe (r.getOrNull() as IntArray).toList()
        }
    }

    test("copyOfRange(Object[], int, int) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(0..6), Arb.int(0..9)) { xs, from0, to0 ->
            val a: Array<Any?> = xs.toTypedArray()
            val from = minOf(from0, a.size)
            val to = maxOf(from, to0)
            val r = staticCall(REAL, "copyOfRange", arrayOf(OBJARR, INT, INT), a.copyOf(), from, to)
            val m = staticCall(MODEL, "copyOfRange", arrayOf(OBJARR, INT, INT), a.copyOf(), from, to)
            assertSameException(r, m)
            if (r.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                (m.getOrNull() as Array<Any?>).toList() shouldBe (r.getOrNull() as Array<Any?>).toList()
            }
        }
    }

    // --- fill -----------------------------------------------------------------------------------
    test("fill(int[], int) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(-9..9)) { xs, v ->
            val ar = xs.toIntArray(); val am = xs.toIntArray()
            java.util.Arrays.fill(ar, v)
            staticCall(MODEL, "fill", arrayOf(INTARR, INT), am, v).getOrThrow()
            am.toList() shouldBe ar.toList()
        }
    }

    test("fill(int[], int, int, int) conforms (incl. bad range)") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(-1..7), Arb.int(-1..7), Arb.int(-9..9)) { xs, from, to, v ->
            val ar = xs.toIntArray(); val am = xs.toIntArray()
            val r = staticCall(REAL, "fill", arrayOf(INTARR, INT, INT, INT), ar, from, to, v)
            val m = staticCall(MODEL, "fill", arrayOf(INTARR, INT, INT, INT), am, from, to, v)
            assertSameException(r, m)
            if (r.isSuccess) am.toList() shouldBe ar.toList()
        }
    }

    test("fill(Object[], Object) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(0..9)) { xs, v ->
            val ar: Array<Any?> = xs.toTypedArray(); val am: Array<Any?> = xs.toTypedArray()
            java.util.Arrays.fill(ar, v)
            staticCall(MODEL, "fill", arrayOf(OBJARR, OBJECT), am, v).getOrThrow()
            am.toList() shouldBe ar.toList()
        }
    }

    // --- equals ---------------------------------------------------------------------------------
    test("equals(int[], int[]) conforms") {
        checkAll(Arb.list(Arb.int(0..3), 0..5), Arb.list(Arb.int(0..3), 0..5)) { xs, ys ->
            val r = java.util.Arrays.equals(xs.toIntArray(), ys.toIntArray())
            val m = staticCall(MODEL, "equals", arrayOf(INTARR, INTARR), xs.toIntArray(), ys.toIntArray())
            m.getOrThrow() shouldBe r
        }
    }

    test("equals(Object[], Object[]) conforms") {
        checkAll(Arb.list(Arb.int(0..3), 0..5), Arb.list(Arb.int(0..3), 0..5)) { xs, ys ->
            val a: Array<Any?> = xs.toTypedArray(); val b: Array<Any?> = ys.toTypedArray()
            val r = java.util.Arrays.equals(a, b)
            val m = staticCall(MODEL, "equals", arrayOf(OBJARR, OBJARR), a, b)
            m.getOrThrow() shouldBe r
        }
    }

    // --- hashCode -------------------------------------------------------------------------------
    test("hashCode(int[]) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6)) { xs ->
            val r = java.util.Arrays.hashCode(xs.toIntArray())
            val m = staticCall(MODEL, "hashCode", arrayOf(INTARR), xs.toIntArray())
            m.getOrThrow() shouldBe r
        }
    }

    test("hashCode(long[]) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6)) { xs ->
            val a = xs.map { it.toLong() }.toLongArray()
            val r = java.util.Arrays.hashCode(a)
            val m = staticCall(MODEL, "hashCode", arrayOf(LONGARR), a)
            m.getOrThrow() shouldBe r
        }
    }

    test("hashCode(Object[]) conforms") {
        checkAll(Arb.list(Arb.int(0..9), 0..6)) { xs ->
            val a: Array<Any?> = xs.toTypedArray()
            val r = java.util.Arrays.hashCode(a)
            val m = staticCall(MODEL, "hashCode", arrayOf(OBJARR), a)
            m.getOrThrow() shouldBe r
        }
    }

    // --- sort -----------------------------------------------------------------------------------
    test("sort(int[]) conforms (insertion vs JDK quicksort -> same sorted order)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..7)) { xs ->
            val ar = xs.toIntArray(); val am = xs.toIntArray()
            java.util.Arrays.sort(ar)
            staticCall(MODEL, "sort", arrayOf(INTARR), am).getOrThrow()
            am.toList() shouldBe ar.toList()
        }
    }

    test("sort(long[]) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..7)) { xs ->
            val ar = xs.map { it.toLong() }.toLongArray(); val am = ar.copyOf()
            java.util.Arrays.sort(ar)
            staticCall(MODEL, "sort", arrayOf(LONGARR), am).getOrThrow()
            am.toList() shouldBe ar.toList()
        }
    }

    test("sort(Object[]) conforms (Comparable elements)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..7)) { xs ->
            val ar: Array<Any?> = xs.toTypedArray(); val am: Array<Any?> = xs.toTypedArray()
            java.util.Arrays.sort(ar)
            staticCall(MODEL, "sort", arrayOf(OBJARR), am).getOrThrow()
            am.toList() shouldBe ar.toList()
        }
    }

    // --- binarySearch (sorted-assume: search a SORTED array) ------------------------------------
    test("binarySearch(int[], int) on a sorted array conforms") {
        checkAll(Arb.list(Arb.int(-20..20), 0..7), Arb.int(-25..25)) { xs, key ->
            val a = xs.toIntArray().also { it.sort() }
            val r = java.util.Arrays.binarySearch(a, key)
            val m = staticCall(MODEL, "binarySearch", arrayOf(INTARR, INT), a.copyOf(), key)
            // The JDK only specifies the found-index when present; insertion-point sign/contract
            // otherwise. Both implementations honor the same contract, so compare directly.
            m.getOrThrow() shouldBe r
        }
    }

    test("binarySearch(long[], long) on a sorted array conforms") {
        checkAll(Arb.list(Arb.int(-20..20), 0..7), Arb.int(-25..25)) { xs, key ->
            val a = xs.map { it.toLong() }.toLongArray().also { it.sort() }
            val r = java.util.Arrays.binarySearch(a, key.toLong())
            val m = staticCall(MODEL, "binarySearch", arrayOf(LONGARR, Long::class.javaPrimitiveType!!), a.copyOf(), key.toLong())
            m.getOrThrow() shouldBe r
        }
    }

    // --- stream / setAll ------------------------------------------------------------------------
    test("stream(int[]).sum() conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6)) { xs ->
            val a = xs.toIntArray()
            val r = java.util.Arrays.stream(a).sum()
            val ms = staticCall(MODEL, "stream", arrayOf(INTARR), a).getOrThrow()!!
            call(ms, "sum", arrayOf()).getOrThrow() shouldBe r
        }
    }
})
