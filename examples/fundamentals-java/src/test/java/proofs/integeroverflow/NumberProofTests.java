package proofs.integeroverflow;

import example.integeroverflow.Numbers;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

class NumberProofTests {

    /** FAILS: abs(Integer.MIN_VALUE) overflows back to a negative number. */
    // Expected verdict: REFUTED - Math.abs(Integer.MIN_VALUE) is negative.
    @BmcProof(expect = Verdict.REFUTED)
    void abs_is_never_negative() {
        int x = Bmc.anyInt();
        Bmc.check(Numbers.abs(x) >= 0);
    }

    /** PASSES: max really is >= both arguments, for all inputs. */
    @BmcProof
    void max_is_at_least_both_arguments() {
        int a = Bmc.anyInt();
        int b = Bmc.anyInt();
        int m = Numbers.max(a, b);
        Bmc.check(m >= a && m >= b);
    }
}
