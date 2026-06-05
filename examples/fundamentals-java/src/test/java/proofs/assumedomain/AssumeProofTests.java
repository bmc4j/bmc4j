package proofs.assumedomain;

import example.assumedomain.Items;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

class AssumeProofTests {

    /** PASSES: a full bounds assumption makes the access provably safe. */
    @BmcProof
    void access_within_full_bounds_is_safe() {
        int[] a = {10, 20, 30};
        int i = Bmc.anyInt();
        Bmc.assume(i >= 0 && i < a.length);
        Items.elementAt(a, i);
    }

    /** FAILS: dropping the upper bound lets i == length (and beyond) through. */
    // Expected verdict: REFUTED - the missing upper bound admits an out-of-range index.
    @BmcProof(expect = Verdict.REFUTED)
    void access_with_only_a_lower_bound_can_throw() {
        int[] a = {10, 20, 30};
        int i = Bmc.anyInt();
        Bmc.assume(i >= 0);              // forgot: i < a.length
        Items.elementAt(a, i);
    }
}
