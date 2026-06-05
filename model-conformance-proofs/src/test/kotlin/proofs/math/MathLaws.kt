package proofs.math

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the integer-valued [java.lang.Math] methods that JBMC's bundled
 * core-models.jar does NOT model — it stubs floorDiv/floorMod/the *Exact family/toIntExact/absExact
 * to a nondet result, so before the [org.bmc4j.engine.MathBytecode] redirect even
 * `Math.floorDiv(-7, 3) == -3` spuriously refuted. The redirect routes those call sites to the sound
 * `BmcMath`; these laws check the redirect under JBMC's own semantics.
 *
 * Each method gets (a) concrete known values a nondet stub fails on, and (b) SYMBOLIC algebraic laws
 * a nondet stub cannot satisfy (e.g. the floor division identity for all symbolic a and non-zero b).
 * All must pass.
 */
class MathLaws {

    // --- floorDiv / floorMod ----------------------------------------------------------------------

    @BmcProof
    fun floorDiv_concrete_negatives() {
        Bmc.check(Math.floorDiv(-7, 3) == -3)
        Bmc.check(Math.floorDiv(7, -3) == -3)
        Bmc.check(Math.floorDiv(-7, -3) == 2)
        Bmc.check(Math.floorDiv(7, 3) == 2)
    }

    @BmcProof
    fun floorMod_concrete_negatives() {
        Bmc.check(Math.floorMod(-7, 3) == 2)
        Bmc.check(Math.floorMod(7, -3) == -2)
        Bmc.check(Math.floorMod(-7, -3) == -1)
        Bmc.check(Math.floorMod(7, 3) == 1)
    }

    @BmcProof
    fun floorDiv_floorMod_division_identity_int() {
        // The defining law: floorDiv(a,b)*b + floorMod(a,b) == a, for every symbolic a and b != 0.
        // A nondet stub cannot satisfy this. Tight range keeps the multiply circuit small.
        val a = Bmc.anyInt(-200, 200)
        val b = Bmc.anyInt(-20, 20)
        Bmc.assume(b != 0)
        Bmc.check(Math.floorDiv(a, b) * b + Math.floorMod(a, b) == a)
    }

    @BmcProof
    fun floorMod_in_range_int() {
        // floorMod's result has the sign of the divisor and magnitude < |b| (symbolic law).
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(1, 100) // positive divisor -> 0 <= floorMod < b
        val r = Math.floorMod(a, b)
        Bmc.check(r in 0 until b)
    }

    @BmcProof
    fun floorDiv_floorMod_division_identity_long() {
        val a = Bmc.anyLong(-200L, 200L)
        val b = Bmc.anyLong(-20L, 20L)
        Bmc.assume(b != 0L)
        Bmc.check(Math.floorDiv(a, b) * b + Math.floorMod(a, b) == a)
    }

    // --- addExact / subtractExact / multiplyExact -------------------------------------------------

    @BmcProof
    fun addExact_equals_plain_in_range_int() {
        // When no overflow occurs, addExact equals + (symbolic law); the *Exact methods are
        // exact-or-loud, so in-range they must agree with ordinary arithmetic.
        val a = Bmc.anyInt(-1_000_000, 1_000_000)
        val b = Bmc.anyInt(-1_000_000, 1_000_000)
        Bmc.check(Math.addExact(a, b) == a + b)
    }

    @BmcProof
    fun subtractExact_equals_plain_in_range_int() {
        val a = Bmc.anyInt(-1_000_000, 1_000_000)
        val b = Bmc.anyInt(-1_000_000, 1_000_000)
        Bmc.check(Math.subtractExact(a, b) == a - b)
    }

    @BmcProof
    fun multiplyExact_equals_plain_in_range_int() {
        // Tight range: the overflow check divides, so a small divisor keeps that circuit small.
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        Bmc.check(Math.multiplyExact(a, b) == a * b)
    }

    @BmcProof
    fun addExact_equals_plain_in_range_long() {
        val a = Bmc.anyLong(-1_000_000L, 1_000_000L)
        val b = Bmc.anyLong(-1_000_000L, 1_000_000L)
        Bmc.check(Math.addExact(a, b) == a + b)
    }

    @BmcProof
    fun addExact_concrete() {
        Bmc.check(Math.addExact(2, 3) == 5)
        Bmc.check(Math.subtractExact(2, 3) == -1)
        Bmc.check(Math.multiplyExact(4, 5) == 20)
    }

    // --- negateExact / incrementExact / decrementExact --------------------------------------------

    @BmcProof
    fun negateExact_in_range_int() {
        val a = Bmc.anyInt(-1_000_000, 1_000_000)
        Bmc.check(Math.negateExact(a) == -a)
    }

    @BmcProof
    fun increment_decrement_exact_int() {
        val a = Bmc.anyInt(-1_000_000, 1_000_000)
        Bmc.check(Math.incrementExact(a) == a + 1)
        Bmc.check(Math.decrementExact(a) == a - 1)
        Bmc.check(Math.decrementExact(Math.incrementExact(a)) == a)
    }

    // --- toIntExact -------------------------------------------------------------------------------

    @BmcProof
    fun toIntExact_round_trips_in_range() {
        // For values that fit in an int, toIntExact is the identity-as-int (symbolic law). Range is
        // well inside int so no overflow trap fires; a nondet stub can't prove the equality.
        val v = Bmc.anyLong(-1_000_000L, 1_000_000L)
        Bmc.check(Math.toIntExact(v).toLong() == v)
    }

    @BmcProof
    fun toIntExact_concrete() {
        Bmc.check(Math.toIntExact(123456789L) == 123456789)
        Bmc.check(Math.toIntExact(-5L) == -5)
    }

    // --- abs / absExact ---------------------------------------------------------------------------

    @BmcProof
    fun abs_int_law() {
        val a = Bmc.anyInt(-1_000_000, 1_000_000)
        val r = Math.abs(a)
        Bmc.check(r >= 0)
        Bmc.check(r == a || r == -a)
    }

    @BmcProof
    fun absExact_int_in_range() {
        val a = Bmc.anyInt(-1_000_000, 1_000_000)
        Bmc.check(Math.absExact(a) == (if (a < 0) -a else a))
    }
}
