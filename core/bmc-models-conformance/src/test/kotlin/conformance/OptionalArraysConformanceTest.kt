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
