package proofs.vacuity;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Vacuity check: a proof whose assumptions are contradictory passes <em>vacuously</em> —
 * verified over an empty input domain, so it checks nothing. bmc4j injects a reachability marker at
 * each proof's normal exit and FAILS the proof with a dedicated verdict
 * ({@code "assumptions are unsatisfiable - this proof checks nothing"}) when no input survives the
 * assumptions. This is the same "visible over silent" discipline as the engine's soundness guards,
 * applied to your own harness.
 */
class VacuityProofTests {

    /**
     * VACUOUS (declared): the two {@code assume} calls contradict — no {@code x} is both {@code > 0}
     * and {@code < 0}. Without the vacuity check this would show green while proving nothing.
     * {@code expect = VACUOUS} makes the demo a real regression test: it PASSES while the vacuity
     * check fires, and goes red if the check ever stops firing.
     */
    @BmcProof(expect = Verdict.VACUOUS)
    void contradictory_assumptions_check_nothing() {
        int x = Bmc.anyInt();
        Bmc.assume(x > 0);
        Bmc.assume(x < 0);          // empty domain: no value is both
        Bmc.check(x == 42);         // "passes" only because nothing reaches it
    }

    /**
     * VACUOUS (declared): a subtler emptiness — a 1-char symbolic string can never equal a 2-char
     * literal, so the {@code assume} excludes every input. A refactor that tightens a bound can
     * silently hollow out a real proof exactly like this.
     */
    @BmcProof(expect = Verdict.VACUOUS)
    void bound_too_short_for_the_literals_is_vacuous() {
        String region = Bmc.anyString(1);                       // length 0..1
        Bmc.assume(region.equals("us") || region.equals("eu")); // both literals are length 2
        Bmc.check(region.length() == 2);
    }

    /**
     * PASSES: a satisfiable sibling — there are values of {@code x} in {@code [1, 9]}, so the end is
     * reachable and the property genuinely holds. The vacuity check leaves real proofs green.
     */
    @BmcProof
    void satisfiable_assumptions_still_verify() {
        int x = Bmc.anyInt();
        Bmc.assume(x > 0);
        Bmc.assume(x < 10);         // non-empty domain: 1..9
        Bmc.check(x >= 1 && x <= 9);
    }

    /**
     * PASSES (no false vacuity): the success path returns normally, while the rejected-input path is
     * pruned with {@code assumeUnreachable()} in the {@code catch}. One normal exit is reachable, so
     * the proof is correctly non-vacuous even though one exit is dead.
     */
    @BmcProof
    void expected_exception_path_is_not_false_vacuity() {
        int x = Bmc.anyInt();
        try {
            if (x < 0) {
                throw new IllegalArgumentException("negative");
            }
            Bmc.check(x >= 0);      // reachable: the valid path
        } catch (IllegalArgumentException e) {
            Bmc.assumeUnreachable(); // prune the rejected input; this exit is intentionally dead
        }
    }
}
