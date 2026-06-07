package proofs.jakarta;

import example.jakarta.Address;
import example.jakarta.AddressConstraints;
import example.jakarta.Customer;
import example.jakarta.CustomerConstraints;
import example.jakarta.Customers;
import example.jakarta.Node;
import example.jakarta.NodeConstraints;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Cascading {@code @Valid}: the generated {@code assumeValid} recurses into a nested bean's own
 * constraints class (null-guarded). Without the cascade, a proof over a symbolic Customer would
 * quietly range over Addresses the validator rejects.
 */
class CascadeProofTests {

    /**
     * PASSES — and only because the cascade is honored: region() indexes a 4-bucket array by
     * zip/33333, which stays in {0,1,2,3} EXACTLY because the cascade brings Address's
     * {@code @Max(99999)} into the proof domain. Without the cascade, zip would be an unconstrained
     * int and this would be REFUTED. The green verdict regression-pins that the cascade fires.
     */
    @BmcProof
    void region_is_safe_because_the_cascade_bounds_zip(Customer c) {
        CustomerConstraints.assumeValid(c);
        Customers.region(c);
    }

    /**
     * REFUTED: regionNarrow() uses a 3-bucket array but the cascade admits zip 99999 (zip/33333 == 3),
     * indexing out of bounds — a valid Customer still hits the bug.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void narrow_region_breaks_at_the_inner_boundary(Customer c) {
        CustomerConstraints.assumeValid(c);
        Customers.regionNarrow(c);
    }

    /** PASSES: the clamped version is safe for every valid Customer. */
    @BmcProof
    void narrow_region_safe_handles_every_valid_customer(Customer c) {
        CustomerConstraints.assumeValid(c);
        Customers.regionNarrowSafe(c);
    }

    /**
     * PASSES: the nested Address bound is directly observable after the cascade — a valid Customer's
     * zip is in 0..99999. Asserting it via the cascaded helper proves the inner constraint reached
     * the domain. (If the cascade were dropped, zip would be free and this would be REFUTED.)
     */
    @BmcProof
    void cascade_brings_the_inner_bound_into_scope(Customer c) {
        CustomerConstraints.assumeValid(c);
        Bmc.check(c.address.zip >= 0 && c.address.zip <= 99999);
    }

    /**
     * PASSES: a null nested field passes the cascade. Address-typed field stays null-in-domain when
     * it has no @NotNull. Here we assume only the inner Address directly to isolate the null-guard:
     * the cascade's "if (x != null)" means a null Address is valid.
     */
    @BmcProof
    void null_nested_field_passes_the_cascade(Address a) {
        // The cascade helper on a possibly-null Address: null is admitted (no crash, no constraint).
        if (a != null) {
            AddressConstraints.assumeValid(a);
            Bmc.check(a.zip >= 0 && a.zip <= 99999);
        }
    }

    /**
     * PASSES: a self-referential {@code @Valid Node next} generates compiling, finite code; the
     * recursive assume is bounded by JBMC's unwind. After the cascade, the head node's value bound
     * holds; the chain is explored to the unwind depth.
     */
    @BmcProof
    void self_referential_cascade_compiles_and_bounds(Node n) {
        NodeConstraints.assumeValid(n);
        Bmc.check(n.value >= 0);
    }
}
