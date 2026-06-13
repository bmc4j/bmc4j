package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;

/**
 * Conformance proofs for the sound char-array-backed String model used under string refinement OFF
 * (--no-refine-strings / StringMode.NONE). See {@code core/bmc-string-model} and
 * {@code BundledStringModel}.
 *
 * <p><b>How they run.</b> Each proof is pinned to {@code @BmcProof(stringMode = StringMode.NONE)} so it
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
 *   <li>A String from {@code Bmc.anyString(...)} / raw {@code nondetWithoutNull()} is not yet sound
 *       under no-refine (the nondet object's backing field is re-havoced across calls); introduce a
 *       symbolic string via {@code new String(symbolicCharArray)} instead. Routing {@code anyString}
 *       through construction under NONE is the companion change owned by the stringMode PR.</li>
 * </ul>
 */
class NoRefineStringModelLaws {

    // ---- new String(char[]) construction + readback (SOUND under no-refine) ----

    @BmcProof(stringMode = StringMode.NONE)
    void newStringFromChars_readback() {
        char[] data = {'a', 'b'};
        String s = new String(data);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == 'a');
        Bmc.check(s.charAt(1) == 'b');
    }

    @BmcProof(stringMode = StringMode.NONE)
    void newStringFromCharsRange_readback() {
        char[] data = {'x', 'a', 'b', 'y'};
        String s = new String(data, 1, 2);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == 'a');
        Bmc.check(s.charAt(1) == 'b');
    }

    // ---- StringBuilder.append(char) + toString() (the bmc4j construction primitive) ----

    @BmcProof(unwind = 4, stringMode = StringMode.NONE)
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

    // Symbolic length 0..1 (kept small: under NONE there is no refinement to compress the symbolic
    // char-array reasoning, so a larger bound times out in CI - 0..1 is enough to pin symbolic readback).
    @BmcProof(unwind = 2, stringMode = StringMode.NONE)
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

    @BmcProof(unwind = 3, stringMode = StringMode.NONE)
    void equals_overConstructedStrings() {
        String a = new String(new char[]{'h', 'i'});
        String b = new String(new char[]{'h', 'i'});
        String c = new String(new char[]{'h', 'o'});
        Bmc.check(a.equals(b));    // equal content -> equal
        Bmc.check(!a.equals(c));   // differing content -> not equal
    }

    // NOTE on symbolic strings under no-refine: a whole-string SYMBOLIC equals (anyChar-filled array
    // then s.equals(s)) is sound but very heavy here - with no refinement to compress the array
    // reasoning it can take minutes, so it is deliberately NOT a suite proof. The
    // symbolicViaConstruction_lengthAndCharsAgree proof above is the symbolic evidence (length + per-index
    // reads agree with the source array); whole-string symbolic equals stays on the refinement axis.
}
