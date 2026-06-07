package proofs.audit;

import java.math.BigInteger;
import java.util.ArrayList;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Upgrade A probe (a LIVE regression test in the green suite): proves that reaching a member the model
 * deliberately does NOT implement — declared via {@code @BmcNotModelled}/{@code @BmcNotNeeded} or
 * absorbed by {@code @BmcModelTail} — fails LOUDLY under JBMC instead of silently havocking to a nondet
 * stub.
 *
 * <p>The build-time loud-body synthesis pass gives every such member a body that throws
 * {@code AssertionError("bmc4j: unmodelled member <Class.member> — <reason>")}. Under JBMC an uncaught
 * {@code AssertionError} on a reachable path is a property violation, so these proofs are REFUTED. We
 * declare {@code expect = REFUTED}: the test PASSES while the loud body fires, and goes red (a loud test
 * failure naming both verdicts) if a future change ever makes one of these members silently havoc again
 * — exactly the regression this whole mechanism exists to prevent.
 *
 * <p>The targets are members that are genuinely unmodeled today (not on the modeled surface): if one of
 * them later gets a real model, this probe will go red (VERIFIED), signalling "retarget me at a member
 * that is still unmodeled" — the loud, intended failure mode.
 */
class LoudUnmodelledProbe {

    /**
     * {@code ArrayList.sort(Comparator)} is a declared {@code @BmcNotModelled} member (comparator-driven
     * sort over the bounded array). The synthesized loud body throws on call, so the proof is refuted —
     * NOT a silent nondet result.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void reaching_an_unmodelled_list_member_is_loud() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(2);
        a.add(1);
        a.sort((x, y) -> x - y); // no real model body; the synthesized loud AssertionError fires here
        Bmc.check(a.get(0) == 1); // never reached — the loud body refutes first
    }

    /**
     * {@code BigInteger.gcd} is in the model's tail (number-theory surface, out of scope for the
     * long-backed bounded model): reaching it is loud, not a silent stub that would let an unconstrained
     * result through.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void reaching_a_tailed_biginteger_member_is_loud() {
        BigInteger x = BigInteger.valueOf(12);
        BigInteger y = BigInteger.valueOf(8);
        BigInteger g = x.gcd(y); // synthesized loud AssertionError fires here
        Bmc.check(g.longValue() == 4); // never reached
    }
}
