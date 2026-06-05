package proofs.assumedomain

import example.assumedomain.Items
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

class AssumeProofs {

    /** PASSES: a full bounds assumption makes the access provably safe. */
    @BmcProof
    fun access_within_full_bounds_is_safe() {
        val a = intArrayOf(10, 20, 30)
        val i = Bmc.anyInt()
        Bmc.assume(i >= 0 && i < a.size)
        Items.elementAt(a, i)
    }

    /** FAILS: dropping the upper bound lets `i == size` (and beyond) through. */
    // Expected verdict: REFUTED - the missing upper bound admits an out-of-range index.
    @BmcProof(expect = Verdict.REFUTED)
    fun access_with_only_a_lower_bound_can_throw() {
        val a = intArrayOf(10, 20, 30)
        val i = Bmc.anyInt()
        Bmc.assume(i >= 0)              // forgot: i < a.size
        Items.elementAt(a, i)
    }
}
