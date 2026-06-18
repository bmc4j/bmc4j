package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;
import org.bmc4j.Verdict;

/**
 * Conformance proofs for the sound char-array-backed String model used under string refinement OFF
 * (--no-refine-strings / StringMode.CHAR_ARRAY_MODEL). See {@code core/bmc-string-model} and
 * {@code BundledStringModel}.
 *
 * <p><b>How they run.</b> Each proof is pinned to {@code @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)} so it
 * exercises the char-array model with JBMC string refinement OFF (the production knob from the StringMode
 * PR; it both passes {@code --no-refine-strings} and prepends the bundled model). The same shapes also
 * verify under refinement, so they pin the model surface without depending on a test-only flag.
 *
 * <p><b>What the model makes sound under no-refine</b> (each pinned in both directions so a nondet model
 * could not satisfy it):
 * <ul>
 *   <li>{@code new String(char[])} / {@code new String(char[],int,int)} then {@code length()} /
 *       {@code charAt(i)} - exact content recovered from the char[] backing.</li>
 *   <li>The {@code StringBuilder.append(char) + toString()} construction primitive (the one bmc4j's
 *       whole sound String layer is rebuilt from) then {@code length()} / {@code charAt(i)}.</li>
 *   <li>A symbolic String built via {@code new String(symbolicCharArray)}: length and per-index reads
 *       agree with the source array.</li>
 *   <li>{@code String.equals} over constructed strings (rebuilt from the array backing).</li>
 * </ul>
 *
 * <p><b>Documented limits under no-refine</b> (sound, never a false VERIFIED - they go UNKNOWN/REFUTE):
 * <ul>
 *   <li>A String LITERAL's content is NOT recovered (JBMC materializes a literal without running a
 *       constructor, so the backing is a fresh nondet array): {@code "ab".length()} is nondet, not 2.
 *       Conservative - a literal-content claim is UNKNOWN, never wrong.</li>
 *   <li>A String LITERAL's content is still not recovered under no-refine (above). A String from
 *       {@code Bmc.anyString(...)} / raw {@code nondetWithoutNull()} is now LENGTH-BOUNDED soundly
 *       under no-refine: {@code StringLengthBytecode} rewrites the symbolic-string introduction into a
 *       bounded char-array construction (per-call {@code anyString(n)} bound, global
 *       {@code maxStringLength} for a bare nondet string), so the length knobs bind the same way they do
 *       under refinement. See {@code NoRefineLengthBoundLaws}.</li>
 * </ul>
 */
class NoRefineStringModelLaws {

    // ---- new String(char[]) construction + readback (SOUND under no-refine) ----

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void newStringFromChars_readback() {
        char[] data = {'a', 'b'};
        String s = new String(data);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == 'a');
        Bmc.check(s.charAt(1) == 'b');
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void newStringFromCharsRange_readback() {
        char[] data = {'x', 'a', 'b', 'y'};
        String s = new String(data, 1, 2);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == 'a');
        Bmc.check(s.charAt(1) == 'b');
    }

    // ---- StringBuilder.append(char) + toString() (the bmc4j construction primitive) ----

    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void stringBuilderBuild_readback() {
        StringBuilder sb = new StringBuilder();
        sb.append('a');
        sb.append('b');
        String s = sb.toString();
        Bmc.check(s.length() == 2);
        char[] out = new char[s.length()];
        for (int i = 0; i < s.length(); i++) {
            out[i] = s.charAt(i);
        }
        Bmc.check(out[0] == 'a');
        Bmc.check(out[1] == 'b');
    }

    // ---- symbolic String via construction (SOUND under no-refine) ----

    // Symbolic length 0..1 (kept small: under CHAR_ARRAY_MODEL there is no refinement to compress the symbolic
    // char-array reasoning, so a larger bound times out in CI - 0..1 is enough to pin symbolic readback).
    @BmcProof(unwind = 2, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void symbolicViaConstruction_lengthAndCharsAgree() {
        int n = Bmc.anyInt(0, 1);
        char[] data = new char[n];
        for (int i = 0; i < n; i++) {
            data[i] = Bmc.anyChar();
        }
        String s = new String(data);
        Bmc.check(s.length() == n);
        for (int i = 0; i < s.length(); i++) {
            Bmc.check(s.charAt(i) == data[i]);   // per-index agreement: a nondet read could refute this
        }
    }

    // ---- equals over constructed strings (rebuilt from the backing) ----

    @BmcProof(unwind = 3, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void equals_overConstructedStrings() {
        String a = new String(new char[]{'h', 'i'});
        String b = new String(new char[]{'h', 'i'});
        String c = new String(new char[]{'h', 'o'});
        Bmc.check(a.equals(b));    // equal content -> equal
        Bmc.check(!a.equals(c));   // differing content -> not equal
    }

    // ---- toLowerCase() / toLowerCase(Locale) (SOUND + LENGTH-PRESERVING under no-refine) ----
    //
    // Without a model these calls degrade to a nondet stub returning an UNCONSTRAINED-length String,
    // discarding the caller's length bound. The char-array model maps char-by-char via the native
    // Character.toLowerCase intrinsic, so the result is exact AND same-length for the ASCII/BMP common
    // case (the bound keeps flowing), and traps the locale-special / expanding / context-dependent chars
    // LOUD (UNKNOWN, never wrong). Each proof below pins a direction a nondet model could not satisfy.

    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCase_concrete_readback() {
        // Built through a constructor (so content is exact under no-refine), then lowercased: the
        // ASCII fold is exact and length-preserving.
        String s = new String(new char[]{'A', 'B', 'c'}).toLowerCase();
        Bmc.check(s.length() == 3);
        Bmc.check(s.charAt(0) == 'a');
        Bmc.check(s.charAt(1) == 'b');
        Bmc.check(s.charAt(2) == 'c');
    }

    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCaseLocale_concrete_readback() {
        // The Locale overload routes to the no-arg form: same exact, same-length result.
        String s = new String(new char[]{'A', 'B', 'c'}).toLowerCase(java.util.Locale.ROOT);
        Bmc.check(s.length() == 3);
        Bmc.check(s.charAt(0) == 'a' && s.charAt(1) == 'b' && s.charAt(2) == 'c');
    }

    // Length-preservation is the WHOLE POINT (it keeps the caller's length bound): a symbolic string of
    // length 0..1 over the safe ASCII band folds to the SAME length. Band 'A'..'H' avoids 'I' (which is
    // locale-trapped). Small bound: no refinement to compress the symbolic char-array reasoning under
    // CHAR_ARRAY_MODEL.
    @BmcProof(unwind = 2, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCase_symbolic_length_preserving_and_lowercase() {
        int n = Bmc.anyInt(0, 1);
        char[] data = new char[n];
        for (int i = 0; i < n; i++) {
            char c = Bmc.anyChar();
            Bmc.assume(c >= 'A' && c <= 'H');   // safe ASCII band (excludes locale-sensitive 'I')
            data[i] = c;
        }
        String lo = new String(data).toLowerCase();
        Bmc.check(lo.length() == n);   // length preserved: a nondet-length stub could refute this
        for (int i = 0; i < lo.length(); i++) {
            char r = lo.charAt(i);
            Bmc.check(r == (char) (data[i] + 32));   // exact ASCII fold (a nondet read could not)
        }
    }

    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCase_concrete_idempotent() {
        // toLowerCase(toLowerCase(s)) == toLowerCase(s): a second fold is a no-op on the handled domain.
        String lo = new String(new char[]{'A', 'b', 'C'}).toLowerCase();
        Bmc.check(lo.toLowerCase().equals(lo));
    }

    // The loud boundary: a non-ASCII char (capital Greek sigma, U+03A3) is outside the model's precise
    // ASCII domain - and genuinely has a context-dependent lowercase a char map cannot reproduce - so the
    // model traps it to UNKNOWN (NOT a wrong VERIFIED). Pins the "loud, not silent" discipline: a
    // representative unsafe char goes undecided, never wrong.
    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void toLowerCase_nonAscii_char_is_loud_not_wrong() {
        String s = new String(new char[]{'\u03A3'}).toLowerCase();   // sentinel to UNKNOWN
        Bmc.check(s.length() == 1);   // never conclusively reached
    }

    // 'I' is ASCII but locale-sensitive (Turkish dotless-i), so it too is trapped -> UNKNOWN, never wrong.
    @BmcProof(unwind = 4, stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void toLowerCase_capitalI_is_loud_not_wrong() {
        String s = new String(new char[]{'I'}).toLowerCase();   // sentinel to UNKNOWN
        Bmc.check(s.charAt(0) == 'i');   // never conclusively reached
    }

    // NOTE on symbolic strings under no-refine: a whole-string SYMBOLIC equals (anyChar-filled array
    // then s.equals(s)) is sound but very heavy here - with no refinement to compress the array
    // reasoning it can take minutes, so it is deliberately NOT a suite proof. The
    // symbolicViaConstruction_lengthAndCharsAgree proof above is the symbolic evidence (length + per-index
    // reads agree with the source array); whole-string symbolic equals stays on the refinement axis.
}
