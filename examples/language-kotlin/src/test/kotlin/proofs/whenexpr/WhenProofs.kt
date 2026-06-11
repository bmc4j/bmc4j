package proofs.whenexpr

import example.whenexpr.Circle
import example.whenexpr.Rect
import example.whenexpr.Shape
import example.whenexpr.Square
import example.whenexpr.Suit
import example.whenexpr.Suits
import example.whenexpr.area
import example.whenexpr.grade
import example.whenexpr.statusCode
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Kotlin `when` across all its subject forms — enum, a sealed hierarchy (`is` + properties), String,
 * Int ranges, and the subjectless boolean form. All are sound under BMC.
 */
class WhenProofs {

    // enum `when` (compiles to $WhenMappings + tableswitch).
    @BmcProof(unwind = 8)
    fun enum_rank_is_bounded() {
        val s = Bmc.anyOf(Suit.values())
        Bmc.check(Suits.rank(s) in 1..4)
    }

    // sealed `when` (exhaustive, no else): area is non-negative for any variant with non-negative
    // dimensions — proven across all three Shape cases at once.
    @BmcProof
    fun sealed_area_is_nonneg() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        val shape: Shape = when (Bmc.anyInt(0, 2)) {
            0 -> Circle(a)
            1 -> Square(a)
            else -> Rect(a, b)
        }
        Bmc.check(area(shape) >= 0)
    }

    // sealed `when` reads the matched variant's properties correctly.
    @BmcProof
    fun sealed_reads_properties() {
        Bmc.check(area(Rect(4, 5)) == 20 && area(Square(3)) == 9)
    }

    // string `when` — over every choice, lands in a known branch.
    @BmcProof
    fun string_when_is_sound() {
        val s = Bmc.anyOf(arrayOf("ok", "missing"))
        Bmc.check(statusCode(s) == 200 || statusCode(s) == 404)
    }

    // subjectless `when` (boolean conditions) matches the spec for every input.
    @BmcProof
    fun subjectless_when_signum() {
        val x = Bmc.anyInt()
        val sign = when {
            x > 0 -> 1
            x < 0 -> -1
            else -> 0
        }
        Bmc.check(if (x > 0) sign == 1 else if (x < 0) sign == -1 else sign == 0)
    }

    // FAIL: the range `when` in grade() has a gap — score 79 is covered by no branch and falls to
    // 'F'. BMC finds the counterexample score = 79.
    // Expected verdict: REFUTED - the seeded gap in the when leaves one score ungraded.
    @BmcProof(expect = Verdict.REFUTED)
    fun every_valid_score_is_graded() {
        val score = Bmc.anyInt(0, 100)
        Bmc.check(grade(score) != 'F')
    }
}
