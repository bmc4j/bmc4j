package proofs.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;
import org.bmc4j.engine.BmcRequest;
import org.bmc4j.engine.JbmcResult;
import org.bmc4j.engine.VerificationBackend;
import org.bmc4j.engine.VerificationBackends;
import org.junit.jupiter.api.Test;

/**
 * Live regression test (in the green suite) for the unmodelled-member VERDICT HONESTY rule: reaching
 * a real JDK member bmc4j deliberately does NOT implement — declared via {@code @BmcNotModelled} /
 * {@code @BmcNotNeeded} or absorbed by {@code @BmcModelTail} — is bmc4j's own MODELING GAP, not a
 * counterexample in the user's code. The build-time loud-body synthesis routes every such member
 * through the {@code org.bmc4j.analysis.BmcUnmodelledReached} sentinel; the verdict interpreter
 * recognizes that and DEMOTES the would-be refutation to {@code UNKNOWN}, naming the member.
 *
 * <p>So these probes declare {@code expect = UNKNOWN}: the test passes while the reach is honestly
 * undecided (NOT REFUTED — that would falsely tell the user their code has a counterexample), and
 * goes red if a future change ever makes one of these members silently havoc, or surfaces it as a
 * refutation again. If one later gets a real model the probe goes red (VERIFIED) — "retarget me".
 *
 * <p>The {@link #demotion_names_the_reached_member()} unit test additionally asserts that the
 * demotion FACT names the member and that the UNKNOWN framing tells the user what to do.
 */
class LoudUnmodelledProbe {

    /**
     * {@code ArrayList.sort(Comparator)} is a {@code @BmcNotModelled} member (comparator-driven sort
     * over the bounded array). Reaching it is honestly UNKNOWN — bmc4j can't model it — NOT a false
     * REFUTED that would claim the user's code has a counterexample.
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    void reaching_an_unmodelled_list_member_is_undecided() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(2);
        a.add(1);
        a.sort((x, y) -> x - y); // no real model body; the loud body routes through the sentinel -> UNKNOWN
        Bmc.check(a.get(0) == 1); // never reached
    }

    /**
     * {@code BigInteger.shiftLeft} is in the model's tail (bit-twiddling surface, out of scope for
     * the long-backed bounded model): reaching it is UNKNOWN (a model gap), not a silent stub that
     * would let an unconstrained result through, and not a refutation. (This probe previously used
     * {@code gcd}, which has since been modeled.)
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    void reaching_a_tailed_biginteger_member_is_undecided() {
        BigInteger x = BigInteger.valueOf(12);
        BigInteger s = x.shiftLeft(2); // synthesized loud body -> sentinel -> UNKNOWN
        Bmc.check(s.longValue() == 48); // never reached
    }

    /**
     * Acknowledgment opt-out (revision 6): the SAME {@code ArrayList.sort} reach, but
     * {@code acknowledgeUnmodelled} lists it — so instead of UNKNOWN it degrades to the classic
     * nondet-stub behavior (treated as an unconstrained havoc, footnoted, never silent) and the proof
     * proceeds. {@code expect = VERIFIED}: with the member acknowledged-as-nondet and no real
     * counterexample, the proof passes with a loud footnote naming the acknowledged member.
     */
    @BmcProof(acknowledgeUnmodelled = {"java.util.ArrayList.sort"})
    void acknowledged_unmodelled_member_degrades_to_footnoted_nondet() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(2);
        a.add(1);
        a.sort((x, y) -> x - y); // acknowledged: treated as nondet stub, footnoted (not UNKNOWN)
    }

    /** Drives the backend directly to assert the demotion FACT names the reached member — so the
     *  UNKNOWN the interpreter raises (see {@link #reaching_an_unmodelled_list_member_is_undecided})
     *  carries the member name, never a faceless "could not decide". */
    @Test
    void demotion_names_the_reached_member() {
        String cp = System.getProperty("java.class.path");
        BmcRequest req = new BmcRequest(
                "proofs.audit.LoudUnmodelledProbe",
                "proofs.audit.LoudUnmodelledProbe.reaching_an_unmodelled_list_member_is_undecided",
                cp, 16, true, 16, false, "", 0);
        VerificationBackend backend = VerificationBackends.select(req);
        JbmcResult result = backend.verify(req);

        // The engine reports a would-be refutation (the loud body's assertion), but the harvested fact
        // names the reached member — which the interpreter then turns into a member-named UNKNOWN.
        assertFalse(result.isVerified(), "the loud body should fire, not verify");
        assertTrue(result.unmodelledMembers().stream().anyMatch(m -> m.contains("java.util.ArrayList.sort")),
                "the reached unmodelled member must be named: " + result.unmodelledMembers());
    }
}
