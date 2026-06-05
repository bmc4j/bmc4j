package proofs.strings;

import example.strings.Charsets;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Charset- and length-bounded symbolic strings: {@code Bmc.anyAsciiString(n)},
 * {@code Bmc.anyString(n, alphabet)}, {@code Bmc.anyString(min, max)}. These fold the per-character
 * domain into the helper — the string analogue of {@code anyInt(lo, hi)} — so a proof ranges only
 * over realistic inputs (and keeps the symbolic length, the cost driver, small).
 *
 * <p>Each helper's constraint is enforced via {@code assume} over the sound char primitive, so the
 * constraints HOLD: the proofs below read {@code length()}/{@code charAt(i)} back and the bounds are
 * honoured; a deliberately false claim about the same string refutes. Lengths are kept tiny on
 * purpose — symbolic string length drives proof cost.
 */
class CharsetProofs {

    // --- anyString(min, max): length bounds hold both ways -------------------

    // PASS: a (min,max)-bounded string's length is within [min,max] for every value.
    @BmcProof
    void length_bounds_hold() {
        String code = Bmc.anyString(2, 3);
        Bmc.check(code.length() >= 2 && code.length() <= 3);
    }

    // PASS: anyString(n, n) pins the length exactly.
    @BmcProof
    void exact_length_form() {
        String s = Bmc.anyString(3, 3);
        Bmc.check(s.length() == 3);
    }

    // INTENDED FAILURE: claiming the length is always exactly the max is false (it can be the min).
    // Expected verdict: REFUTED - the length bound is a RANGE, not a pin.
    @BmcProof(expect = Verdict.REFUTED)
    void length_is_not_always_max() {
        String code = Bmc.anyString(0, 3);
        Bmc.check(code.length() == 3);   // refuted by a shorter string (e.g. "")
    }

    // --- anyAsciiString(n): every char is printable ASCII --------------------

    // PASS: every character of an anyAsciiString is in [0x20, 0x7E].
    @BmcProof
    void ascii_chars_are_printable() {
        String name = Bmc.anyAsciiString(4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            Bmc.check(c >= 0x20 && c <= 0x7E);
        }
    }

    // PASS over every printable-ASCII string: it has no ASCII control characters (code under proof).
    @BmcProof
    void ascii_string_has_no_control_chars() {
        String s = Bmc.anyAsciiString(4);
        Bmc.check(Charsets.hasNoAsciiControlChars(s));
    }

    // INTENDED FAILURE: not every printable-ASCII char is a digit — the constraint is the full
    // printable range, not just '0'..'9', so this over-claim refutes.
    // Expected verdict: REFUTED - printable ASCII includes non-digits.
    @BmcProof(expect = Verdict.REFUTED)
    void ascii_string_is_not_all_digits() {
        String s = Bmc.anyAsciiString(2);
        Bmc.assume(s.length() >= 1);
        Bmc.check(Charsets.isAllDigits(s));   // refuted by e.g. "A"
    }

    // --- anyString(n, alphabet): every char is from the alphabet -------------

    // PASS: every character of anyString(2, "ab") is 'a' or 'b', and length <= 2.
    @BmcProof
    void alphabet_chars_are_in_alphabet() {
        String s = Bmc.anyString(2, "ab");
        Bmc.check(s.length() <= 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Bmc.check(c == 'a' || c == 'b');
        }
    }

    // PASS over every digit-alphabet string: the code-under-proof digit check holds.
    @BmcProof
    void digit_alphabet_string_is_all_digits() {
        String s = Bmc.anyString(3, "0123456789");
        Bmc.check(Charsets.isAllDigits(s));
    }

    // INTENDED FAILURE: an "ab"-alphabet string is not always "aa" — over-claims a single value.
    // Expected verdict: REFUTED - the alphabet admits more than one string.
    @BmcProof(expect = Verdict.REFUTED)
    void alphabet_string_is_not_a_single_value() {
        String s = Bmc.anyString(2, "ab");
        Bmc.assume(s.length() == 2);
        Bmc.check(s.equals("aa"));   // refuted by e.g. "ab"
    }

    // --- a proof OVER a constrained string verifies --------------------------

    // PASS: a real property of code-under-proof, proven over a charset-bounded input domain —
    // a string of <=3 digits never contains a non-digit, so isAllDigits is always true.
    @BmcProof
    void property_over_constrained_string_verifies() {
        String s = Bmc.anyString(3, "0123456789");
        Bmc.assume(s.length() >= 1);
        Bmc.check(s.charAt(0) >= '0' && s.charAt(0) <= '9');
    }
}
