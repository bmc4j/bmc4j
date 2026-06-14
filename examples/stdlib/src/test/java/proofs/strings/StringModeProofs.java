package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;

/**
 * Demonstrates the per-proof {@link StringMode} knob ({@code @BmcProof(stringMode = ...)}).
 *
 * <p>The proof below is a STRING-AS-DATA / decimal round-trip over a BYTE BUFFER - the kernel of an
 * okio-style {@code writeDecimalLong}/{@code readDecimalLong}: a symbolic value is encoded as ASCII
 * digit bytes into a {@code byte[]} and parsed back, and we prove the round-trip is the identity. All
 * reasoning is over the BYTE/INT values in the buffer; no {@code java.lang.String} content op
 * ({@code equals}/{@code contains}/{@code substring}/…) is involved. This is the class of proof where
 * JBMC's string-refinement solver is overkill, and where (on the real okio example) refinement can
 * explode formula construction while it stays tractable with refinement OFF.
 *
 * <p>Both modes VERIFY this proof. {@link StringMode#CHAR_ARRAY_MODEL} is a COMPLETENESS tradeoff, never a soundness
 * one: it is the right mode for string-as-DATA / encoding proofs like this. A string-CONTENT op that
 * relies on refinement (or a {@code CProverString} helper the shim does not implement, e.g. the
 * char-array {@code String} constructor {@code CProverString.ofCharArray}) would nondet-stub to UNKNOWN
 * under CHAR_ARRAY_MODEL - never a false VERIFIED - which is why CONTENT proofs keep the default
 * {@link StringMode#REFINEMENT}. We pin both modes so the toggle is shown reaching the engine and
 * producing the same sound verdict.
 */
class StringModeProofs {

    /** Encode a 0..99 value as two ASCII digit bytes and parse it back - a decimal byte round-trip. */
    private static int decimalRoundTrip(int value) {
        byte[] buf = new byte[2];
        buf[0] = (byte) ('0' + value / 10);
        buf[1] = (byte) ('0' + value % 10);
        return (buf[0] - (byte) '0') * 10 + (buf[1] - (byte) '0');
    }

    // VERIFIES under the DEFAULT mode (string refinement ON) - the baseline.
    @BmcProof(unwind = 4)
    void decimal_round_trip_under_refinement() {
        int value = Bmc.anyInt(0, 99);
        Bmc.check(decimalRoundTrip(value) == value);
    }

    // SAME proof, string refinement turned OFF via the per-proof knob. The toggle reaches the engine
    // (it emits --no-refine-strings and omits --max-nondet-string-length) and the proof still VERIFIES
    // over the byte-buffer arithmetic.
    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void decimal_round_trip_under_string_mode_none() {
        int value = Bmc.anyInt(0, 99);
        Bmc.check(decimalRoundTrip(value) == value);
    }
}
