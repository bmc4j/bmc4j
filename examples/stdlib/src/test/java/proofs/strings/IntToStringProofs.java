package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;
import org.cprover.CProverString;

/**
 * Sound {@code int -> String}. JBMC's frontend lowers {@code Integer.toString(int)} /
 * {@code String.valueOf(int)} to {@code org.cprover.CProverString.toString(int)}. bmc4j ships its own
 * {@link org.cprover.CProverString} (to shadow CBMC's unsound internal string model); before the shim
 * implemented {@code toString(int)} the class was present but the method was missing, so the call site
 * hit a missing method and JBMC nondet-stubbed it -> every int-to-String proof came back UNKNOWN. With
 * the sound {@code toString(int)} in place, JBMC recognises the intrinsic and lowers it soundly, so the
 * decimal rendering's {@code length()}/{@code charAt(i)} agree with the value.
 *
 * <p>Ranges are tiny on purpose (single- and double-digit) so the symbolic decimal rendering stays a
 * tractable unwind; each PASS has a {@code REFUTED} negative control proving the verify is real.
 */
class IntToStringProofs {

    // PASS: a single decimal digit 0..9 renders to a 1-char String whose char is '0' + the digit.
    @BmcProof(maxStringLength = 1, unwind = 4)
    void single_digit_renders_to_one_char() {
        int d = Bmc.anyInt(0, 9);
        String s = Integer.toString(d);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) ('0' + d));
    }

    // NEGATIVE CONTROL: claiming the digit renders to the WRONG char must refute (no false VERIFY).
    @BmcProof(maxStringLength = 1, unwind = 4, expect = Verdict.REFUTED)
    void single_digit_wrong_char_refutes() {
        int d = Bmc.anyInt(0, 8);
        String s = Integer.toString(d);
        Bmc.check(s.charAt(0) == (char) ('0' + d + 1));   // refuted: off by one
    }

    // PASS: a negative single digit -9..-1 renders to "-d": length 2, sign then digit.
    @BmcProof(maxStringLength = 2, unwind = 4)
    void negative_single_digit_has_sign() {
        int d = Bmc.anyInt(1, 9);
        String s = Integer.toString(-d);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == '-');
        Bmc.check(s.charAt(1) == (char) ('0' + d));
    }

    // PASS: String.valueOf(int) over a two-digit value 10..99 renders to length 2 with the right digits.
    @BmcProof(maxStringLength = 2, unwind = 4)
    void two_digit_value_renders_both_digits() {
        int v = Bmc.anyInt(10, 99);
        String s = String.valueOf(v);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == (char) ('0' + v / 10));
        Bmc.check(s.charAt(1) == (char) ('0' + v % 10));
    }

    // NEGATIVE CONTROL: a two-digit value cannot render to a length-1 String.
    @BmcProof(maxStringLength = 2, unwind = 4, expect = Verdict.REFUTED)
    void two_digit_value_length_one_refutes() {
        int v = Bmc.anyInt(10, 99);
        String s = String.valueOf(v);
        Bmc.check(s.length() == 1);   // refuted: a 10..99 value always renders to two chars
    }

    // PASS: a DIRECT call to the shim intrinsic org.cprover.CProverString.toString(int) -- the surface
    // JBMC's frontend lowers Integer.toString/String.valueOf to, and the call site that landed on a
    // missing method before this shim implemented it. This proof does not even COMPILE without the
    // method present, which is the gap itself: any caller of the bmc4j-shipped CProverString could not
    // reference toString(int) at all.
    @BmcProof(maxStringLength = 1, unwind = 4)
    void direct_intrinsic_renders_single_digit() {
        int d = Bmc.anyInt(0, 9);
        String s = CProverString.toString(d);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) ('0' + d));
    }
}
