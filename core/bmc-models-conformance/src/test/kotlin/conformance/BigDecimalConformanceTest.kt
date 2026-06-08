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

    // Deprecated int-rounding overloads: divide(divisor, scale, int) / divide(divisor, int) / setScale(
    // newScale, int). The legacy ROUND_* int (== RoundingMode ordinal) must produce exactly the same
    // result as the RoundingMode overload. Driven across every valid constant, plus zero-divisor parity.
    test("deprecated int-rounding overloads conform to their RoundingMode siblings") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val divU = Arb.long(-1000L..1000L)   // includes 0 -> ArithmeticException parity
        val modes = java.math.RoundingMode.values().filter { it != java.math.RoundingMode.UNNECESSARY }
        checkAll(u, s, divU, s) { uA, sA, uB, sB ->
            val rA = RealBD.valueOf(uA, sA); val rB = RealBD.valueOf(uB, sB)
            val mA = ModelBD.valueOf(uA, sA); val mB = ModelBD.valueOf(uB, sB)
            for (mode in modes) {
                val legacyInt = mode.ordinal   // ROUND_* constant == RoundingMode ordinal
                // divide(divisor, scale, int)
                val rDiv = runCatching { rA.divide(rB, 2, mode.ordinal) }
                val mDiv = runCatching { mA.divide(mB, 2, legacyInt) }
                withClue("$uA@$sA / $uB@$sB scale=2 int-mode=$legacyInt [$mode]") {
                    mDiv.exceptionOrNull()?.javaClass shouldBe rDiv.exceptionOrNull()?.javaClass
                    if (rDiv.isSuccess && mDiv.isSuccess) obs(mDiv.getOrThrow()) shouldBe obs(rDiv.getOrThrow())
                }
                // divide(divisor, int) — result scale == this.scale
                val rDiv2 = runCatching { rA.divide(rB, mode.ordinal) }
                val mDiv2 = runCatching { mA.divide(mB, legacyInt) }
                withClue("$uA@$sA / $uB@$sB int-mode=$legacyInt [$mode] (scale=this)") {
                    mDiv2.exceptionOrNull()?.javaClass shouldBe rDiv2.exceptionOrNull()?.javaClass
                    if (rDiv2.isSuccess && mDiv2.isSuccess) obs(mDiv2.getOrThrow()) shouldBe obs(rDiv2.getOrThrow())
                }
            }
        }
    }

    test("deprecated setScale(int, int) conforms to setScale(int, RoundingMode)") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val target = Arb.int(0..4)
        val modes = java.math.RoundingMode.values().filter { it != java.math.RoundingMode.UNNECESSARY }
        checkAll(u, s, target) { uA, sA, ns ->
            val rA = RealBD.valueOf(uA, sA); val mA = ModelBD.valueOf(uA, sA)
            for (mode in modes) {
                val r = runCatching { rA.setScale(ns, mode.ordinal) }
                val m = runCatching { mA.setScale(ns, mode.ordinal) }
                withClue("$uA@$sA setScale($ns, int=${mode.ordinal}) [$mode]") {
                    m.exceptionOrNull()?.javaClass shouldBe r.exceptionOrNull()?.javaClass
                    if (r.isSuccess && m.isSuccess) obs(m.getOrThrow()) shouldBe obs(r.getOrThrow())
                }
            }
        }
    }

    // plus() (unary +): returns this unchanged (unscaled + scale identical) — exact identity parity.
    test("plus() conforms (identity)") {
        val u = Arb.long(-1_000_000L..1_000_000L)
        val s = Arb.int(0..4)
        checkAll(u, s) { uA, sA ->
            obs(ModelBD.valueOf(uA, sA).plus()) shouldBe obs(RealBD.valueOf(uA, sA).plus())
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

    // setScale(int) (the no-rounding overload): widening is exact; narrowing throws "Rounding
    // necessary" (ArithmeticException) unless the dropped digits are all zero. Exception parity + value.
    test("setScale(int) conforms (RoundingMode.UNNECESSARY semantics)") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val target = Arb.int(0..4)
        checkAll(u, s, target) { uA, sA, ns ->
            val rA = RealBD.valueOf(uA, sA)
            val mA = ModelBD.valueOf(uA, sA)
            val r = runCatching { rA.setScale(ns) }
            val m = runCatching { mA.setScale(ns) }
            withClue("$uA@$sA setScale($ns)  real=${r.exceptionOrNull()?.javaClass?.simpleName ?: r.getOrNull()?.let { obs(it) }}  model=${m.exceptionOrNull()?.javaClass?.simpleName ?: m.getOrNull()?.let { obs(it) }}") {
                m.exceptionOrNull()?.javaClass shouldBe r.exceptionOrNull()?.javaClass
                if (r.isSuccess && m.isSuccess) obs(m.getOrThrow()) shouldBe obs(r.getOrThrow())
            }
        }
    }

    // movePointRight/movePointLeft: shift the decimal point n places, absorbing the surplus power of
    // ten into the unscaled value once the scale would go negative. Compare (unscaled, scale) exactly.
    test("movePointRight / movePointLeft conform") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val n = Arb.int(-4..4)
        checkAll(u, s, n) { uA, sA, k ->
            val rA = RealBD.valueOf(uA, sA)
            val mA = ModelBD.valueOf(uA, sA)
            withClue("$uA@$sA movePointRight($k)") { obs(mA.movePointRight(k)) shouldBe obs(rA.movePointRight(k)) }
            withClue("$uA@$sA movePointLeft($k)") { obs(mA.movePointLeft(k)) shouldBe obs(rA.movePointLeft(k)) }
        }
    }

    // divide(BigDecimal) exact: preferred scale this.scale - divisor.scale, extended until exact; a
    // non-terminating expansion (e.g. 1/3) throws ArithmeticException, a zero divisor throws too.
    test("divide(BigDecimal) (exact) conforms incl. non-terminating + zero-divisor throws") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val divU = Arb.long(-1000L..1000L)   // includes 0
        checkAll(u, s, divU, s) { uA, sA, uB, sB ->
            val rA = RealBD.valueOf(uA, sA); val rB = RealBD.valueOf(uB, sB)
            val mA = ModelBD.valueOf(uA, sA); val mB = ModelBD.valueOf(uB, sB)
            val r = runCatching { rA.divide(rB) }
            val m = runCatching { mA.divide(mB) }
            withClue("$uA@$sA / $uB@$sB exact  real=${r.exceptionOrNull()?.javaClass?.simpleName ?: r.getOrNull()?.let { obs(it) }}  model=${m.exceptionOrNull()?.javaClass?.simpleName ?: m.getOrNull()?.let { obs(it) }}") {
                m.exceptionOrNull()?.javaClass shouldBe r.exceptionOrNull()?.javaClass
                if (r.isSuccess && m.isSuccess) obs(m.getOrThrow()) shouldBe obs(r.getOrThrow())
            }
        }
    }

    // divideToIntegralValue / remainder: integer quotient (truncated) + the remainder it leaves; zero
    // divisor throws. Scales follow the JDK (preferred scale this.scale - divisor.scale, clamped at 0
    // for divideToIntegralValue; max(this.scale, divisor.scale) for remainder).
    test("divideToIntegralValue / remainder conform") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        val divU = Arb.long(-1000L..1000L)   // includes 0
        checkAll(u, s, divU, s) { uA, sA, uB, sB ->
            val rA = RealBD.valueOf(uA, sA); val rB = RealBD.valueOf(uB, sB)
            val mA = ModelBD.valueOf(uA, sA); val mB = ModelBD.valueOf(uB, sB)
            val rd = runCatching { rA.divideToIntegralValue(rB) }
            val md = runCatching { mA.divideToIntegralValue(mB) }
            withClue("$uA@$sA divToInt $uB@$sB  real=${rd.exceptionOrNull()?.javaClass?.simpleName ?: rd.getOrNull()?.let { obs(it) }}  model=${md.exceptionOrNull()?.javaClass?.simpleName ?: md.getOrNull()?.let { obs(it) }}") {
                md.exceptionOrNull()?.javaClass shouldBe rd.exceptionOrNull()?.javaClass
                if (rd.isSuccess && md.isSuccess) obs(md.getOrThrow()) shouldBe obs(rd.getOrThrow())
            }
            val rr = runCatching { rA.remainder(rB) }
            val mr = runCatching { mA.remainder(mB) }
            withClue("$uA@$sA remainder $uB@$sB  real=${rr.exceptionOrNull()?.javaClass?.simpleName ?: rr.getOrNull()?.let { obs(it) }}  model=${mr.exceptionOrNull()?.javaClass?.simpleName ?: mr.getOrNull()?.let { obs(it) }}") {
                mr.exceptionOrNull()?.javaClass shouldBe rr.exceptionOrNull()?.javaClass
                if (rr.isSuccess && mr.isSuccess) obs(mr.getOrThrow()) shouldBe obs(rr.getOrThrow())
            }
            // divideAndRemainder == {divideToIntegralValue, remainder} as a pair.
            val rp = runCatching { rA.divideAndRemainder(rB) }
            val mp = runCatching { mA.divideAndRemainder(mB) }
            withClue("$uA@$sA divideAndRemainder $uB@$sB") {
                mp.exceptionOrNull()?.javaClass shouldBe rp.exceptionOrNull()?.javaClass
                if (rp.isSuccess && mp.isSuccess) {
                    obs(mp.getOrThrow()[0]) shouldBe obs(rp.getOrThrow()[0])
                    obs(mp.getOrThrow()[1]) shouldBe obs(rp.getOrThrow()[1])
                }
            }
        }
    }

    // pow(int): exact, scale = this.scale * n; pow(0) == ONE (scale 0); negative/too-large exponent
    // throws ArithmeticException. Small exponents keep the result inside the long bound.
    test("pow(int) conforms for small exponents + invalid-exponent parity") {
        val u = Arb.long(-300L..300L)
        val s = Arb.int(0..2)
        val n = Arb.int(-2..6)
        checkAll(u, s, n) { uA, sA, e ->
            val rA = RealBD.valueOf(uA, sA); val mA = ModelBD.valueOf(uA, sA)
            val r = runCatching { rA.pow(e) }; val m = runCatching { mA.pow(e) }
            withClue("$uA@$sA pow($e)  real=${r.exceptionOrNull()?.javaClass?.simpleName ?: r.getOrNull()?.let { obs(it) }}  model=${m.exceptionOrNull()?.javaClass?.simpleName ?: m.getOrNull()?.let { obs(it) }}") {
                m.exceptionOrNull()?.javaClass shouldBe r.exceptionOrNull()?.javaClass
                if (r.isSuccess && m.isSuccess) obs(m.getOrThrow()) shouldBe obs(r.getOrThrow())
            }
        }
    }

    // scaleByPowerOfTen / ulp / precision: scaleByPowerOfTen subtracts n from the scale (may go
    // negative, unlike movePoint*); ulp is 1 at this scale; precision is the unscaled-digit count.
    test("scaleByPowerOfTen / ulp / precision conform") {
        val u = Arb.long(-1_000_000L..1_000_000L)
        val s = Arb.int(0..4)
        val n = Arb.int(-6..6)
        checkAll(u, s, n) { uA, sA, k ->
            val rA = RealBD.valueOf(uA, sA); val mA = ModelBD.valueOf(uA, sA)
            withClue("$uA@$sA scaleByPowerOfTen($k)") { obs(mA.scaleByPowerOfTen(k)) shouldBe obs(rA.scaleByPowerOfTen(k)) }
            withClue("$uA@$sA ulp") { obs(mA.ulp()) shouldBe obs(rA.ulp()) }
            withClue("$uA@$sA precision") { mA.precision() shouldBe rA.precision() }
        }
    }

    // byte/short narrowing + the byte/short/int/long *Exact variants (exact narrowing, JDK exception
    // parity: a value out of the target range -> Overflow; a nonzero fractional part -> Rounding necessary).
    test("byteValue/shortValue + byte/short/int/long ValueExact conform") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        checkAll(u, s) { uA, sA ->
            val rA = RealBD.valueOf(uA, sA); val mA = ModelBD.valueOf(uA, sA)
            for (op in listOf("byteValue", "shortValue", "byteValueExact", "shortValueExact", "intValueExact", "longValueExact")) {
                val r = call(rA, op, arrayOf()); val m = call(mA, op, arrayOf())
                withClue("$uA@$sA $op  real=${r.exceptionOrNull()?.javaClass?.simpleName ?: r.getOrNull()}  model=${m.exceptionOrNull()?.javaClass?.simpleName ?: m.getOrNull()}") {
                    m.exceptionOrNull()?.javaClass?.name?.removePrefix("bmcref.") shouldBe r.exceptionOrNull()?.javaClass?.name
                    if (r.isSuccess && m.isSuccess) m.getOrThrow() shouldBe r.getOrThrow()
                }
            }
        }
    }

    // toBigIntegerExact: exact integer value, throwing ArithmeticException on a nonzero fractional part.
    test("toBigIntegerExact conforms (exact-or-throw)") {
        val u = Arb.long(-100_000L..100_000L)
        val s = Arb.int(0..4)
        checkAll(u, s) { uA, sA ->
            val rA = RealBD.valueOf(uA, sA)
            val mA = ModelBD.valueOf(uA, sA)
            val r = runCatching { rA.toBigIntegerExact().toLong() }
            val m = runCatching { mA.toBigIntegerExact().longValueExact() }
            withClue("$uA@$sA toBigIntegerExact  real=${r.exceptionOrNull()?.javaClass?.simpleName ?: r.getOrNull()}  model=${m.exceptionOrNull()?.javaClass?.simpleName ?: m.getOrNull()}") {
                m.exceptionOrNull()?.javaClass?.name?.removePrefix("bmcref.") shouldBe r.exceptionOrNull()?.javaClass?.name
                if (r.isSuccess && m.isSuccess) m.getOrThrow() shouldBe r.getOrThrow()
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

    // --- OUT-OF-DOMAIN: String-ctor digit accumulation past the long bound ------------------------
    // Bounded-model loud-failure, NOT JDK parity: an over-long numeral overflows the unscaled `long`.
    // The JDK is arbitrary-precision and SUCCEEDS; the model must FAIL LOUDLY (the digit-accumulation
    // overflow guard trips) rather than silently wrap to a wrong unscaled value. Previously the parse
    // loop did `u = u*10 + d` unchecked and wrapped silently. Assertions are on under Gradle's test
    // task, so the guard surfaces as AssertionError here exactly as it surfaces as a CBMC assert
    // under the engine.
    test("BigDecimal(String) with an over-long numeral fails LOUDLY (bounded-model loud-failure)") {
        // 25 nines: far beyond Long.MAX_VALUE (~9.2e18, 19 digits) -> unscaled long would wrap.
        val tooBig = "9".repeat(25)
        val real = runCatching { RealBD(tooBig) }   // arbitrary precision -> succeeds
        val model = runCatching { ModelBD(tooBig) }
        withClue("real success=${real.isSuccess}, model=${model.exceptionOrNull()?.javaClass}") {
            real.isSuccess shouldBe true
            model.isFailure shouldBe true            // loud past its bound, never a silent wrap
        }
    }
})
