package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

private typealias RealBD = java.math.BigDecimal
private typealias ModelBD = bmcref.java.math.BigDecimal

/** Canonical observable: (unscaled, scale). For the model we read the private fields (its
 *  unscaledValue()/toString() chain isn't itself modeled); for the JDK we use the public API. */
private fun obs(bd: Any): String =
    if (bd is ModelBD) {
        val u = ModelBD::class.java.getDeclaredField("unscaled").apply { isAccessible = true }.getLong(bd)
        val s = ModelBD::class.java.getDeclaredField("scale").apply { isAccessible = true }.getInt(bd)
        "$u@$s"
    } else {
        bd as RealBD
        "${bd.unscaledValue()}@${bd.scale()}"
    }

/**
 * Differential conformance for the BigDecimal model vs java.math.BigDecimal. Inputs are kept inside
 * the model's documented bound (|unscaled| ≤ 10^6, scale ≤ 4) so intermediate rescaling stays within
 * the long range; crossing that bound is the "loud overflow" concern handled by the symbolic-law axis.
 */
class BigDecimalConformanceTest : FunSpec({

    test("add / subtract / multiply / compareTo / equals conform") {
        val u = Arb.long(-1_000_000L..1_000_000L)
        val s = Arb.int(0..4)
        checkAll(u, s, u, s) { uA, sA, uB, sB ->
            val rA = RealBD.valueOf(uA, sA); val rB = RealBD.valueOf(uB, sB)
            val mA = ModelBD.valueOf(uA, sA); val mB = ModelBD.valueOf(uB, sB)
            obs(mA.add(mB)) shouldBe obs(rA.add(rB))
            obs(mA.subtract(mB)) shouldBe obs(rA.subtract(rB))
            obs(mA.multiply(mB)) shouldBe obs(rA.multiply(rB))
            mA.compareTo(mB) shouldBe rA.compareTo(rB)
            (mA as Any).equals(mB) shouldBe (rA as Any).equals(rB)
        }
    }

    // divide(divisor, scale, mode) across every rounding mode, incl. zero-divisor exception parity.
    // The model's roundDiv is the trust-critical rounding kernel; this pins it to the JDK exactly.
    test("divide(divisor, scale, RoundingMode) conforms across rounding modes") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val divU = Arb.long(-1000L..1000L)   // includes 0 -> ArithmeticException parity
        val modes = java.math.RoundingMode.values().filter { it != java.math.RoundingMode.UNNECESSARY }
        checkAll(u, s, divU, s) { uA, sA, uB, sB ->
            val rA = RealBD.valueOf(uA, sA); val rB = RealBD.valueOf(uB, sB)
            val mA = ModelBD.valueOf(uA, sA); val mB = ModelBD.valueOf(uB, sB)
            for (mode in modes) {
                val bmode = bmcref.java.math.RoundingMode.valueOf(mode.name)
                val r = runCatching { rA.divide(rB, 2, mode) }
                val m = runCatching { mA.divide(mB, 2, bmode) }
                withClue("$uA@$sA / $uB@$sB scale=2 [$mode]") {
                    m.exceptionOrNull()?.javaClass shouldBe r.exceptionOrNull()?.javaClass
                    if (r.isSuccess && m.isSuccess) obs(m.getOrThrow()) shouldBe obs(r.getOrThrow())
                }
            }
        }
    }

    // setScale narrowing rounds via the same roundDiv kernel; widening is exact. Cover both directions
    // across all rounding modes.
    test("setScale(newScale, RoundingMode) conforms across rounding modes") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val target = Arb.int(0..4)
        val modes = java.math.RoundingMode.values().filter { it != java.math.RoundingMode.UNNECESSARY }
        checkAll(u, s, target) { uA, sA, ns ->
            val rA = RealBD.valueOf(uA, sA)
            val mA = ModelBD.valueOf(uA, sA)
            for (mode in modes) {
                val bmode = bmcref.java.math.RoundingMode.valueOf(mode.name)
                val r = runCatching { rA.setScale(ns, mode) }
                val m = runCatching { mA.setScale(ns, bmode) }
                withClue("$uA@$sA setScale($ns) [$mode]") {
                    m.exceptionOrNull()?.javaClass shouldBe r.exceptionOrNull()?.javaClass
                    if (r.isSuccess && m.isSuccess) obs(m.getOrThrow()) shouldBe obs(r.getOrThrow())
                }
            }
        }
    }

    // Divergence ledger — "everything else is a bug": the String constructor must accept exactly
    // what the JDK accepts and throw NumberFormatException on the rest (the silent-parse bug).
    test("BigDecimal(String) conforms for valid and invalid inputs") {
        val valid = listOf("0", "12", "12.5", "-3.14", "+7", "0.001", "-0.50", "100", "000", "0.0")
        val invalid = listOf("", "12x4", "1.2.3", "abc", "-", "+", ".", "x", "1 2", "12.3.4", " 5", "5 ")
        for (str in valid + invalid) {
            val real = runCatching { RealBD(str) }
            val model = runCatching { ModelBD(str) }
            withClue("BigDecimal(\"$str\")  real=${real.exceptionOrNull()?.javaClass?.simpleName ?: real.getOrNull()?.let { obs(it) }}  model=${model.exceptionOrNull()?.javaClass?.simpleName ?: model.getOrNull()?.let { obs(it) }}") {
                model.exceptionOrNull()?.javaClass shouldBe real.exceptionOrNull()?.javaClass
                if (real.isSuccess && model.isSuccess) {
                    obs(model.getOrThrow()) shouldBe obs(real.getOrThrow())
                }
            }
        }
    }
})
