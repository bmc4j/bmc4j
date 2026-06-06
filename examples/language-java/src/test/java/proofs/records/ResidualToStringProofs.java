package proofs.records;

import example.records.Box;
import example.records.Point;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * The residual-invokedynamic trust surface, demonstrated on the one common construct that stays
 * residual by design: a record {@code toString} with a non-String reference component ({@link Box}
 * — see {@code RecordToStringProofs} for the desugared, fully-sound primitive/String case). Two
 * layers make the residue honest instead of silent:
 *
 * <ol>
 *   <li>The residual-indy pass replaces the site with a call to the bodiless
 *       {@code org.bmc4j.analysis.ResidualInvokedynamic.toString__ObjectMethods} marker, so the
 *       engine havocs it like any other unmodeled callee AND reports it: the green proof below
 *       carries a stub footnote naming the marker (previously: havoc'd with no report at all),
 *       and {@code -Dbmc.strictStubs=true} escalates it to UNKNOWN.</li>
 *   <li>A refutation whose slice includes that havoc is demoted to a named UNKNOWN — the
 *       "counterexample" may be an artifact of the havoc (here: the nondet String being null),
 *       and REFUTED is reserved for real counterexamples.</li>
 * </ol>
 */
class ResidualToStringProofs {

    /**
     * PASSES, with the residual-indy stub footnote: the checked property is independent of the
     * havoc'd toString result, so reaching the residual site is visible, not fatal.
     */
    @BmcProof
    void reaching_the_residual_site_is_visible_not_fatal(int x, int y) {
        Box b = new Box(new Point(x, y));
        String ignored = b.toString();
        Bmc.check(b.inner().x() == x);
    }

    /**
     * Real toString semantics would make this VERIFIED (a record's toString is never null) — but
     * the havoc'd marker admits null, the would-be NPE refutation is an artifact, and it is
     * demoted to a named UNKNOWN. If this demo ever turns VERIFIED, reference-component record
     * toString has started being desugared/trusted: update the demo or investigate.
     */
    // Expected verdict: UNKNOWN - a refutation through residual-indy havoc is an artifact,
    // not a counterexample.
    @BmcProof(expect = Verdict.UNKNOWN)
    void havoc_dependent_property_is_undecided_not_refuted(int x, int y) {
        Box b = new Box(new Point(x, y));
        Bmc.check(b.toString().length() >= 0);
    }
}
