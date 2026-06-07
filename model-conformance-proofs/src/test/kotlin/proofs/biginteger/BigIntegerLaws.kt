package proofs.biginteger

import java.math.BigInteger
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

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

    @BmcProof
    fun divide_multiply_remainder_reconstructs_dividend() {
        // Euclidean identity a == (a/b)*b + (a%b). Tight range keeps the divider circuit small
        // (the setScale lesson); the law holds for every value in range, so it stays a real proof.
        val a = BigInteger.valueOf(Bmc.anyInt(-1000, 1000).toLong())
        val b = BigInteger.valueOf(Bmc.anyInt(1, 100).toLong())   // positive, non-zero divisor
        Bmc.check(a.divide(b).multiply(b).add(a.remainder(b)) == a)
    }
}
