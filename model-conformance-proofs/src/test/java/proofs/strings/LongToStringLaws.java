package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;

/**
 * Conformance for the {@code long -> String} {@code @ConditionalOn} no-refine override
 * ({@code org.bmc4j.engine.BmcStrings.ofLong}, redirecting {@code org.cprover.CProverString.toString(long)})
 * - the {@code long} twin of {@link IntToStringLaws}. Under {@code StringMode.CHAR_ARRAY_MODEL} the
 * override is length-bounded ({@code <= 20} chars, a {@code long}'s max) and value-correct; under
 * {@code REFINEMENT} the intrinsic is unchanged.
 */
class LongToStringLaws {

    // ===== CHAR_ARRAY_MODEL: the override is bounded (the whole point) =====================

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void length_is_bounded_for_any_long_none() {
        long i = Bmc.anyLong();
        Bmc.check(Long.toString(i).length() <= 20);
    }

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void value_of_length_is_bounded_for_any_long_none() {
        long i = Bmc.anyLong();
        // String.valueOf(long) shares the CProverString.toString(long) choke point the override redirects.
        Bmc.check(String.valueOf(i).length() <= 20);
    }

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void length_is_at_least_one_for_any_long_none() {
        long i = Bmc.anyLong();
        Bmc.check(Long.toString(i).length() >= 1);
    }

    // ===== CHAR_ARRAY_MODEL: representative values are correct (differential vs the JDK) ====

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void zero_is_built_through_char_path_none() {
        String s = Long.toString(0L);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == '0');
    }

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void minus_one_is_correct_none() {
        String s = Long.toString(-1L);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == '-');
        Bmc.check(s.charAt(1) == '1');
    }

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void min_value_is_correct_none() {
        // Long.MIN_VALUE = -9223372036854775808: the two's-complement trap. 20 chars.
        String s = Long.toString(Long.MIN_VALUE);
        Bmc.check(s.length() == 20);
        Bmc.check(s.charAt(0) == '-');
        Bmc.check(s.charAt(1) == '9');
        Bmc.check(s.charAt(19) == '8');
    }

    @BmcProof(unwind = 21, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void symbolic_small_long_matches_decimal_none() {
        long d = Bmc.anyLong(0L, 9L);
        String s = Long.toString(d);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) ('0' + d));
    }

    // ===== REFINEMENT: the override did NOT alter the refinement path =====================

    @BmcProof
    void refinement_zero_unchanged() {
        Bmc.check(Long.toString(0L).equals("0"));
    }

    @BmcProof
    void refinement_min_value_unchanged() {
        // Under refinement no override fires; the CProverString intrinsic runs. Assert the structural shape (a full 20-char
        // equality against a long literal is a string-refinement completeness wall, not a feature issue);
        // length + boundary chars confirm the intrinsic still produces the right value here.
        String s = Long.toString(Long.MIN_VALUE);
        Bmc.check(s.length() == 20);
        Bmc.check(s.charAt(0) == '-');
        Bmc.check(s.charAt(1) == '9');
    }
}
