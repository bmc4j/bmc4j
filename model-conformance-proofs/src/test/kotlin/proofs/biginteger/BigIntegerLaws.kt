package proofs.biginteger

import java.math.BigInteger
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Shard

/**
 * Model proofs (axis 2): algebraic laws the BigInteger model (bounded, long-backed) must
 * satisfy under JBMC, over symbolic inputs kept well inside the long bound. All must pass.
 */
class BigIntegerLaws {

    private fun any(): BigInteger = BigInteger.valueOf(Bmc.anyInt(-100_000, 100_000).toLong())

    @BmcProof
    fun add_is_commutative() {
        val a = any()
        val b = any()
        Bmc.check(a.add(b) == b.add(a))
    }

    @BmcProof
    fun add_zero_is_identity() {
        val a = any()
        Bmc.check(a.add(BigInteger.ZERO) == a)
    }

    @BmcProof
    fun multiply_one_is_identity() {
        val a = any()
        Bmc.check(a.multiply(BigInteger.ONE) == a)
    }

    @BmcProof
    fun subtract_self_is_zero() {
        val a = any()
        Bmc.check(a.subtract(a).signum() == 0)
    }

    @BmcProof
    fun negate_twice_round_trips() {
        val a = any()
        Bmc.check(a.negate().negate() == a)
    }

    // --- String constructor: parse round-trips (concrete to keep the parse loop SAT-light) ---------
    // The symbolic/garbage axis is covered differentially (BigIntegerConformanceTest); these pin the
    // parse semantics concretely under JBMC so the model's own parse path is verified, not stubbed.

    @BmcProof
    fun parse_round_trips_via_longValue() {
        Bmc.check(BigInteger("123").toLong() == 123L)
        Bmc.check(BigInteger("-456").toLong() == -456L)
        Bmc.check(BigInteger("+7").toLong() == 7L)
        Bmc.check(BigInteger("0").toLong() == 0L)
        Bmc.check(BigInteger("000").toLong() == 0L)
    }

    @BmcProof
    fun parse_agrees_with_valueOf() {
        Bmc.check(BigInteger("1000") == BigInteger.valueOf(1000L))
        Bmc.check(BigInteger("-1000") == BigInteger.valueOf(-1000L))
    }

    // --- gcd / pow: CONCRETE pins (the Euclid loop and pow's repeated-multiply loop are over a
    // symbolic count, which JBMC must unwind — a symbolic axis is loop-unbounded and SAT-pathological,
    // the division/loop lesson. The wide symbolic axis + sign-agnosticism + the loud overflow boundary
    // are covered differentially in BigIntegerConformanceTest; these pin the algebra concretely.) ----

    @BmcProof
    fun gcd_pins() {
        Bmc.check(BigInteger.valueOf(12).gcd(BigInteger.valueOf(18)) == BigInteger.valueOf(6))
        Bmc.check(BigInteger.valueOf(-12).gcd(BigInteger.valueOf(18)) == BigInteger.valueOf(6))  // sign-agnostic
        Bmc.check(BigInteger.valueOf(0).gcd(BigInteger.valueOf(5)) == BigInteger.valueOf(5))      // gcd(0,x)=|x|
        Bmc.check(BigInteger.valueOf(0).gcd(BigInteger.ZERO) == BigInteger.ZERO)                  // gcd(0,0)=0
        Bmc.check(BigInteger.valueOf(7).gcd(BigInteger.valueOf(7)) == BigInteger.valueOf(7))
    }

    @BmcProof
    fun pow_pins() {
        Bmc.check(BigInteger.valueOf(2).pow(10) == BigInteger.valueOf(1024))
        Bmc.check(BigInteger.valueOf(-3).pow(3) == BigInteger.valueOf(-27))
        Bmc.check(BigInteger.valueOf(5).pow(0) == BigInteger.ONE)   // x^0 == 1
        Bmc.check(BigInteger.ZERO.pow(0) == BigInteger.ONE)         // 0^0 == 1, per the JDK
    }

    @BmcProof
    fun divideAndRemainder_agrees_with_divide_and_remainder() {
        // {q, r} must equal {divide, remainder}. Concrete pins keep the symbolic divider off this
        // proof (the division-cost lesson); the symbolic reconstruction is the slow proof below + the
        // wide differential axis (BigIntegerConformanceTest).
        val a = BigInteger.valueOf(17L)
        val b = BigInteger.valueOf(5L)
        val qr = a.divideAndRemainder(b)
        Bmc.check(qr[0] == a.divide(b))
        Bmc.check(qr[1] == a.remainder(b))
        Bmc.check(qr[0] == BigInteger.valueOf(3L))
        Bmc.check(qr[1] == BigInteger.valueOf(2L))
        val nqr = BigInteger.valueOf(-17L).divideAndRemainder(b)
        Bmc.check(nqr[0] == BigInteger.valueOf(-3L))   // truncates toward zero
        Bmc.check(nqr[1] == BigInteger.valueOf(-2L))
    }

    @BmcProof
    fun valueExact_round_trips_via_valueOf() {
        // longValueExact always holds on the long backing; intValueExact holds within the int range.
        val a = BigInteger.valueOf(Bmc.anyInt(-100_000, 100_000).toLong())
        Bmc.check(BigInteger.valueOf(a.longValueExact()) == a)
        Bmc.check(a.intValueExact().toLong() == a.longValueExact())   // in-range values agree
    }

    // --- bitwise laws (symbolic; native long two's-complement ops are SAT-light, no divider) --------

    @BmcProof
    fun and_or_absorption() {
        val a = any()
        val b = any()
        Bmc.check(a.and(b).or(a) == a)   // a & b | a == a
        Bmc.check(a.or(b).and(a) == a)   // (a | b) & a == a
    }

    @BmcProof
    fun xor_self_is_zero_and_not_involutes() {
        val a = any()
        Bmc.check(a.xor(a).signum() == 0)        // a ^ a == 0
        Bmc.check(a.not().not() == a)            // ~~a == a
        Bmc.check(a.andNot(BigInteger.ZERO) == a) // a & ~0 == a
    }

    @BmcProof
    fun de_morgan() {
        val a = any()
        val b = any()
        Bmc.check(a.and(b).not() == a.not().or(b.not()))   // ~(a & b) == ~a | ~b
    }

    // setBit/clearBit/testBit pins (concrete bit indices keep the shift symbolic-count off the proof).
    @BmcProof
    fun bit_set_clear_test_pins() {
        val a = BigInteger.valueOf(0b1010L)
        Bmc.check(a.testBit(1))                      // bit 1 set
        Bmc.check(!a.testBit(0))                     // bit 0 clear
        Bmc.check(a.setBit(0) == BigInteger.valueOf(0b1011L))
        Bmc.check(a.clearBit(1) == BigInteger.valueOf(0b1000L))
        Bmc.check(a.flipBit(2) == BigInteger.valueOf(0b1110L))
        Bmc.check(BigInteger.valueOf(12L).getLowestSetBit() == 2)
        Bmc.check(BigInteger.ZERO.getLowestSetBit() == -1)
        Bmc.check(BigInteger.valueOf(7L).bitCount() == 3)
        Bmc.check(BigInteger.valueOf(255L).bitLength() == 8)
    }

    // shift round-trip: (a << k) >> k == a for a small fixed shift that stays in-bound (concrete count
    // keeps the shift loop unwound cheaply; the wide axis is differential).
    @BmcProof
    fun shift_left_then_right_round_trips() {
        val a = BigInteger.valueOf(Bmc.anyInt(-100_000, 100_000).toLong())
        Bmc.check(a.shiftLeft(3).shiftRight(3) == a)
    }

    // modPow / sqrt: concrete pins (the square-and-multiply loop + Newton iteration are over a symbolic
    // count → SAT-pathological if symbolic; the wide axis is differential). Pin the algebra here.
    @BmcProof
    fun modPow_pins() {
        Bmc.check(BigInteger.valueOf(2L).modPow(BigInteger.valueOf(10L), BigInteger.valueOf(1000L))
            == BigInteger.valueOf(24L))                                  // 1024 mod 1000
        Bmc.check(BigInteger.valueOf(3L).modPow(BigInteger.ZERO, BigInteger.valueOf(7L))
            == BigInteger.ONE)                                           // x^0 mod m == 1
        Bmc.check(BigInteger.valueOf(5L).modPow(BigInteger.valueOf(3L), BigInteger.ONE).signum() == 0) // mod 1 == 0
    }

    @BmcProof
    fun sqrt_pins() {
        Bmc.check(BigInteger.valueOf(16L).sqrt() == BigInteger.valueOf(4L))
        Bmc.check(BigInteger.valueOf(17L).sqrt() == BigInteger.valueOf(4L))   // floor
        Bmc.check(BigInteger.valueOf(99L).sqrt() == BigInteger.valueOf(9L))
        Bmc.check(BigInteger.ZERO.sqrt().signum() == 0)
        val sr = BigInteger.valueOf(17L).sqrtAndRemainder()
        Bmc.check(sr[0] == BigInteger.valueOf(4L) && sr[1] == BigInteger.ONE)   // 17 = 4*4 + 1
    }

    @BmcProof
    fun byteShortValueExact_round_trips() {
        val a = BigInteger.valueOf(Bmc.anyInt(-100, 100).toLong())   // fits byte and short
        Bmc.check(BigInteger.valueOf(a.byteValueExact().toLong()) == a)
        Bmc.check(BigInteger.valueOf(a.shortValueExact().toLong()) == a)
    }

    // modInverse: the defining round-trip a·a⁻¹ ≡ 1 (mod m). CONCRETE pins keep the extended-Euclid
    // loop (over a symbolic count) off the proof — the symbolic axis + non-invertible/non-positive
    // boundaries are covered differentially (BigIntegerConformanceTest). Pins the algebra under JBMC.
    @BmcProof
    fun modInverse_round_trips() {
        // 3·inv ≡ 1 (mod 11): inv == 4 (3*4 = 12 ≡ 1).
        val inv = BigInteger.valueOf(3L).modInverse(BigInteger.valueOf(11L))
        Bmc.check(inv == BigInteger.valueOf(4L))
        Bmc.check(BigInteger.valueOf(3L).multiply(inv).mod(BigInteger.valueOf(11L)) == BigInteger.ONE)
        // a negative residue is reduced into [0, m): -7 ≡ 4 (mod 11), inv(4) == 3.
        Bmc.check(BigInteger.valueOf(-7L).modInverse(BigInteger.valueOf(11L)) == BigInteger.valueOf(3L))
        // modInverse mod 1 is ZERO (single-residue ring), per the JDK.
        Bmc.check(BigInteger.valueOf(5L).modInverse(BigInteger.ONE).signum() == 0)
    }

    // toByteArray: minimal two's-complement big-endian encoding — concrete pins under JBMC (the array
    // build is over a symbolic length if value-symbolic; the wide byte-for-byte axis is differential).
    @BmcProof
    fun toByteArray_pins() {
        Bmc.check(BigInteger.ZERO.toByteArray().let { it.size == 1 && it[0].toInt() == 0 })       // {0}
        Bmc.check(BigInteger.valueOf(127L).toByteArray().let { it.size == 1 && it[0].toInt() == 127 })
        Bmc.check(BigInteger.valueOf(128L).toByteArray()
            .let { it.size == 2 && it[0].toInt() == 0 && (it[1].toInt() and 0xFF) == 128 })        // 0x0080
        Bmc.check(BigInteger.valueOf(-1L).toByteArray().let { it.size == 1 && it[0].toInt() == -1 }) // {0xFF}
    }

    // parallelMultiply delegates to multiply, so this is a full-width multiplier-EQUIVALENCE check —
    // SAT-pathological at the wide any() bound (two symbolic multipliers proven equal). A tight range
    // keeps the multiplier circuit small (the same lesson as the divider proofs); wide-value parity is
    // already on the differential axis (BigIntegerConformanceTest), so this stays a real proof of the
    // delegation. kissat is markedly faster than the built-in MiniSat on multiplier CNF (falls back to
    // the default solver if the bundled binary isn't present, which the tight range still discharges).
    @BmcProof
    fun parallelMultiply_equals_multiply() {
        val a = BigInteger.valueOf(Bmc.anyInt(-100, 100).toLong())
        val b = BigInteger.valueOf(Bmc.anyInt(-100, 100).toLong())
        Bmc.check(a.parallelMultiply(b) == a.multiply(b))
    }

    // ~80s, the module's heaviest BigInteger division proof — pinned to shard 3 (setScale → 1,
    // add_then_subtract → 2 in BigDecimalLaws), so the three slow model-conformance proofs spread out.
    @Shard(3)
    @BmcProof
    fun divide_multiply_remainder_reconstructs_dividend() {
        // Euclidean identity a == (a/b)*b + (a%b). Tight range keeps the divider circuit small
        // (the setScale lesson); the law holds for every value in range, so it stays a real proof.
        val a = BigInteger.valueOf(Bmc.anyInt(-1000, 1000).toLong())
        val b = BigInteger.valueOf(Bmc.anyInt(1, 100).toLong())   // positive, non-zero divisor
        Bmc.check(a.divide(b).multiply(b).add(a.remainder(b)) == a)
    }
}
