package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Differential conformance for the OptionalInt model (an {@code int} + present flag). The real
 * {@code java.util.OptionalInt} and the relocated {@code bmcref.java.util.OptionalInt} are different
 * types with the same surface; each op is run on both and the observable (primitive return, boolean,
 * branch hits, exception type, or stream count) is compared. Present and empty are both exercised
 * (empty is built once via {@code empty()}; present via {@code of(v)}).
 */
class OptionalIntConformanceTest : FunSpec({

    test("of(v): getAsInt / isPresent / isEmpty / orElse / orElseGet conform") {
        checkAll(Arb.int(-1000..1000), Arb.int(-9..9)) { v, d ->
            val r = java.util.OptionalInt.of(v)
            val m = bmcref.java.util.OptionalInt.of(v)
            r.isPresent shouldBe m.isPresent
            r.isEmpty shouldBe m.isEmpty
            r.asInt shouldBe m.asInt
            r.orElse(d) shouldBe m.orElse(d)
            r.orElseGet { d } shouldBe m.orElseGet { d }
            r.orElseThrow() shouldBe m.orElseThrow()
        }
    }

    test("empty(): getAsInt throws like the JDK; orElse/orElseGet fall back; stream is empty") {
        val r = java.util.OptionalInt.empty()
        val m = bmcref.java.util.OptionalInt.empty()
        r.isPresent shouldBe m.isPresent
        r.isEmpty shouldBe m.isEmpty
        assertSameException(runCatching { r.asInt }, runCatching { m.asInt })
        assertSameException(runCatching { r.orElseThrow() }, runCatching { m.orElseThrow() })
        r.orElse(7) shouldBe m.orElse(7)
        r.orElseGet { 7 } shouldBe m.orElseGet { 7 }
        r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
    }

    test("orElseThrow(Supplier): value when present, supplied exception when empty") {
        checkAll(Arb.int(-1000..1000)) { v ->
            val rp = java.util.OptionalInt.of(v)
            val mp = bmcref.java.util.OptionalInt.of(v)
            rp.orElseThrow { IllegalStateException() } shouldBe mp.orElseThrow { IllegalStateException() }
            val re = runCatching { java.util.OptionalInt.empty().orElseThrow { IllegalStateException("x") } }
            val me = runCatching { bmcref.java.util.OptionalInt.empty().orElseThrow { IllegalStateException("x") } }
            (re.exceptionOrNull()?.javaClass) shouldBe (me.exceptionOrNull()?.javaClass)
        }
    }

    test("ifPresent / ifPresentOrElse run exactly the right branch") {
        checkAll(Arb.int(-1000..1000)) { v ->
            val rHit = intArrayOf(0, 0); val mHit = intArrayOf(0, 0)
            java.util.OptionalInt.of(v).ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            bmcref.java.util.OptionalInt.of(v).ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            java.util.OptionalInt.empty().ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            bmcref.java.util.OptionalInt.empty().ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            mHit.toList() shouldBe rHit.toList()
        }
    }

    test("stream(): present -> count 1 and same single value; empty -> count 0") {
        checkAll(Arb.int(-1000..1000)) { v ->
            val r = java.util.OptionalInt.of(v)
            val m = bmcref.java.util.OptionalInt.of(v)
            r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
            r.stream().sum() shouldBe call(m.stream(), "sum", arrayOf()).getOrThrow()
        }
    }
})

/** Differential conformance for the OptionalLong model (a {@code long} + present flag). Mirrors
 *  {@link OptionalIntConformanceTest} for the primitive {@code long}. */
class OptionalLongConformanceTest : FunSpec({

    test("of(v): getAsLong / isPresent / isEmpty / orElse / orElseGet conform") {
        checkAll(Arb.long(-1_000_000L..1_000_000L), Arb.long(-9L..9L)) { v, d ->
            val r = java.util.OptionalLong.of(v)
            val m = bmcref.java.util.OptionalLong.of(v)
            r.isPresent shouldBe m.isPresent
            r.isEmpty shouldBe m.isEmpty
            r.asLong shouldBe m.asLong
            r.orElse(d) shouldBe m.orElse(d)
            r.orElseGet { d } shouldBe m.orElseGet { d }
            r.orElseThrow() shouldBe m.orElseThrow()
        }
    }

    test("empty(): getAsLong throws like the JDK; orElse/orElseGet fall back; stream is empty") {
        val r = java.util.OptionalLong.empty()
        val m = bmcref.java.util.OptionalLong.empty()
        r.isPresent shouldBe m.isPresent
        r.isEmpty shouldBe m.isEmpty
        assertSameException(runCatching { r.asLong }, runCatching { m.asLong })
        assertSameException(runCatching { r.orElseThrow() }, runCatching { m.orElseThrow() })
        r.orElse(7L) shouldBe m.orElse(7L)
        r.orElseGet { 7L } shouldBe m.orElseGet { 7L }
        r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
    }

    test("orElseThrow(Supplier): value when present, supplied exception when empty") {
        checkAll(Arb.long(-1_000_000L..1_000_000L)) { v ->
            val rp = java.util.OptionalLong.of(v)
            val mp = bmcref.java.util.OptionalLong.of(v)
            rp.orElseThrow { IllegalStateException() } shouldBe mp.orElseThrow { IllegalStateException() }
            val re = runCatching { java.util.OptionalLong.empty().orElseThrow { IllegalStateException("x") } }
            val me = runCatching { bmcref.java.util.OptionalLong.empty().orElseThrow { IllegalStateException("x") } }
            (re.exceptionOrNull()?.javaClass) shouldBe (me.exceptionOrNull()?.javaClass)
        }
    }

    test("ifPresent / ifPresentOrElse run exactly the right branch") {
        checkAll(Arb.long(-1_000_000L..1_000_000L)) { v ->
            val rHit = intArrayOf(0, 0); val mHit = intArrayOf(0, 0)
            java.util.OptionalLong.of(v).ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            bmcref.java.util.OptionalLong.of(v).ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            java.util.OptionalLong.empty().ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            bmcref.java.util.OptionalLong.empty().ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            mHit.toList() shouldBe rHit.toList()
        }
    }

    test("stream(): present -> count 1 and same single value; empty -> count 0") {
        checkAll(Arb.long(-1_000_000L..1_000_000L)) { v ->
            val r = java.util.OptionalLong.of(v)
            val m = bmcref.java.util.OptionalLong.of(v)
            r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
            r.stream().sum() shouldBe call(m.stream(), "sum", arrayOf()).getOrThrow()
        }
    }
})

/** Differential conformance for the OptionalDouble model (a {@code double} + present flag). Mirrors
 *  {@link OptionalIntConformanceTest} for the primitive {@code double}. Inputs are finite, non-NaN so
 *  the {@code ==}-based assertions are well-defined; OptionalDouble itself contains no Double.compare /
 *  total-order op (its values are stored and returned by primitive identity), so this is sound. */
class OptionalDoubleConformanceTest : FunSpec({

    // Finite, non-NaN values keep the value-equality assertions well-defined (NaN != NaN).
    val finite = Arb.double(-1_000_000.0, 1_000_000.0).filter { it.isFinite() }

    test("of(v): getAsDouble / isPresent / isEmpty / orElse / orElseGet conform") {
        checkAll(finite, Arb.double(-9.0, 9.0).filter { it.isFinite() }) { v, d ->
            val r = java.util.OptionalDouble.of(v)
            val m = bmcref.java.util.OptionalDouble.of(v)
            r.isPresent shouldBe m.isPresent
            r.isEmpty shouldBe m.isEmpty
            r.asDouble shouldBe m.asDouble
            r.orElse(d) shouldBe m.orElse(d)
            r.orElseGet { d } shouldBe m.orElseGet { d }
            r.orElseThrow() shouldBe m.orElseThrow()
        }
    }

    test("empty(): getAsDouble throws like the JDK; orElse/orElseGet fall back; stream is empty") {
        val r = java.util.OptionalDouble.empty()
        val m = bmcref.java.util.OptionalDouble.empty()
        r.isPresent shouldBe m.isPresent
        r.isEmpty shouldBe m.isEmpty
        assertSameException(runCatching { r.asDouble }, runCatching { m.asDouble })
        assertSameException(runCatching { r.orElseThrow() }, runCatching { m.orElseThrow() })
        r.orElse(7.0) shouldBe m.orElse(7.0)
        r.orElseGet { 7.0 } shouldBe m.orElseGet { 7.0 }
        r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
    }

    test("orElseThrow(Supplier): value when present, supplied exception when empty") {
        checkAll(finite) { v ->
            val rp = java.util.OptionalDouble.of(v)
            val mp = bmcref.java.util.OptionalDouble.of(v)
            rp.orElseThrow { IllegalStateException() } shouldBe mp.orElseThrow { IllegalStateException() }
            val re = runCatching { java.util.OptionalDouble.empty().orElseThrow { IllegalStateException("x") } }
            val me = runCatching { bmcref.java.util.OptionalDouble.empty().orElseThrow { IllegalStateException("x") } }
            (re.exceptionOrNull()?.javaClass) shouldBe (me.exceptionOrNull()?.javaClass)
        }
    }

    test("ifPresent / ifPresentOrElse run exactly the right branch") {
        checkAll(finite) { v ->
            val rHit = intArrayOf(0, 0); val mHit = intArrayOf(0, 0)
            java.util.OptionalDouble.of(v).ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            bmcref.java.util.OptionalDouble.of(v).ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            java.util.OptionalDouble.empty().ifPresentOrElse({ rHit[0]++ }, { rHit[1]++ })
            bmcref.java.util.OptionalDouble.empty().ifPresentOrElse({ mHit[0]++ }, { mHit[1]++ })
            mHit.toList() shouldBe rHit.toList()
        }
    }

    test("stream(): present -> count 1 and same single sum; empty -> count 0") {
        checkAll(finite) { v ->
            val r = java.util.OptionalDouble.of(v)
            val m = bmcref.java.util.OptionalDouble.of(v)
            r.stream().count() shouldBe call(m.stream(), "count", arrayOf()).getOrThrow()
            r.stream().sum() shouldBe call(m.stream(), "sum", arrayOf()).getOrThrow()
        }
    }
})
