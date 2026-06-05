package proofs.jakarta;

import example.jakarta.Ages;
import example.jakarta.User;
import example.jakarta.UserConstraints;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * The proof takes a {@code User} parameter — JBMC makes it (and its fields) a
 * symbolic input. {@code UserConstraints.assumeValid} is generated from the
 * {@code @Min/@Max/@NotNull/@Size} annotations, so the proof ranges over exactly
 * the Users the validation layer accepts.
 */
class ValidationProofTests {

    /** FAILS: age 120 is valid per @Max(120) but breaks the bucket lookup. */
    // Expected verdict: REFUTED - a jakarta-valid user still hits the seeded bug.
    @BmcProof(expect = Verdict.REFUTED)
    void group_handles_every_valid_user(User u) {
        UserConstraints.assumeValid(u);
        Ages.group(u);
    }

    /** PASSES: the clamped version is correct for every valid User. */
    @BmcProof
    void groupSafe_handles_every_valid_user(User u) {
        UserConstraints.assumeValid(u);
        Ages.groupSafe(u);
    }

    /**
     * You can LAYER extra assumptions on top of the generated ones. Validation
     * alone (age <= 120) isn't enough for the buggy lookup; narrowing the domain
     * with one more assume makes it provable.
     */
    @BmcProof
    void group_is_safe_for_users_under_120(User u) {
        UserConstraints.assumeValid(u);   // @Min/@Max/@NotNull/@Size
        Bmc.assume(u.age < 120);          // extra, hand-written domain constraint
        Ages.group(u);                    // PASSES under the combined assumptions
    }

    /**
     * You can also IGNORE the generated helper entirely and constrain by hand —
     * even with the Jakarta processor enabled. Use only what your proof needs.
     */
    @BmcProof
    void group_with_manual_constraints(User u) {
        Bmc.assume(u != null);
        Bmc.assume(u.age >= 0 && u.age < 120);   // your own bounds; name left free (unused here)
        Ages.group(u);                            // PASSES
    }
}
