package proofs.domainsplit;

import example.domainsplit.Clamp;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * End-to-end demos of the {@code domainSplit} DSL: an explicit partition of a slow proof's claimed
 * input domain into N independent slices plus one soundness cover check.
 *
 * <p>The split expands ONE proof into N+1 derived runs: N slice runs (the body under
 * {@code assume(slice_i)}) and one cover run ({@code overall => union(slices)}). The proof passes iff
 * the cover VERIFIED and every slice VERIFIED. A GAP (a sub-domain no slice covers) makes the cover
 * REFUTE — never a false green. A refuting slice surfaces its counterexample (early-exit). The reported
 * verdict is domain-SCOPED: a green means "holds over the overall condition", not the full type domain.
 */
class DomainSplitProofTests {

    /**
     * PASSES via its slices. The claimed domain is a wide int range; the three slices
     * (negative / zero / positive) cover it, and {@code clamp}'s result is in range in each.
     */
    @BmcProof
    void clamp_result_in_range_split_by_sign() {
        int x = Bmc.anyInt();
        Bmc.domainSplit(x >= -1_000_000 && x <= 1_000_000); // the claimed domain
        Bmc.slice(x < 0);
        Bmc.slice(x == 0);
        Bmc.slice(x > 0);
        int r = Clamp.clamp(x, -10, 10);
        Bmc.check(r >= -10 && r <= 10); // body runs once per slice
    }

    /**
     * PASSES with OVERLAPPING slices. {@code x <= 0} and {@code x >= 0} double-cover {@code x == 0};
     * the subset cover check ({@code overall => union}, not equality) accepts the overlap.
     */
    @BmcProof
    void overlapping_slices_are_accepted() {
        int x = Bmc.anyInt();
        Bmc.domainSplit(x >= -100 && x <= 100);
        Bmc.slice(x <= 0); // overlaps the next at x == 0
        Bmc.slice(x >= 0);
        int r = Clamp.clamp(x, -5, 5);
        Bmc.check(r >= -5 && r <= 5);
    }

    /**
     * FAILS LOUD via the COVER proof: the slices leave a GAP — nothing covers {@code x == 0} inside the
     * claimed {@code [-100, 100]} domain. The cover obligation {@code overall => (x<0 || x>0)} is
     * REFUTED at {@code x == 0}, so the whole proof fails — never a silent green over the uncovered point.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void gap_in_the_cover_fails_loud() {
        int x = Bmc.anyInt();
        Bmc.domainSplit(x >= -100 && x <= 100);
        Bmc.slice(x < 0);
        Bmc.slice(x > 0); // GAP: x == 0 is claimed but covered by no slice
        int r = Clamp.clamp(x, -5, 5);
        Bmc.check(r >= -5 && r <= 5);
    }

    /**
     * A REFUTED slice surfaces its counterexample (and cancels the remaining slices, early-exit). The
     * cover is sound (the three slices tile the domain), but {@code buggySign} mis-classifies
     * {@code x == 1}, which lives in the {@code x > 0} slice — so that slice REFUTES.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void a_refuted_slice_surfaces_its_counterexample() {
        int x = Bmc.anyInt();
        Bmc.domainSplit(x >= -1000 && x <= 1000);
        Bmc.slice(x < 0);
        Bmc.slice(x == 0);
        Bmc.slice(x > 0); // x == 1 is here, and buggySign(1) == 0 (should be 1)
        int expected = x < 0 ? -1 : (x == 0 ? 0 : 1);
        Bmc.check(Clamp.buggySign(x) == expected);
    }
}
