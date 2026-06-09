package proofs.jakarta;

import example.jakarta.Customer;
import example.jakarta.CustomerConstraints;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * {@code @NotBlank} lowers to a non-null check AND a bounded {@code charAt} blankness scan ("some
 * index in [0, maxStringLength) holds a char {@code > ' '}") — the {@code length()}/{@code charAt}
 * route, which uses only JBMC-natively-sound string primitives and never the native
 * {@code String.trim()} model (which mis-binds on the older Kotlin matrix legs, false-refuting this
 * proof). Unlike the numeric constraints, {@code @NotBlank} REJECTS null (the deliberate jakarta
 * asymmetry).
 */
class NotBlankProofTests {

    /**
     * REFUTED: @NotBlank guarantees SOME non-blank char exists, NOT that index 0 is non-blank — a
     * leading-space name like " x" is perfectly valid. So claiming "the first char is visible" is
     * refutable through the generated assume, which correctly admits leading whitespace.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void notBlank_does_not_imply_the_first_char_is_visible(Customer c) {
        CustomerConstraints.assumeValid(c);
        Bmc.check(c.name.length() == 0 || c.name.charAt(0) > ' ');
    }

    /**
     * PASSES: a valid name is non-null and contains at least one visible character — so its length is
     * positive. This is exactly what @NotBlank guarantees.
     */
    @BmcProof
    void valid_name_is_non_empty(Customer c) {
        CustomerConstraints.assumeValid(c);
        Bmc.check(c.name != null && c.name.length() > 0);
    }

    /**
     * PASSES: @NotBlank REJECTS null — a valid Customer never has a null name. This pins the
     * asymmetry vs the numeric constraints (where null passes). If the generated assume had treated
     * null as valid, "name != null" would be refutable here.
     */
    @BmcProof
    void notBlank_rejects_null(Customer c) {
        CustomerConstraints.assumeValid(c);
        Bmc.check(c.name != null);
    }
}
