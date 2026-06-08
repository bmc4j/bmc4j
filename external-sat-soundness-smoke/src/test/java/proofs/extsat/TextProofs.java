package proofs.extsat;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proofs that GENUINELY depend on text/String reasoning — the inputs to the external-SAT (fast solver)
 * soundness smoke and the Step-0 empirical probe.
 *
 * <p>The fast external SAT solver runs the engine with its String reasoning OFF. For these proofs that is
 * NOT sound: the property each one states can only be decided by reasoning about string length/content.
 * The CI workflow runs them two ways:
 * <ul>
 *   <li><b>Guard path</b> (normal run with {@code solver = "kissat"}): each must <b>FAIL LOUD</b> — the
 *       safe-by-default guard refuses to hand a text proof to the fast solver. None may VERIFY.</li>
 *   <li><b>Probe path</b> (expert unsafe override on, {@code -Dbmc.externalSatUnsafeTextOverride=true}):
 *       records what jbmc actually does refinement-off on a string proof — does it self-protect
 *       (ignore the external solver / still refine) or does it falsely verify? The workflow captures the
 *       verdict; the guard's job is to make that question moot for ordinary users.</li>
 * </ul>
 *
 * <p>Under the DEFAULT solver (no external SAT) these are honest fail-on-purpose / verify proofs, so the
 * cold {@code gradlew test} run is green: {@link #refutable_only_through_string_length} is a real
 * refutation ({@code expect = REFUTED}) and {@link #verifies_only_with_string_reasoning} verifies.
 */
class TextProofs {

    /**
     * REFUTABLE only through string reasoning: an exactly-3-char symbolic string cannot have length &le; 2,
     * so this deliberately-false claim is refuted — but ONLY if the engine reasons about string length.
     * With String reasoning off, the length relationship is unconstrained, so a sound engine cannot
     * produce THIS refutation honestly. Pinned {@code expect = REFUTED} so the default-solver run is green.
     */
    @BmcProof(expect = Verdict.REFUTED, maxStringLength = 4)
    void refutable_only_through_string_length() {
        String s = Bmc.anyString(3, 3); // every exactly-3-character string
        Bmc.check(s.length() <= 2); // FALSE: length is exactly 3 — refutable via string length only
    }

    /**
     * VERIFIES only with string reasoning: a symbolic string of length &le; 2 has at most 2 characters.
     * The property is true, but proving it requires the engine to constrain the string's length — exactly
     * the reasoning the fast solver turns off. Verifies under the default solver (green cold run).
     */
    @BmcProof(maxStringLength = 4)
    void verifies_only_with_string_reasoning() {
        String s = Bmc.anyString(2); // every string of length 0..2
        Bmc.check(s.length() <= 2); // TRUE — but needs string-length reasoning to verify
    }
}
