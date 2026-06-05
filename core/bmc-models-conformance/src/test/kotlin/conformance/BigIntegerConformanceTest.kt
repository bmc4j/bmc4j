package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
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
            cmp("negate", { rx.negate().toLong() }, { mx.negate().toLong() })
            cmp("abs", { rx.abs().toLong() }, { mx.abs().toLong() })

            // mod with a guaranteed-positive modulus (real mod requires m > 0).
            val pm = Math.abs(y) + 1
            cmp("mod", { rx.mod(java.math.BigInteger.valueOf(pm)).toLong() },
                { mx.mod(bmcref.java.math.BigInteger.valueOf(pm)).toLong() })

            rx.signum() shouldBe mx.signum()
            rx.compareTo(ry) shouldBe mx.compareTo(my)
            rx.equals(ry) shouldBe mx.equals(my)
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
