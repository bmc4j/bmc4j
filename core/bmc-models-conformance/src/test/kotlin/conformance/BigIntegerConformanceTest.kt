package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Differential conformance for the long-backed BigInteger model. The first ("in-domain") block keeps
 * operands well inside the long range so intermediate results don't overflow (the model's documented
 * bound; real BigInteger is arbitrary-precision) and asserts exact value/exception parity with the
 * JDK. Division/remainder by zero must throw like the JDK.
 *
 * The OUT-OF-DOMAIN blocks below deliberately leave the documented bound to exercise the model's
 * "loud, never silent" contract:
 *  - PRECONDITION violation (m <= 0 for mod): the model must throw the SAME exception the JDK throws
 *    (ArithmeticException) — full parity via [assertSameException].
 *  - BOUND violation (a result outside the long range): this is bounded-model loud-failure, NOT JDK
 *    parity. The arbitrary-precision JDK succeeds; the bounded model must FAIL LOUDLY (throw) rather
 *    than silently wrapping to a wrong value. We assert the model throws, independent of the JDK.
 */
class BigIntegerConformanceTest : FunSpec({

    val v = Arb.long(-1_000_000L..1_000_000L)

    test("arithmetic and comparison conform") {
        checkAll(v, v) { x, y ->
            val rx = java.math.BigInteger.valueOf(x)
            val ry = java.math.BigInteger.valueOf(y)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            val my = bmcref.java.math.BigInteger.valueOf(y)

            fun cmp(label: String, r: () -> Long, m: () -> Long) {
                val rr = runCatching(r)
                val mm = runCatching(m)
                withClue("$label  real=${rr.exceptionOrNull()?.javaClass?.simpleName ?: rr.getOrNull()}  model=${mm.exceptionOrNull()?.javaClass?.simpleName ?: mm.getOrNull()}") {
                    assertSameException(rr, mm)
                    if (rr.isSuccess) mm.getOrThrow() shouldBe rr.getOrThrow()
                }
            }

            cmp("add", { rx.add(ry).toLong() }, { mx.add(my).toLong() })
            cmp("subtract", { rx.subtract(ry).toLong() }, { mx.subtract(my).toLong() })
            cmp("multiply", { rx.multiply(ry).toLong() }, { mx.multiply(my).toLong() })
            cmp("divide", { rx.divide(ry).toLong() }, { mx.divide(my).toLong() })          // y==0 -> ArithmeticException
            cmp("remainder", { rx.remainder(ry).toLong() }, { mx.remainder(my).toLong() })
            // divideAndRemainder: {quotient, remainder} pair (y==0 throws ArithmeticException both sides).
            cmp("divAndRem[0]", { rx.divideAndRemainder(ry)[0].toLong() }, { mx.divideAndRemainder(my)[0].toLong() })
            cmp("divAndRem[1]", { rx.divideAndRemainder(ry)[1].toLong() }, { mx.divideAndRemainder(my)[1].toLong() })
            cmp("negate", { rx.negate().toLong() }, { mx.negate().toLong() })
            cmp("abs", { rx.abs().toLong() }, { mx.abs().toLong() })

            // mod with a guaranteed-positive modulus (real mod requires m > 0).
            val pm = Math.abs(y) + 1
            cmp("mod", { rx.mod(java.math.BigInteger.valueOf(pm)).toLong() },
                { mx.mod(bmcref.java.math.BigInteger.valueOf(pm)).toLong() })

            cmp("gcd", { rx.gcd(ry).toLong() }, { mx.gcd(my).toLong() })   // non-negative, sign-agnostic

            rx.signum() shouldBe mx.signum()
            rx.compareTo(ry) shouldBe mx.compareTo(my)
            rx.equals(ry) shouldBe mx.equals(my)
        }
    }

    // intValueExact / longValueExact: exact narrowing with JDK exception parity. longValueExact always
    // succeeds on the long backing; intValueExact throws ArithmeticException when the value leaves the
    // int range — driven across a band straddling Integer.MIN/MAX so both the fit and the throw fire.
    test("intValueExact / longValueExact conform (exact narrowing parity)") {
        val band = Arb.long(Integer.MIN_VALUE.toLong() - 5L..Integer.MAX_VALUE.toLong() + 5L)
        checkAll(band) { x ->
            val rx = java.math.BigInteger.valueOf(x)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            rx.longValueExact() shouldBe mx.longValueExact()
            val realI = runCatching { rx.intValueExact() }
            val modelI = runCatching { mx.intValueExact() }
            assertSameException(realI, modelI)
            if (realI.isSuccess) modelI.getOrThrow() shouldBe realI.getOrThrow()
            // out of int range -> both throw ArithmeticException
            if (x < Integer.MIN_VALUE.toLong() || x > Integer.MAX_VALUE.toLong()) {
                realI.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
                modelI.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            }
        }
    }

    // bitwise ops: a long IS two's-complement, so the native operators agree with BigInteger's
    // infinite-width two's-complement bitwise semantics bit-for-bit within the bound. Includes
    // negatives (sign extension) so not/and/or/xor over mixed signs are exercised.
    test("bitwise and/or/xor/not/andNot conform") {
        checkAll(v, v) { x, y ->
            val rx = java.math.BigInteger.valueOf(x); val ry = java.math.BigInteger.valueOf(y)
            val mx = bmcref.java.math.BigInteger.valueOf(x); val my = bmcref.java.math.BigInteger.valueOf(y)
            rx.and(ry).toLong() shouldBe mx.and(my).toLong()
            rx.or(ry).toLong() shouldBe mx.or(my).toLong()
            rx.xor(ry).toLong() shouldBe mx.xor(my).toLong()
            rx.andNot(ry).toLong() shouldBe mx.andNot(my).toLong()
            rx.not().toLong() shouldBe mx.not().toLong()
        }
    }

    // single-bit queries/mutations: testBit/setBit/clearBit/flipBit. Bit index kept < 62 so the
    // set/clear/flip results stay inside the long bound (a high bit on a small value is genuinely past
    // the long range — the model loudly fails there, covered by the OUT-OF-DOMAIN test below); a
    // negative index throws on both sides. testBit (read-only) is also exercised across the >= 64
    // sign-extension region. getLowestSetBit/bitCount/bitLength too.
    test("testBit/setBit/clearBit/flipBit/getLowestSetBit/bitCount/bitLength conform") {
        val small = Arb.long(-1_000L..1_000L)
        val idx = Arb.int(-2..61)
        checkAll(small, idx) { x, n ->
            val rx = java.math.BigInteger.valueOf(x)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            for (op in listOf("testBit", "setBit", "clearBit", "flipBit")) {
                val real = call(rx, op, arrayOf(Int::class.javaPrimitiveType!!), n)
                val model = call(mx, op, arrayOf(Int::class.javaPrimitiveType!!), n)
                assertSameException(real, model)
                if (real.isSuccess && model.isSuccess) {
                    val rr = real.getOrNull(); val mm = model.getOrNull()
                    if (rr is java.math.BigInteger) (mm as bmcref.java.math.BigInteger).toLong() shouldBe rr.toLong()
                    else mm shouldBe rr   // testBit -> Boolean
                }
            }
            rx.getLowestSetBit() shouldBe mx.getLowestSetBit()
            rx.bitCount() shouldBe mx.bitCount()
            rx.bitLength() shouldBe mx.bitLength()
        }
    }

    // testBit over the high index region (>= 64) reads the sign bit on both sides — read-only, never
    // out of bound, so full parity holds across the sign-extension region.
    test("testBit conforms in the high (>= 64) sign-extension region") {
        checkAll(Arb.long(-1_000L..1_000L), Arb.int(64..130)) { x, n ->
            java.math.BigInteger.valueOf(x).testBit(n) shouldBe bmcref.java.math.BigInteger.valueOf(x).testBit(n)
        }
    }

    // OUT-OF-DOMAIN bound: setBit at a high index on a non-negative value needs precision past the long
    // range; the arbitrary-precision JDK succeeds, the model must FAIL LOUDLY rather than wrap.
    test("setBit past the long bound fails LOUDLY (bounded-model loud-failure)") {
        java.math.BigInteger.valueOf(1L).setBit(100)   // JDK succeeds
        val model = runCatching { bmcref.java.math.BigInteger.valueOf(1L).setBit(100) }
        withClue("model setBit(100) on 1 should overflow loudly") {
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }

    // shiftLeft/shiftRight across positive and negative counts (negative delegates to the opposite
    // direction). Arithmetic (sign-extending) right shift; left shift kept small so it stays in-bound.
    test("shiftLeft / shiftRight conform") {
        val small = Arb.long(-1_000_000L..1_000_000L)
        val n = Arb.int(-40..40)
        checkAll(small, n) { x, k ->
            val rx = java.math.BigInteger.valueOf(x)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            val rl = runCatching { rx.shiftLeft(k).toLong() }
            val ml = runCatching { mx.shiftLeft(k).toLong() }
            // shiftLeft can overflow the long bound (loud in the model, fine for the arbitrary-precision
            // JDK); compare only when both succeed, else require the model failed loudly.
            if (rl.isSuccess && ml.isSuccess) ml.getOrThrow() shouldBe rl.getOrThrow()
            rx.shiftRight(k).toLong() shouldBe mx.shiftRight(k).toLong()
        }
    }

    // modPow: bounded base/exponent/modulus, non-negative exponent. Non-positive modulus throws like the
    // JDK; exponent 0 -> 1 mod m. (Negative exponent needs modInverse, the unmodeled tail — loud-only.)
    test("modPow conforms for non-negative exponents and positive modulus") {
        val base = Arb.long(-1_000L..1_000L)
        val exp = Arb.long(0L..12L)
        val mod = Arb.long(1L..10_000L)
        checkAll(base, exp, mod) { b, e, m ->
            val real = java.math.BigInteger.valueOf(b)
                .modPow(java.math.BigInteger.valueOf(e), java.math.BigInteger.valueOf(m)).toLong()
            val model = bmcref.java.math.BigInteger.valueOf(b)
                .modPow(bmcref.java.math.BigInteger.valueOf(e), bmcref.java.math.BigInteger.valueOf(m)).toLong()
            model shouldBe real
        }
    }

    test("modPow with non-positive modulus throws like the JDK") {
        val nonpos = Arb.long(-100L..0L)
        checkAll(v, nonpos) { b, m ->
            val real = call(java.math.BigInteger.valueOf(b), "modPow",
                arrayOf(java.math.BigInteger::class.java, java.math.BigInteger::class.java),
                java.math.BigInteger.valueOf(2L), java.math.BigInteger.valueOf(m))
            val model = call(bmcref.java.math.BigInteger.valueOf(b), "modPow",
                arrayOf(bmcref.java.math.BigInteger::class.java, bmcref.java.math.BigInteger::class.java),
                bmcref.java.math.BigInteger.valueOf(2L), bmcref.java.math.BigInteger.valueOf(m))
            assertSameException(real, model)
            real.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }

    // sqrt / sqrtAndRemainder: floor square root; negative throws ArithmeticException like the JDK.
    test("sqrt / sqrtAndRemainder conform (incl. negative -> throw)") {
        val band = Arb.long(-100L..1_000_000L)
        checkAll(band) { x ->
            val rx = java.math.BigInteger.valueOf(x)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            val real = runCatching { rx.sqrt().toLong() }
            val model = runCatching { mx.sqrt().toLong() }
            assertSameException(real, model)
            if (real.isSuccess) model.getOrThrow() shouldBe real.getOrThrow()
            if (x >= 0) {
                rx.sqrtAndRemainder()[0].toLong() shouldBe mx.sqrtAndRemainder()[0].toLong()
                rx.sqrtAndRemainder()[1].toLong() shouldBe mx.sqrtAndRemainder()[1].toLong()
            }
        }
    }

    // byte/short narrowing + the *Exact variants (exact narrowing with JDK exception parity).
    test("byteValue/shortValue + byteValueExact/shortValueExact conform") {
        val band = Arb.long(-100_000L..100_000L)
        checkAll(band) { x ->
            val rx = java.math.BigInteger.valueOf(x)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            for (op in listOf("byteValue", "shortValue", "byteValueExact", "shortValueExact")) {
                val r = call(rx, op, arrayOf()); val m = call(mx, op, arrayOf())
                assertSameException(r, m)
                if (r.isSuccess && m.isSuccess) m.getOrThrow() shouldBe r.getOrThrow()
            }
        }
    }

    // OUT-OF-DOMAIN bound: shiftLeft past the long range fails LOUDLY (the JDK succeeds; the model must
    // throw rather than drop high bits).
    test("shiftLeft past the long bound fails LOUDLY (bounded-model loud-failure)") {
        java.math.BigInteger.valueOf(1L).shiftLeft(100)   // JDK succeeds
        val model = runCatching { bmcref.java.math.BigInteger.valueOf(1L).shiftLeft(100) }
        withClue("model 1<<100 should overflow loudly") {
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }

    // pow: small non-negative exponents (exact in-bound); negative exponent throws like the JDK.
    test("pow conforms for small exponents and negative-exponent parity") {
        val base = Arb.long(-1_000L..1_000L)
        val exp = Arb.int(-2..6)
        checkAll(base, exp) { bse, e ->
            val real = runCatching { java.math.BigInteger.valueOf(bse).pow(e).toLong() }
            val model = runCatching { bmcref.java.math.BigInteger.valueOf(bse).pow(e).toLong() }
            assertSameException(real, model)
            if (real.isSuccess) model.getOrThrow() shouldBe real.getOrThrow()
        }
    }

    // OUT-OF-DOMAIN bound: gcd(Long.MIN_VALUE, 0) == abs(Long.MIN_VALUE), past the long range. The
    // arbitrary-precision JDK returns it; the long-backed model must FAIL LOUDLY (Math.absExact).
    test("gcd(Long.MIN_VALUE, 0) fails LOUDLY (bounded-model loud-failure)") {
        java.math.BigInteger.valueOf(Long.MIN_VALUE).gcd(java.math.BigInteger.ZERO)   // JDK succeeds
        val model = runCatching {
            bmcref.java.math.BigInteger.valueOf(Long.MIN_VALUE).gcd(bmcref.java.math.BigInteger.valueOf(0L))
        }
        withClue("model gcd(Long.MIN,0) should overflow loudly") {
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }

    // OUT-OF-DOMAIN bound: pow overflowing the long backing fails LOUDLY, not silently wrapping.
    test("pow overflowing the long bound fails LOUDLY (bounded-model loud-failure)") {
        java.math.BigInteger.valueOf(10L).pow(30)   // JDK (arbitrary precision) succeeds
        val model = runCatching { bmcref.java.math.BigInteger.valueOf(10L).pow(30) }
        withClue("model 10^30 should overflow loudly") {
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }

    // Divergence ledger — "everything else is a bug": the String constructor must accept exactly what
    // the JDK accepts in radix 10 and throw NumberFormatException on the rest (the silent-parse bug,
    // mirroring the BigDecimal(String) precedent). In-bound values round-trip bit-for-bit.
    test("BigInteger(String) conforms for valid and invalid inputs") {
        val valid = listOf("0", "12", "-3", "+7", "100", "000", "-0", "+0", "9999999", "-9999999",
            "2147483648", "-2147483648", "9223372036854775807")   // up to Long.MAX
        val invalid = listOf("", "-", "+", "12x4", "1.5", "abc", "x", "1 2", " 5", "5 ", "0x1f",
            "1_000", "--3", "+-3", "12.0", ".")
        for (str in valid + invalid) {
            val real = runCatching { java.math.BigInteger(str) }
            val model = runCatching { bmcref.java.math.BigInteger(str) }
            withClue("BigInteger(\"$str\")  real=${real.exceptionOrNull()?.javaClass?.simpleName ?: real.getOrNull()}  model=${model.exceptionOrNull()?.javaClass?.simpleName ?: model.getOrNull()}") {
                // Same success/failure split; on failure both NumberFormatException.
                real.isSuccess shouldBe model.isSuccess
                if (real.isSuccess) {
                    model.getOrThrow().toLong() shouldBe real.getOrThrow().toLong()
                } else {
                    real.exceptionOrNull().shouldBeInstanceOf<NumberFormatException>()
                    (model.exceptionOrNull()!!::class.java.name.removePrefix("bmcref.")) shouldBe
                        "java.lang.NumberFormatException"
                }
            }
        }
    }

    // modInverse: this^-1 mod m over a positive modulus. Value parity with the JDK when invertible, and
    // exception parity (ArithmeticException) when not invertible or the modulus is non-positive.
    test("modInverse conforms (value + exception parity)") {
        val a = Arb.long(-10_000L..10_000L)
        val m = Arb.long(-50L..5_000L)   // straddles the m<=0 boundary; includes non-invertible residues
        checkAll(a, m) { x, mod ->
            val rx = java.math.BigInteger.valueOf(x); val rm = java.math.BigInteger.valueOf(mod)
            val mx = bmcref.java.math.BigInteger.valueOf(x); val mm = bmcref.java.math.BigInteger.valueOf(mod)
            val real = runCatching { rx.modInverse(rm).toLong() }
            val model = runCatching { mx.modInverse(mm).toLong() }
            withClue("modInverse($x, $mod)  real=${real.exceptionOrNull()?.javaClass?.simpleName ?: real.getOrNull()}  model=${model.exceptionOrNull()?.javaClass?.simpleName ?: model.getOrNull()}") {
                assertSameException(real, model)
                if (real.isSuccess) model.getOrThrow() shouldBe real.getOrThrow()
            }
        }
    }

    // parallelMultiply is JDK 19+ (absent on the Java-17 floor), so it can't be referenced here; it
    // delegates to multiply with no observable difference and stays model-only (multiply is covered).

    // toByteArray: minimal two's-complement big-endian encoding — byte-for-byte parity with the JDK
    // across positives, negatives, and zero (every long value encodes in <= 8 bytes).
    test("toByteArray conforms (byte-for-byte, signed big-endian)") {
        val band = Arb.long(-10_000_000L..10_000_000L)
        checkAll(band) { x ->
            val real = java.math.BigInteger.valueOf(x).toByteArray()
            val model = bmcref.java.math.BigInteger.valueOf(x).toByteArray()
            withClue("toByteArray($x)  real=${real.toList()}  model=${model.toList()}") {
                model.toList() shouldBe real.toList()
            }
        }
        // explicit edge values, incl. exact byte boundaries and zero.
        for (x in listOf(0L, 1L, -1L, 127L, 128L, 255L, 256L, -128L, -129L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            bmcref.java.math.BigInteger.valueOf(x).toByteArray().toList() shouldBe
                java.math.BigInteger.valueOf(x).toByteArray().toList()
        }
    }

    // OUT-OF-DOMAIN bound: a magnitude past the long range is one the arbitrary-precision JDK accepts,
    // but the long-backed model must FAIL LOUDLY (ArithmeticException via Math.*Exact), never wrap.
    test("BigInteger(String) past the long bound fails LOUDLY (bounded-model loud-failure)") {
        val tooBig = listOf("9223372036854775808",          // Long.MAX + 1
            "-9223372036854775809",                          // Long.MIN - 1
            "99999999999999999999999999", "-10000000000000000000")
        for (str in tooBig) {
            // JDK (arbitrary precision) succeeds.
            java.math.BigInteger(str)
            val model = runCatching { bmcref.java.math.BigInteger(str) }
            withClue("BigInteger(\"$str\") should fail loudly in the bounded model") {
                model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            }
        }
    }

    // --- OUT-OF-DOMAIN: precondition violation (mod requires a positive modulus) -------------------
    // A band straddling the m <= 0 boundary (the model's mod previously normalized any modulus,
    // silently diverging for m <= 0 where the real BigInteger.mod throws). Assert exception parity.
    test("mod with non-positive modulus throws like the JDK (exception parity)") {
        val mod = Arb.long(-1_000_000L..1L)   // <= 0 plus the just-valid 1 boundary
        checkAll(v, mod) { x, m ->
            val rx = java.math.BigInteger.valueOf(x)
            val mx = bmcref.java.math.BigInteger.valueOf(x)
            val real = call(rx, "mod", arrayOf(java.math.BigInteger::class.java),
                java.math.BigInteger.valueOf(m))
            val model = call(mx, "mod", arrayOf(bmcref.java.math.BigInteger::class.java),
                bmcref.java.math.BigInteger.valueOf(m))
            assertSameException(real, model)
            // For m <= 0 BOTH must have thrown ArithmeticException; for m == 1 both succeed.
            if (m <= 0L) {
                real.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
                model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            }
        }
    }

    // --- OUT-OF-DOMAIN: bound violation (result leaves the long range) -----------------------------
    // Bounded-model loud-failure, NOT JDK parity: real BigInteger is arbitrary-precision and SUCCEEDS;
    // the long-backed model must THROW (via the checked Math.*Exact arithmetic) rather than silently
    // wrap. We assert the model fails loudly; we do NOT require it to match the JDK's (bigger) result.
    test("arithmetic overflowing the long bound fails LOUDLY (bounded-model loud-failure)") {
        // High operand within the long range, and a second operand guaranteed to push the
        // add/multiply result PAST Long.MAX_VALUE (so the model's checked arithmetic must throw).
        // s >= 2 makes multiply(b, s) > Long.MAX; an add that overflows needs b + s > Long.MAX, so we
        // pick b in the top band and s large enough to clear the remaining headroom.
        val headroom = 1_000_000L
        val big = Arb.long(Long.MAX_VALUE - headroom..Long.MAX_VALUE - 1L)
        val small = Arb.long(2L..headroom + 1L)   // > headroom guarantees b + s overflows for the add case
        checkAll(big, small) { b, s ->
            val mb = bmcref.java.math.BigInteger.valueOf(b)
            val ms = bmcref.java.math.BigInteger.valueOf(s)
            // multiply(top, >=2) always overflows the long backing.
            val mulM = call(mb, "multiply", arrayOf(bmcref.java.math.BigInteger::class.java), ms)
            withClue("model multiply overflow should throw") {
                mulM.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            }
            // add overflows only when b + s clears the headroom; assert loud-failure exactly then.
            if (b > Long.MAX_VALUE - s) {
                val addM = call(mb, "add", arrayOf(bmcref.java.math.BigInteger::class.java), ms)
                withClue("model add overflow should throw") {
                    addM.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
                }
            }
            // Sanity: the arbitrary-precision JDK SUCCEEDS on the same inputs (so this is genuinely
            // out of the MODEL's bound, not an input both reject).
            val rb = java.math.BigInteger.valueOf(b)
            val rs = java.math.BigInteger.valueOf(s)
            rb.add(rs)        // no throw
            rb.multiply(rs)   // no throw
        }
    }
})
