package proofs.patternswitch;

import example.patternswitch.EnumRouting;
import example.patternswitch.EnumRouting.Status;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Enum pattern switches ({@code SwitchBootstraps.enumSwitch} — what javac emits when a {@code
 * case null} arm or pattern labels are present) are desugared to a sound identity-comparison
 * chain, the same way {@code typeSwitch} is. So a symbolic enum subject gets REAL verdicts here:
 * the selected branch is provably tied to the subject's actual constant. (These proofs were
 * {@code expect = UNKNOWN} when enumSwitch was still a residual havoc'd indy — their flip to real
 * verdicts is the desugar working.)
 */
class EnumSwitchProofs {

    /** PASSES: for every status (null included), the routing code lands in the declared range. */
    @BmcProof
    void code_is_total_and_in_range(Status s) {
        int c = EnumRouting.code(s);
        Bmc.check(c >= -1 && c <= 2);
    }

    /** PASSES: the null arm routes to exactly -1, and only null routes there. */
    @BmcProof
    void null_routes_to_minus_one_and_nothing_else_does(Status s) {
        int c = EnumRouting.code(s);
        Bmc.check((s == null) == (c == -1));
    }

    /** PASSES: each constant routes to its own arm — the branch tracks the symbolic subject. */
    @BmcProof
    void each_constant_routes_to_its_own_arm(Status s) {
        Bmc.assume(s != null);
        int c = EnumRouting.code(s);
        Bmc.check((s == Status.OK) == (c == 0));
        Bmc.check((s == Status.RETRY) == (c == 1));
        Bmc.check((s == Status.FAIL) == (c == 2));
    }

    /**
     * FAILS (the bug-demo direction): claiming RETRY routes to 2 is false — BMC refutes it with
     * the RETRY counterexample, proving the arm selection is real, not a nondet that could
     * accidentally satisfy the claim.
     */
    // Expected verdict: REFUTED - RETRY routes to 1, not 2.
    @BmcProof(expect = Verdict.REFUTED)
    void misrouting_claim_is_refuted(Status s) {
        Bmc.assume(s == Status.RETRY);
        Bmc.check(EnumRouting.code(s) == 2);
    }
}
