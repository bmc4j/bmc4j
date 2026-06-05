package proofs.custommodels;

import example.custommodels.TaxPolicy;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Proofs over a consumer-authored {@code src/bmcModel} class whose body uses constructs JBMC handles
 * unsoundly unless the model goes through the same bytecode rewrite passes as the proof code: a
 * {@code String.equals} branch and integer {@code Math.floorDiv}.
 *
 * <p>These are <b>soundness regression</b> proofs. When the user model is rewritten:
 * <ul>
 *   <li>{@code exempt_region_pays_no_tax} VERIFIES — {@code "EXEMPT".equals(region)} is sound, so the
 *       exempt branch is always taken and the tax is exactly zero. If the model were left unrewritten,
 *       JBMC would treat {@code String.equals} as nondet and could take the else branch, producing a
 *       non-zero tax counterexample (a false REFUTE) — proving the model is genuinely exercised.</li>
 *   <li>{@code tax_is_never_negative} VERIFIES — {@code Math.floorDiv} of two non-negative operands is
 *       non-negative. Unrewritten, JBMC stubs {@code Math.floorDiv} to nondet, which would refute.</li>
 *   <li>{@code region_matches_itself} VERIFIES — comparing a <em>symbolic</em> region against itself
 *       by content is always true once {@code String.equals} is sound. Unrewritten, JBMC's nondet
 *       {@code String.equals} can return false for {@code region.equals(region)}, refuting it — a clean
 *       verdict flip that pins down whether the user model was actually rewritten.</li>
 * </ul>
 */
class TaxPolicyProofTests {

    @BmcProof
    void exempt_region_pays_no_tax() {
        long amount = Bmc.anyLong(0, 1_000_000);
        long tax = new TaxPolicy().taxOn(amount, "EXEMPT");
        Bmc.check(tax == 0);
    }

    @BmcProof
    void tax_is_never_negative() {
        long amount = Bmc.anyLong(0, 1_000_000);
        // A region that is statically not "EXEMPT", so the symbolic-rate branch is exercised.
        long tax = new TaxPolicy().taxOn(amount, "STANDARD");
        Bmc.check(tax >= 0);
    }

    @BmcProof
    void region_matches_itself() {
        // A SYMBOLIC region: the self-equality inside the model holds for every value only when the
        // model's String.equals is soundly rewritten.
        String region = Bmc.anyString(8);
        Bmc.check(new TaxPolicy().regionMatchesItself(region));
    }
}
