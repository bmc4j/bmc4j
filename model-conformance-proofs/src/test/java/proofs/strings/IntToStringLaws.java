package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;

/**
 * Conformance for the {@code int -> String} {@code @ConditionalOn} no-refine override
 * ({@code org.bmc4j.engine.BmcStrings.ofInt}, which redirects the single refinement primitive every
 * funnel bottoms out in: {@code org.cprover.CProverString.toString(int)}).
 *
 * <p>The whole point of the override is the LENGTH BOUND under {@code StringMode.CHAR_ARRAY_MODEL}: with
 * the refinement-only {@code CProverString.toString} intrinsic, {@code int -> String} comes back
 * nondet-length (an {@code int} is really at most 11 chars incl. sign), which poisons proofs. Under
 * CHAR_ARRAY_MODEL the override builds a bounded char[] instead, so {@code length() <= 11} holds - and the
 * value is correct (representative inputs + a symbolic in-range int, asserted via the sound
 * {@code BmcStrings} char-by-char path so no string refinement is needed). Because the redirect catches
 * the shared choke point, BOTH {@code Integer.toString} AND {@code String.valueOf} come out bounded.
 *
 * <p>The REFINEMENT proofs at the bottom show the override did NOT alter the refinement path: the intrinsic
 * still produces correct values there.
 */
class IntToStringLaws {

    // ===== CHAR_ARRAY_MODEL: the override is bounded (the whole point) =====================

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void length_is_bounded_for_any_int_none() {
        int i = Bmc.anyInt();
        // The override builds into a fixed 11-char buffer; without it this length would be nondet.
        Bmc.check(Integer.toString(i).length() <= 11);
    }

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void value_of_length_is_bounded_for_any_int_none() {
        int i = Bmc.anyInt();
        // String.valueOf(int) bottoms out in the SAME CProverString.toString(int) choke point the override
        // redirects, so it inherits the bound — proving the single redirect covers every funnel.
        Bmc.check(String.valueOf(i).length() <= 11);
    }

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void length_is_at_least_one_for_any_int_none() {
        int i = Bmc.anyInt();
        // Every int renders to at least one char (even 0 -> "0"), never the empty string.
        Bmc.check(Integer.toString(i).length() >= 1);
    }

    // ===== CHAR_ARRAY_MODEL: representative values are correct (differential vs the JDK) ====

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void zero_is_built_through_char_path_none() {
        // 0 is the literal trap: it must be built char-by-char, never a bare "0" literal (whose backing
        // would be nondet-length under no-refine). length 1, char '0'.
        String s = Integer.toString(0);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == '0');
    }

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void minus_one_is_correct_none() {
        String s = Integer.toString(-1);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == '-');
        Bmc.check(s.charAt(1) == '1');
    }

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void min_value_is_correct_none() {
        // Integer.MIN_VALUE = -2147483648: the two's-complement trap (cannot be negated). 11 chars.
        String s = Integer.toString(Integer.MIN_VALUE);
        Bmc.check(s.length() == 11);
        Bmc.check(s.charAt(0) == '-');
        Bmc.check(s.charAt(1) == '2');
        Bmc.check(s.charAt(10) == '8');
    }

    @BmcProof(unwind = 12, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void symbolic_small_int_matches_decimal_none() {
        // A symbolic single-digit int renders to a single decimal char, exactly.
        int d = Bmc.anyInt(0, 9);
        String s = Integer.toString(d);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) ('0' + d));
    }

    // ===== REFINEMENT: the override did NOT alter the refinement path =====================

    @BmcProof
    void refinement_zero_unchanged() {
        // Under refinement no override fires; CProverString.toString stays the fast intrinsic, still "0".
        Bmc.check(Integer.toString(0).equals("0"));
    }

    @BmcProof
    void refinement_minus_one_unchanged() {
        Bmc.check(Integer.toString(-1).equals("-1"));
    }

    @BmcProof
    void refinement_min_value_unchanged() {
        Bmc.check(Integer.toString(Integer.MIN_VALUE).equals("-2147483648"));
    }
}
