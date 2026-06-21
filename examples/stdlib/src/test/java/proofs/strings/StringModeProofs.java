package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.BmcProfile;
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

    // === No-refine string-plumbing has no internal waste loops ===============================
    //
    // Three focused @BmcProfile proofs, one per internal no-refine string-plumbing path bmc4j keeps
    // cheap. Each VERIFIES; the point is the @BmcProfile "targetable loops" breakdown: each path no
    // longer unwinds the wasteful internal loop it used to. They are kept SEPARATE (not folded into one
    // proof) so each loop's presence/absence is read in isolation, uncontaminated by the others. Read the
    // `bmc4j[profile]:` lines in the test output.

    /**
     * Internal {@code charAt} read (fix 1). {@code startsWith} -> {@code BmcStrings.startsWith} -> the
     * internal {@code CProverString.charAt} read every {@code BmcStrings} content op funnels through.
     * Under no-refine that read is redirected (via {@code @ConditionalOn}) to the bounds-free
     * {@code BmcStrings.charAtRaw}, which reads the char-array model String's backing DIRECTLY instead of
     * delegating to the bounds-checking public {@code String.charAt} - so the dead out-of-range branch no
     * longer BUILDS a {@code StringIndexOutOfBoundsException} + its {@code int->String} message.
     *
     * <p>Profile check: the targetable loops NO LONGER list {@code java.lang.String.<init>:([CII)V} via
     * the {@code CProverString.charAt} exception path (the remaining {@code array[char].clone} is
     * {@code anyString}'s own symbolic-string introduction, a different path).
     */
    @BmcProof(unwind = 8, stringMode = StringMode.CHAR_ARRAY_MODEL)
    @BmcProfile
    void no_refine_internal_charAt_builds_no_exception() {
        String s = Bmc.anyString(3, "abc");
        boolean starts = s.startsWith("a");     // -> BmcStrings.startsWith -> charAtRaw (no exception build)
        Bmc.check(starts || !starts);
    }

    /**
     * Char-array rebuild builder (fix 3). {@code new String(char[])} is rebuilt through
     * {@code BmcStrings.ofChars}'s {@code StringBuilder}. The array is 18 chars, OVER the StringBuilder
     * model's default capacity (16), so an UNSIZED builder would grow ({@code ensureCapacityInternal}'s
     * copy loop). Pre-sizing the builder to the known final length removes that grow-loop unwind.
     *
     * <p>Profile check: the targetable loops NO LONGER list
     * {@code AbstractStringBuilder.ensureCapacityInternal} growth (the remaining
     * {@code java.lang.String.<init>:([CII)V} is the builder's {@code toString()} copying the backing into
     * the result String - a single, unavoidable, bounded copy, not a growth loop).
     */
    @BmcProof(unwind = 24, stringMode = StringMode.CHAR_ARRAY_MODEL)
    @BmcProfile
    void no_refine_char_rebuild_builder_does_not_grow() {
        char[] data = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                       'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r'};   // 18 chars > default capacity 16
        String built = new String(data);        // -> BmcStrings.ofChars -> a PRE-SIZED StringBuilder
        Bmc.check(built.length() == 18);
    }

    /**
     * Literal construction (fix 2). A fixed string LITERAL under no-refine is pinned to a char-array
     * construction; it now routes through the char-array model's clone-free {@code String.adoptChars}
     * factory, which ADOPTS the freshly built (and exclusively owned) array as its backing with no
     * defensive copy - so there is no {@code array[char].clone}.
     *
     * <p>Profile check: the targetable loops are EMPTY - in particular no {@code array[char].clone} for
     * the literal's construction.
     */
    @BmcProof(unwind = 8, stringMode = StringMode.CHAR_ARRAY_MODEL)
    @BmcProfile
    void no_refine_literal_is_adopted_clone_free() {
        String literal = "abc";                 // -> String.adoptChars (clone-free adoption)
        Bmc.check(literal.length() == 3);
    }
}
