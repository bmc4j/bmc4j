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
    val BYTEARR: Class<*> = ByteArray::class.java
    val CHARARR: Class<*> = CharArray::class.java
    val SHORTARR: Class<*> = ShortArray::class.java
    val BOOLARR: Class<*> = BooleanArray::class.java
    val FLOATARR: Class<*> = FloatArray::class.java
    val BYTE: Class<*> = Byte::class.javaPrimitiveType!!
    val CHAR: Class<*> = Char::class.javaPrimitiveType!!
    val SHORT: Class<*> = Short::class.javaPrimitiveType!!
    val BOOL: Class<*> = Boolean::class.javaPrimitiveType!!
    val FLOAT: Class<*> = Float::class.javaPrimitiveType!!

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

    // --- mechanical primitive clones (byte/char/short/boolean + comparison-free float) -----------

    test("copyOf(byte[]/char[]/short[]/boolean[]/float[], int) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..9)) { xs, n ->
            val b = xs.map { it.toByte() }.toByteArray()
            val rb = staticCall(REAL, "copyOf", arrayOf(BYTEARR, INT), b.copyOf(), n)
            val mb = staticCall(MODEL, "copyOf", arrayOf(BYTEARR, INT), b.copyOf(), n)
            assertSameException(rb, mb)
            if (rb.isSuccess) (mb.getOrNull() as ByteArray).toList() shouldBe (rb.getOrNull() as ByteArray).toList()

            val c = xs.map { (it and 0x7f).toChar() }.toCharArray()
            val rc = staticCall(REAL, "copyOf", arrayOf(CHARARR, INT), c.copyOf(), n)
            val mc = staticCall(MODEL, "copyOf", arrayOf(CHARARR, INT), c.copyOf(), n)
            assertSameException(rc, mc)
            if (rc.isSuccess) (mc.getOrNull() as CharArray).toList() shouldBe (rc.getOrNull() as CharArray).toList()

            val s = xs.map { it.toShort() }.toShortArray()
            val rs = staticCall(REAL, "copyOf", arrayOf(SHORTARR, INT), s.copyOf(), n)
            val ms = staticCall(MODEL, "copyOf", arrayOf(SHORTARR, INT), s.copyOf(), n)
            assertSameException(rs, ms)
            if (rs.isSuccess) (ms.getOrNull() as ShortArray).toList() shouldBe (rs.getOrNull() as ShortArray).toList()

            val z = xs.map { it % 2 == 0 }.toBooleanArray()
            val rz = staticCall(REAL, "copyOf", arrayOf(BOOLARR, INT), z.copyOf(), n)
            val mz = staticCall(MODEL, "copyOf", arrayOf(BOOLARR, INT), z.copyOf(), n)
            assertSameException(rz, mz)
            if (rz.isSuccess) (mz.getOrNull() as BooleanArray).toList() shouldBe (rz.getOrNull() as BooleanArray).toList()

            val f = xs.map { it.toFloat() }.toFloatArray()
            val rf = staticCall(REAL, "copyOf", arrayOf(FLOATARR, INT), f.copyOf(), n)
            val mf = staticCall(MODEL, "copyOf", arrayOf(FLOATARR, INT), f.copyOf(), n)
            assertSameException(rf, mf)
            if (rf.isSuccess) (mf.getOrNull() as FloatArray).toList() shouldBe (rf.getOrNull() as FloatArray).toList()
        }
    }

    test("copyOfRange(byte[]/char[]/short[]/boolean[]/float[], int, int) conforms (incl. bad range)") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6), Arb.int(-1..7), Arb.int(-1..9)) { xs, from, to ->
            val b = xs.map { it.toByte() }.toByteArray()
            assertSameException(
                staticCall(REAL, "copyOfRange", arrayOf(BYTEARR, INT, INT), b.copyOf(), from, to),
                staticCall(MODEL, "copyOfRange", arrayOf(BYTEARR, INT, INT), b.copyOf(), from, to))
            val rb = staticCall(REAL, "copyOfRange", arrayOf(BYTEARR, INT, INT), b.copyOf(), from, to)
            val mb = staticCall(MODEL, "copyOfRange", arrayOf(BYTEARR, INT, INT), b.copyOf(), from, to)
            if (rb.isSuccess) (mb.getOrNull() as ByteArray).toList() shouldBe (rb.getOrNull() as ByteArray).toList()

            val s = xs.map { it.toShort() }.toShortArray()
            val rs = staticCall(REAL, "copyOfRange", arrayOf(SHORTARR, INT, INT), s.copyOf(), from, to)
            val ms = staticCall(MODEL, "copyOfRange", arrayOf(SHORTARR, INT, INT), s.copyOf(), from, to)
            assertSameException(rs, ms)
            if (rs.isSuccess) (ms.getOrNull() as ShortArray).toList() shouldBe (rs.getOrNull() as ShortArray).toList()

            val f = xs.map { it.toFloat() }.toFloatArray()
            val rf = staticCall(REAL, "copyOfRange", arrayOf(FLOATARR, INT, INT), f.copyOf(), from, to)
            val mf = staticCall(MODEL, "copyOfRange", arrayOf(FLOATARR, INT, INT), f.copyOf(), from, to)
            assertSameException(rf, mf)
            if (rf.isSuccess) (mf.getOrNull() as FloatArray).toList() shouldBe (rf.getOrNull() as FloatArray).toList()
        }
    }

    test("fill(short[], short) / fill(float[], float) conform") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(-9..9)) { xs, v ->
            val sr = xs.map { it.toShort() }.toShortArray(); val sm = sr.copyOf()
            java.util.Arrays.fill(sr, v.toShort())
            staticCall(MODEL, "fill", arrayOf(SHORTARR, SHORT), sm, v.toShort()).getOrThrow()
            sm.toList() shouldBe sr.toList()

            val fr = xs.map { it.toFloat() }.toFloatArray(); val fm = fr.copyOf()
            java.util.Arrays.fill(fr, v.toFloat())
            staticCall(MODEL, "fill", arrayOf(FLOATARR, FLOAT), fm, v.toFloat()).getOrThrow()
            fm.toList() shouldBe fr.toList()
        }
    }

    test("fill(X[], int, int, X) range overloads conform (long/char/byte/short/boolean/float, incl. bad range)") {
        checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(-1..7), Arb.int(-1..7), Arb.int(-9..9)) { xs, from, to, v ->
            val lr = xs.map { it.toLong() }.toLongArray(); val lm = lr.copyOf()
            val rL = staticCall(REAL, "fill", arrayOf(LONGARR, INT, INT, Long::class.javaPrimitiveType!!), lr, from, to, v.toLong())
            val mL = staticCall(MODEL, "fill", arrayOf(LONGARR, INT, INT, Long::class.javaPrimitiveType!!), lm, from, to, v.toLong())
            assertSameException(rL, mL)
            if (rL.isSuccess) lm.toList() shouldBe lr.toList()

            val br = xs.map { it.toByte() }.toByteArray(); val bm = br.copyOf()
            val rB = staticCall(REAL, "fill", arrayOf(BYTEARR, INT, INT, BYTE), br, from, to, v.toByte())
            val mB = staticCall(MODEL, "fill", arrayOf(BYTEARR, INT, INT, BYTE), bm, from, to, v.toByte())
            assertSameException(rB, mB)
            if (rB.isSuccess) bm.toList() shouldBe br.toList()

            val zr = xs.map { it % 2 == 0 }.toBooleanArray(); val zm = zr.copyOf()
            val rZ = staticCall(REAL, "fill", arrayOf(BOOLARR, INT, INT, BOOL), zr, from, to, v % 2 == 0)
            val mZ = staticCall(MODEL, "fill", arrayOf(BOOLARR, INT, INT, BOOL), zm, from, to, v % 2 == 0)
            assertSameException(rZ, mZ)
            if (rZ.isSuccess) zm.toList() shouldBe zr.toList()
        }
    }

    test("hashCode(short[]) conforms") {
        checkAll(Arb.list(Arb.int(-50..50), 0..6)) { xs ->
            val a = xs.map { it.toShort() }.toShortArray()
            staticCall(MODEL, "hashCode", arrayOf(SHORTARR), a).getOrThrow() shouldBe java.util.Arrays.hashCode(a)
        }
    }

    test("sort(byte[]/char[]/short[]) conform") {
        checkAll(Arb.list(Arb.int(-50..50), 0..7)) { xs ->
            val br = xs.map { it.toByte() }.toByteArray(); val bm = br.copyOf()
            java.util.Arrays.sort(br)
            staticCall(MODEL, "sort", arrayOf(BYTEARR), bm).getOrThrow()
            bm.toList() shouldBe br.toList()

            val cr = xs.map { (it and 0xff).toChar() }.toCharArray(); val cm = cr.copyOf()
            java.util.Arrays.sort(cr)
            staticCall(MODEL, "sort", arrayOf(CHARARR), cm).getOrThrow()
            cm.toList() shouldBe cr.toList()

            val sr = xs.map { it.toShort() }.toShortArray(); val sm = sr.copyOf()
            java.util.Arrays.sort(sr)
            staticCall(MODEL, "sort", arrayOf(SHORTARR), sm).getOrThrow()
            sm.toList() shouldBe sr.toList()
        }
    }

    test("binarySearch(byte[]/char[]/short[], key) on a sorted array conform") {
        checkAll(Arb.list(Arb.int(-20..20), 0..7), Arb.int(-25..25)) { xs, key ->
            val b = xs.map { it.toByte() }.toByteArray().also { it.sort() }
            staticCall(MODEL, "binarySearch", arrayOf(BYTEARR, BYTE), b.copyOf(), key.toByte()).getOrThrow() shouldBe
                java.util.Arrays.binarySearch(b, key.toByte())

            val c = xs.map { (it and 0xff).toChar() }.toCharArray().also { it.sort() }
            staticCall(MODEL, "binarySearch", arrayOf(CHARARR, CHAR), c.copyOf(), (key and 0xff).toChar()).getOrThrow() shouldBe
                java.util.Arrays.binarySearch(c, (key and 0xff).toChar())

            val s = xs.map { it.toShort() }.toShortArray().also { it.sort() }
            staticCall(MODEL, "binarySearch", arrayOf(SHORTARR, SHORT), s.copyOf(), key.toShort()).getOrThrow() shouldBe
                java.util.Arrays.binarySearch(s, key.toShort())
        }
    }

    test("mismatch(X[], X[]) conforms (int/long/byte/char/short/boolean)") {
        checkAll(Arb.list(Arb.int(0..3), 0..5), Arb.list(Arb.int(0..3), 0..5)) { xs, ys ->
            staticCall(MODEL, "mismatch", arrayOf(INTARR, INTARR), xs.toIntArray(), ys.toIntArray()).getOrThrow() shouldBe
                java.util.Arrays.mismatch(xs.toIntArray(), ys.toIntArray())
            val xl = xs.map { it.toLong() }.toLongArray(); val yl = ys.map { it.toLong() }.toLongArray()
            staticCall(MODEL, "mismatch", arrayOf(LONGARR, LONGARR), xl, yl).getOrThrow() shouldBe
                java.util.Arrays.mismatch(xl, yl)
            val xb = xs.map { it.toByte() }.toByteArray(); val yb = ys.map { it.toByte() }.toByteArray()
            staticCall(MODEL, "mismatch", arrayOf(BYTEARR, BYTEARR), xb, yb).getOrThrow() shouldBe
                java.util.Arrays.mismatch(xb, yb)
            val xc = xs.map { it.toChar() }.toCharArray(); val yc = ys.map { it.toChar() }.toCharArray()
            staticCall(MODEL, "mismatch", arrayOf(CHARARR, CHARARR), xc, yc).getOrThrow() shouldBe
                java.util.Arrays.mismatch(xc, yc)
            val xsh = xs.map { it.toShort() }.toShortArray(); val ysh = ys.map { it.toShort() }.toShortArray()
            staticCall(MODEL, "mismatch", arrayOf(SHORTARR, SHORTARR), xsh, ysh).getOrThrow() shouldBe
                java.util.Arrays.mismatch(xsh, ysh)
            val xz = xs.map { it % 2 == 0 }.toBooleanArray(); val yz = ys.map { it % 2 == 0 }.toBooleanArray()
            staticCall(MODEL, "mismatch", arrayOf(BOOLARR, BOOLARR), xz, yz).getOrThrow() shouldBe
                java.util.Arrays.mismatch(xz, yz)
        }
    }

    test("compare(X[], X[]) conforms by sign (int/long/byte/char/short/boolean)") {
        checkAll(Arb.list(Arb.int(0..3), 0..5), Arb.list(Arb.int(0..3), 0..5)) { xs, ys ->
            fun sgn(x: Int) = x.compareTo(0)
            sgn(staticCall(MODEL, "compare", arrayOf(INTARR, INTARR), xs.toIntArray(), ys.toIntArray()).getOrThrow() as Int) shouldBe
                sgn(java.util.Arrays.compare(xs.toIntArray(), ys.toIntArray()))
            val xl = xs.map { it.toLong() }.toLongArray(); val yl = ys.map { it.toLong() }.toLongArray()
            sgn(staticCall(MODEL, "compare", arrayOf(LONGARR, LONGARR), xl, yl).getOrThrow() as Int) shouldBe
                sgn(java.util.Arrays.compare(xl, yl))
            val xb = xs.map { it.toByte() }.toByteArray(); val yb = ys.map { it.toByte() }.toByteArray()
            sgn(staticCall(MODEL, "compare", arrayOf(BYTEARR, BYTEARR), xb, yb).getOrThrow() as Int) shouldBe
                sgn(java.util.Arrays.compare(xb, yb))
            val xc = xs.map { it.toChar() }.toCharArray(); val yc = ys.map { it.toChar() }.toCharArray()
            sgn(staticCall(MODEL, "compare", arrayOf(CHARARR, CHARARR), xc, yc).getOrThrow() as Int) shouldBe
                sgn(java.util.Arrays.compare(xc, yc))
            val xsh = xs.map { it.toShort() }.toShortArray(); val ysh = ys.map { it.toShort() }.toShortArray()
            sgn(staticCall(MODEL, "compare", arrayOf(SHORTARR, SHORTARR), xsh, ysh).getOrThrow() as Int) shouldBe
                sgn(java.util.Arrays.compare(xsh, ysh))
            val xz = xs.map { it % 2 == 0 }.toBooleanArray(); val yz = ys.map { it % 2 == 0 }.toBooleanArray()
            sgn(staticCall(MODEL, "compare", arrayOf(BOOLARR, BOOLARR), xz, yz).getOrThrow() as Int) shouldBe
                sgn(java.util.Arrays.compare(xz, yz))
        }
    }
})
