package proofs.patternswitch;

import example.patternswitch.EnumRouting;
import example.patternswitch.EnumRouting.Status;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * The residual-invokedynamic trust surface, demonstrated. {@link EnumRouting#code} compiles to the
 * {@code SwitchBootstraps.enumSwitch} indy, which bmc4j deliberately does NOT desugar. Two layers
 * make that honest instead of silent:
 *
 * <ol>
 *   <li>The residual-indy pass replaces the site with a call to the bodiless
 *       {@code org.bmc4j.analysis.ResidualInvokedynamic.enumSwitch__SwitchBootstraps} marker, so
 *       the engine havocs it like any other unmodeled callee AND reports it through the normal
 *       stub channel (previously: havoc'd with no report at all).</li>
 *   <li>A refutation whose slice includes that havoc is demoted to UNKNOWN — the
 *       "counterexample" may be an artifact (here: the switch's MatchException default arm, which
 *       the real bootstrap can never reach), and REFUTED is reserved for real counterexamples.
 *       Previously such proofs failed as REFUTED with a fake counterexample.</li>
 * </ol>
 */
class EnumSwitchResidualProofs {

    /**
     * Real semantics would pin {@code code(s)} to -1..2 and VERIFY this — but the havoc'd marker
     * admits any int, the impossible values reach the check, and the would-be refutation is
     * demoted to a named UNKNOWN. If this demo ever turns VERIFIED, enumSwitch has started being
     * desugared/trusted: update the demo (and celebrate) or investigate the regression.
     */
    // Expected verdict: UNKNOWN - the residual indy's result is havoc'd, and a refutation
    // through havoc is an artifact, not a counterexample.
    @BmcProof(expect = Verdict.UNKNOWN)
    void enumSwitch_result_is_undecided_not_refuted(Status s) {
        Bmc.assume(s != null);
        int c = EnumRouting.code(s);
        Bmc.check(c >= -1 && c <= 2);
    }

    /**
     * Even a property INDEPENDENT of the switch result goes UNKNOWN: the havoc'd result can reach
     * the switch's throwing default arm, so "no uncaught exception" is refutable - an artifact,
     * demoted the same way. The classic indy-free form (an enum switch without {@code case null},
     * which compiles to $SwitchMap) analyzes soundly - see the other proofs in this package.
     */
    // Expected verdict: UNKNOWN - reaching a residual indy site is visibly undecided, never a
    // silent pass and never a fake refutation.
    @BmcProof(expect = Verdict.UNKNOWN)
    void reaching_the_residual_site_is_undecided(Status s) {
        int sentinel = 7;
        int ignored = EnumRouting.code(s);
        Bmc.check(sentinel == 7);
    }
}
