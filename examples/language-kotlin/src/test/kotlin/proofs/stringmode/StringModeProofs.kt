package proofs.stringmode

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.StringMode

/**
 * Demonstrates the per-proof [StringMode] knob (`@BmcProof(stringMode = ...)`).
 *
 * The proof below is a STRING-AS-DATA / decimal round-trip over a BYTE BUFFER - the kernel of an
 * okio-style `writeDecimalLong`/`readDecimalLong`: a symbolic value is encoded as ASCII digit bytes into
 * a `ByteArray` and parsed back, and we prove the round-trip is the identity. All reasoning is over the
 * BYTE/INT values in the buffer; no `java.lang.String` content op (`equals`/`contains`/`substring`/…) is
 * involved. This is exactly the class of proof where JBMC's string-refinement solver is overkill, and
 * where (on the real okio example) refinement can explode formula construction while it stays tractable
 * with refinement OFF.
 *
 * Both modes VERIFY this proof. [StringMode.CHAR_ARRAY_MODEL] is a COMPLETENESS tradeoff, never a soundness one: it
 * is the right mode for string-as-DATA / encoding proofs like this. A string-CONTENT op that relies on
 * refinement (or a `CProverString` helper the shim does not implement, e.g. the char-array `String`
 * constructor `CProverString.ofCharArray`) would nondet-stub to UNKNOWN under CHAR_ARRAY_MODEL - never a false
 * VERIFIED - which is why CONTENT proofs keep the default [StringMode.REFINEMENT]. We pin both modes
 * here so the toggle is shown actually reaching the engine and producing the same sound verdict.
 */
class StringModeProofs {

    /** Encode a 0..99 value as two ASCII digit bytes and parse it back - a decimal byte round-trip. */
    private fun decimalRoundTrip(value: Int): Int {
        val buf = ByteArray(2)
        buf[0] = ('0'.code + value / 10).toByte()
        buf[1] = ('0'.code + value % 10).toByte()
        return (buf[0] - '0'.code.toByte()) * 10 + (buf[1] - '0'.code.toByte())
    }

    // VERIFIES under the DEFAULT mode (string refinement ON) - the baseline.
    @BmcProof(unwind = 4)
    fun decimal_round_trip_under_refinement() {
        val value = Bmc.anyInt(0, 99)
        Bmc.check(decimalRoundTrip(value) == value)
    }

    // SAME proof, with string refinement turned OFF via the per-proof knob. The toggle reaches the
    // engine (it emits --no-refine-strings and omits --max-nondet-string-length) and the proof still
    // VERIFIES over the byte-buffer arithmetic.
    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL)
    fun decimal_round_trip_under_string_mode_none() {
        val value = Bmc.anyInt(0, 99)
        Bmc.check(decimalRoundTrip(value) == value)
    }
}
