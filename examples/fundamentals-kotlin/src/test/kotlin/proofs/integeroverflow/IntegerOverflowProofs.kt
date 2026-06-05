package proofs.integeroverflow

import example.integeroverflow.Numbers
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

class IntegerOverflowProofs {

    /** FAILS: `abs(Int.MIN_VALUE)` overflows back to a negative number. */
    // Expected verdict: REFUTED - abs(Int.MIN_VALUE) is negative.
    @BmcProof(expect = Verdict.REFUTED)
    fun abs_is_never_negative() {
        val x = Bmc.anyInt()
        Bmc.check(Numbers.abs(x) >= 0)
    }

    /** PASSES: max really is >= both arguments, for all inputs. */
    @BmcProof
    fun max_is_at_least_both_arguments() {
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val m = Numbers.max(a, b)
        Bmc.check(m >= a && m >= b)
    }
}
