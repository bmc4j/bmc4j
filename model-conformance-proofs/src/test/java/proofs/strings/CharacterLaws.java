package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Conformance pins for the {@code java.lang.Character} classification / case helpers consumer code
 * hits constantly (input validation, parsing, normalization): {@code isDigit}, {@code isLetter},
 * {@code isLetterOrDigit}, {@code isWhitespace}, {@code toUpperCase(char)}, {@code toLowerCase(char)}.
 * These are static {@code char->boolean}/{@code char->char} predicates JBMC models natively (no shim
 * / redirect needed), so this suite is the conformance record that they are sound.
 *
 * <p>Each proof pins BOTH directions concretely (a true-positive AND a true-negative) and, where
 * cheap, a symbolic law over the ASCII band — which a nondet model could not satisfy.
 *
 * <h2>Soundness map (probed 2026-06)</h2>
 * <ul>
 *   <li>{@code isDigit/isLetter/isWhitespace(char)} — SOUND (native).</li>
 *   <li>{@code toUpperCase/toLowerCase(char)} — SOUND (native) over the ASCII band probed here.
 *       The full Unicode case fold is whatever the JDK does — not a bmc4j-narrowed stand-in.</li>
 *   <li><b>{@code isLetterOrDigit(char)} — UNSOUND.</b> Returns an unconstrained boolean (a concrete
 *       {@code 'a'}/{@code '-'} claim refutes), unlike the individually-sound {@code isLetter} /
 *       {@code isDigit}. NOT shipped — compose {@code isLetter(c) || isDigit(c)} (both sound) instead;
 *       documented so the limitation stays visible. Conservatively over-refutes (no false green).</li>
 * </ul>
 */
class CharacterLaws {

    // ---- isDigit ----------------------------------------------------------

    @BmcProof
    void isDigit_concrete_true_and_false() {
        Bmc.check(Character.isDigit('5'));
        Bmc.check(!Character.isDigit('a'));
        Bmc.check(!Character.isDigit(' '));
    }

    @BmcProof
    void isDigit_symbolic_matches_ascii_range() {
        // Over the ASCII band, isDigit is true exactly for '0'..'9' — both directions.
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 127);
        Bmc.check(Character.isDigit(c) == (c >= '0' && c <= '9'));
    }

    // ---- isLetter ---------------------------------------------------------

    @BmcProof
    void isLetter_concrete_true_and_false() {
        Bmc.check(Character.isLetter('a'));
        Bmc.check(Character.isLetter('Z'));
        Bmc.check(!Character.isLetter('5'));
        Bmc.check(!Character.isLetter(' '));
    }

    @BmcProof
    void isLetter_symbolic_matches_ascii_alpha() {
        // Over the ASCII band, isLetter is true exactly for 'A'..'Z' and 'a'..'z'.
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 127);
        boolean ascii = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        Bmc.check(Character.isLetter(c) == ascii);
    }

    // NOTE: Character.isLetterOrDigit(char) is UNSOUND in JBMC (unconstrained result) — NOT pinned.
    // Compose isLetter(c) || isDigit(c) instead (both sound above). See the class-doc soundness map.

    // ---- isWhitespace -----------------------------------------------------

    @BmcProof
    void isWhitespace_concrete_true_and_false() {
        Bmc.check(Character.isWhitespace(' '));
        Bmc.check(Character.isWhitespace('\t'));
        Bmc.check(Character.isWhitespace('\n'));
        Bmc.check(!Character.isWhitespace('a'));
    }

    // ---- toUpperCase / toLowerCase ----------------------------------------

    @BmcProof
    void toUpperCase_concrete() {
        Bmc.check(Character.toUpperCase('a') == 'A');
        Bmc.check(Character.toUpperCase('A') == 'A');   // already upper: unchanged
        Bmc.check(Character.toUpperCase('5') == '5');   // non-letter: unchanged
    }

    @BmcProof
    void toLowerCase_concrete() {
        Bmc.check(Character.toLowerCase('A') == 'a');
        Bmc.check(Character.toLowerCase('a') == 'a');
        Bmc.check(Character.toLowerCase('5') == '5');
    }

    @BmcProof
    void toUpperCase_symbolic_ascii_lower_folds_up() {
        // Every ASCII lowercase letter folds to its uppercase (offset 32): a nondet fold could not.
        char c = Bmc.anyChar();
        Bmc.assume(c >= 'a' && c <= 'z');
        Bmc.check(Character.toUpperCase(c) == (char) (c - 32));
    }

    @BmcProof
    void toLowerCase_symbolic_ascii_upper_folds_down() {
        char c = Bmc.anyChar();
        Bmc.assume(c >= 'A' && c <= 'Z');
        Bmc.check(Character.toLowerCase(c) == (char) (c + 32));
    }
}
