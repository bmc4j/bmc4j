package proofs.strings;

import java.nio.charset.StandardCharsets;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Sound byte[] -> String charset decode. JBMC links native byte[] decode (the boundary a
 * charset-decoding library's {@code bytes -> String} accessor boils down to: {@code new String(byte[],
 * Charset)}) to a nondet string, so these
 * went UNKNOWN. bmc4j now redirects the {@code new String(byte[], ...)} constructors to a sound
 * {@code BmcStrings.ofBytes} decoder (see {@code StringBytecode} / {@code BmcStrings}). The proofs
 * read {@code length()}/{@code charAt(i)} back over symbolic bytes; each PASS has a {@code REFUTED}
 * negative control proving the verify is real, not a vacuous/false VERIFY.
 *
 * <p>Lengths are tiny on purpose — symbolic length / the decode loop drive proof cost.
 */
class ByteToStringProofs {

    // --- ISO-8859-1 / Latin-1: char = byte & 0xFF, one byte to one char ------------------------

    // PASS: Latin-1 decode of 2 symbolic bytes yields chars equal to (byte & 0xFF).
    @BmcProof(unwind = 4)
    void latin1_decodes_byte_to_char() {
        byte b0 = Bmc.anyByte();
        byte b1 = Bmc.anyByte();
        byte[] data = { b0, b1 };
        String s = new String(data, StandardCharsets.ISO_8859_1);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == (char) (b0 & 0xFF));
        Bmc.check(s.charAt(1) == (char) (b1 & 0xFF));
    }

    // NEGATIVE CONTROL: a false claim about the Latin-1 decode must refute.
    @BmcProof(unwind = 4, expect = Verdict.REFUTED)
    void latin1_false_claim_refutes() {
        byte b0 = Bmc.anyByte();
        byte b1 = Bmc.anyByte();
        byte[] data = { b0, b1 };
        String s = new String(data, StandardCharsets.ISO_8859_1);
        Bmc.check(s.charAt(0) == s.charAt(1));   // refuted: the two bytes are independent
    }

    // PASS: US-ASCII decode over bytes constrained to the 0x00..0x7F ASCII domain agrees with the byte.
    @BmcProof(unwind = 4)
    void ascii_decodes_byte_to_char() {
        byte b0 = Bmc.anyByte();
        Bmc.assume(b0 >= 0 && b0 <= 0x7F);
        byte[] data = { b0 };
        String s = new String(data, StandardCharsets.US_ASCII);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) b0);
    }

    // --- UTF-8: 1-byte ASCII fast path ---------------------------------------------------------

    // PASS: a single ASCII byte (0x00..0x7F) decodes to a 1-char String equal to the byte.
    @BmcProof(unwind = 6)
    void utf8_ascii_byte_decodes_to_one_char() {
        byte b = Bmc.anyByte();
        Bmc.assume(b >= 0 && b <= 0x7F);
        byte[] data = { b };
        String s = new String(data, StandardCharsets.UTF_8);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) b);
    }

    // NEGATIVE CONTROL: claiming an ASCII byte decodes to a DIFFERENT char must refute.
    @BmcProof(unwind = 6, expect = Verdict.REFUTED)
    void utf8_ascii_false_char_refutes() {
        byte b = Bmc.anyByte();
        Bmc.assume(b >= 0 && b <= 0x7F);
        byte[] data = { b };
        String s = new String(data, StandardCharsets.UTF_8);
        Bmc.check(s.charAt(0) == (char) (b + 1));   // refuted: off by one
    }

    // --- UTF-8: 2-byte sequence ----------------------------------------------------------------

    // A well-formed 2-byte UTF-8 sequence (lead 110xxxxx, cont 10xxxxxx) for a code point in
    // [0x80, 0x7FF] decodes to ONE char equal to that code point. The full domain [0x80, 0x7FF]
    // (1920 code points) is intrinsically too heavy for the musl engine as a single proof (it blows
    // up mid-solve), so it is DOMAIN-SPLIT below: the four bands tile [0x80, 0x7FF] exactly
    // (contiguous, no gaps, no overlaps), so the conjunction proves the identical full-range fact.
    // The negative control covers the whole range in one shot (refutation finds its counterexample
    // cheaply).

    // Shared body: prove the 2-byte decode over the band [lo, hi].
    private static void utf8_two_byte_decodes_band(int lo, int hi) {
        int cp = Bmc.anyInt(lo, hi);
        byte lead = (byte) (0xC0 | (cp >> 6));
        byte cont = (byte) (0x80 | (cp & 0x3F));
        byte[] data = { lead, cont };
        String s = new String(data, StandardCharsets.UTF_8);
        Bmc.check(s.length() == 1);
        Bmc.check(s.charAt(0) == (char) cp);
    }

    // PASS (band 1/4): code points 0x080..0x27F.
    @BmcProof(unwind = 8)
    void utf8_two_byte_decodes_codepoint_0x080_0x27F() {
        utf8_two_byte_decodes_band(0x080, 0x27F);
    }

    // PASS (band 2/4): code points 0x280..0x47F.
    @BmcProof(unwind = 8)
    void utf8_two_byte_decodes_codepoint_0x280_0x47F() {
        utf8_two_byte_decodes_band(0x280, 0x47F);
    }

    // PASS (band 3/4): code points 0x480..0x67F.
    @BmcProof(unwind = 8)
    void utf8_two_byte_decodes_codepoint_0x480_0x67F() {
        utf8_two_byte_decodes_band(0x480, 0x67F);
    }

    // PASS (band 4/4): code points 0x680..0x7FF. Together bands 1..4 tile [0x80, 0x7FF].
    @BmcProof(unwind = 8)
    void utf8_two_byte_decodes_codepoint_0x680_0x7FF() {
        utf8_two_byte_decodes_band(0x680, 0x7FF);
    }

    // NEGATIVE CONTROL: a false claim about the 2-byte decode (wrong code point) must refute.
    @BmcProof(unwind = 8, expect = Verdict.REFUTED)
    void utf8_two_byte_false_codepoint_refutes() {
        int cp = Bmc.anyInt(0x80, 0x7FF);
        byte lead = (byte) (0xC0 | (cp >> 6));
        byte cont = (byte) (0x80 | (cp & 0x3F));
        byte[] data = { lead, cont };
        String s = new String(data, StandardCharsets.UTF_8);
        Bmc.check(s.charAt(0) == (char) (cp ^ 1));   // refuted: flipped low bit
    }
}
