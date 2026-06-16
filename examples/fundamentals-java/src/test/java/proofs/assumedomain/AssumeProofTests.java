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

    // anyLong(min, max) / anyInt(min, max) emit their range as two SEPARATE atomic assumes (not one
    // `lo <= v && v <= hi`) so JBMC prunes downstream dead branches off each bound. These proofs pin
    // that BOTH ends still constrain the value, and the refuting pair shows each bound is load-bearing
    // (neither is vacuously dropped by the split).

    /** PASSES: both ends of anyLong's range still hold after the assume split. */
    @BmcProof
    void anyLong_range_constrains_both_ends() {
        long v = Bmc.anyLong(-5, 7);
        Bmc.check(v >= -5 && v <= 7);
    }

    /** FAILS: the upper bound is real - the split did not drop it. */
    // Expected verdict: REFUTED - v can be as large as 7, so v <= 6 is not provable.
    @BmcProof(expect = Verdict.REFUTED)
    void anyLong_upper_bound_is_load_bearing() {
        long v = Bmc.anyLong(-5, 7);
        Bmc.check(v <= 6);
    }

    /** FAILS: the lower bound is real - the split did not drop it. */
    // Expected verdict: REFUTED - v can be as small as -5, so v >= -4 is not provable.
    @BmcProof(expect = Verdict.REFUTED)
    void anyLong_lower_bound_is_load_bearing() {
        long v = Bmc.anyLong(-5, 7);
        Bmc.check(v >= -4);
    }

    /** PASSES: both ends of anyInt's range still hold after the assume split. */
    @BmcProof
    void anyInt_range_constrains_both_ends() {
        int v = Bmc.anyInt(3, 9);
        Bmc.check(v >= 3 && v <= 9);
    }
}
