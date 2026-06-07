package proofs.audit;

import java.util.ArrayList;
import java.util.HashMap;

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
 */
class LoudUnmodelledProbe {

    /**
     * {@code HashMap.merge} is a declared {@code @BmcNotModelled} member (functional-arg surface). The
     * synthesized loud body throws on call, so the proof is refuted — NOT a silent nondet result.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void reaching_an_unmodelled_map_member_is_loud() {
        HashMap<Integer, Integer> m = new HashMap<>();
        m.put(1, 10);
        // merge() has no real model body; the synthesized loud AssertionError fires here.
        m.merge(1, 5, (a, b) -> a + b);
        Bmc.check(m.get(1) == 15); // never reached — the loud body refutes first
    }

    /**
     * {@code ArrayList.addAll} is covered by the model's tail (a {@code @BmcNotNeeded} declaration here,
     * tailed in general): reaching it is loud, not a silent stub that would let an unconstrained result
     * through.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void reaching_a_tailed_list_member_is_loud() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(1);
        ArrayList<Integer> b = new ArrayList<>();
        b.add(2);
        a.addAll(b); // synthesized loud AssertionError fires here
        Bmc.check(a.size() == 2); // never reached
    }
}
