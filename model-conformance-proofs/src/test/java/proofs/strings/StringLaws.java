package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Conformance proofs for the String-method coverage map (docs/coverage.md).
 *
 * <p>Written in Java so each call binds to the real {@code java.lang.String} /
 * {@code java.lang.StringBuilder} method (Kotlin's {@code split}/{@code chars} route through
 * kotlin-stdlib and would not exercise the JDK method).
 *
 * <p>Soundness rule: when the operation under test is itself a String op, we assert with
 * {@code length()} + per-index {@code charAt}, which JBMC's string-refinement solver models soundly
 * (the same primitives {@code BmcStrings} is built on) — NOT via whole-string {@code equals}, which
 * is shimmed. Each proof pins BOTH directions of the result (a true-positive AND a true-negative, or
 * an exact value), so a nondet / unconstrained model could not satisfy it: a green SYMBOLIC or
 * concrete-differential proof is therefore the conformance record that the native op is sound.
 *
 * <h2>String coverage map (probed 2026-06)</h2>
 * <ul>
 *   <li>{@code substring(int)} / {@code substring(int,int)} — SOUND (native). Concrete + symbolic.</li>
 *   <li>{@code replace(char,char)} — SOUND (native). Concrete + symbolic.</li>
 *   <li>{@code isEmpty()} — SOUND (native): tracks {@code length()==0} in both directions.</li>
 *   <li>{@code equalsIgnoreCase(String)} — SOUND (native): true/false pinned concretely, reflexive
 *       symbolically. <b>ASCII caveat:</b> these proofs exercise ASCII case folding (the common case);
 *       JBMC models the JDK method, so the full Unicode/locale case-fold is whatever the JDK does —
 *       not a bmc4j-narrowed ASCII-only stand-in. No shim, so no ASCII bound to document on a shim.</li>
 *   <li>{@code compareTo(String)} — SOUND (native): sign pinned concretely; reflexive and
 *       first-char lexicographic ordering pinned symbolically.</li>
 *   <li>{@code indexOf(String)} / {@code indexOf(int)} / {@code lastIndexOf(int)} — SOUND (native):
 *       exact hit position and {@code -1} miss pinned (indexOf was already flagged native-sound).</li>
 *   <li>{@code split(...)} — UNSOUND (regex-based): a regex-metachar delimiter returns an
 *       UNCONSTRAINED array length. NOT shipped / NOT modeled.</li>
 *   <li>{@code chars()} — UNSOUND: returns an unconstrained IntStream. NOT shipped / NOT modeled.</li>
 * </ul>
 *
 * <p>Because every method above verifies natively, this needed NO new {@code BmcStrings} shims
 * or {@code StringBytecode} redirects — only these conformance pins, so a future engine/model bump
 * that silently breaks one of them turns this suite red.
 */
class StringLaws {

    // ---- substring(int) ----------------------------------------------------

    @BmcProof
    void substring_from_concrete() {
        String s = "hello".substring(1);
        Bmc.check(s.length() == 4);
        Bmc.check(s.charAt(0) == 'e' && s.charAt(1) == 'l' && s.charAt(2) == 'l' && s.charAt(3) == 'o');
    }

    @BmcProof
    void substring_from_symbolic() {
        String s = Bmc.anyString(4);
        int begin = Bmc.anyInt(0, s.length());
        String sub = s.substring(begin);
        Bmc.check(sub.length() == s.length() - begin);
        if (sub.length() > 0) {
            Bmc.check(sub.charAt(0) == s.charAt(begin));
        }
    }

    // ---- substring(int, int) ----------------------------------------------

    @BmcProof
    void substring_range_concrete() {
        String s = "hello".substring(1, 3);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == 'e' && s.charAt(1) == 'l');
    }

    @BmcProof
    void substring_range_symbolic() {
        String s = Bmc.anyString(4);
        int begin = Bmc.anyInt(0, s.length());
        int end = Bmc.anyInt(begin, s.length());
        String sub = s.substring(begin, end);
        Bmc.check(sub.length() == end - begin);
        if (sub.length() > 0) {
            Bmc.check(sub.charAt(0) == s.charAt(begin));
        }
    }

    // ---- replace(char, char) ----------------------------------------------

    @BmcProof
    void replace_char_concrete() {
        String s = "banana".replace('a', 'o');
        Bmc.check(s.length() == 6);
        Bmc.check(s.charAt(0) == 'b' && s.charAt(1) == 'o' && s.charAt(2) == 'n'
                && s.charAt(3) == 'o' && s.charAt(4) == 'n' && s.charAt(5) == 'o');
    }

    @BmcProof
    void replace_char_symbolic() {
        String s = Bmc.anyString(4);
        String r = s.replace('a', 'o');
        Bmc.check(r.length() == s.length());
        if (s.length() > 0) {
            char c = s.charAt(0);
            char expected = (c == 'a') ? 'o' : c;
            Bmc.check(r.charAt(0) == expected);
        }
    }

    // ---- isEmpty() --------------------------------------------------------

    @BmcProof
    void isEmpty_concrete() {
        Bmc.check("".isEmpty());
        Bmc.check(!"x".isEmpty());
    }

    @BmcProof
    void isEmpty_symbolic_tracks_length() {
        // isEmpty() == (length()==0) in BOTH directions: a nondet result could not satisfy this.
        String s = Bmc.anyString(4);
        Bmc.check(s.isEmpty() == (s.length() == 0));
    }

    // ---- equalsIgnoreCase(String) -----------------------------------------

    @BmcProof
    void equalsIgnoreCase_concrete_true_and_false() {
        Bmc.check("ABC".equalsIgnoreCase("abc"));   // true: a nondet result could be false
        Bmc.check(!"ABC".equalsIgnoreCase("abd"));  // false: a nondet result could be true
    }

    @BmcProof
    void equalsIgnoreCase_symbolic_reflexive() {
        // Reflexive over every bounded string: a nondet equalsIgnoreCase could refute this.
        String s = Bmc.anyString(4);
        Bmc.check(s.equalsIgnoreCase(s));
    }

    // ---- compareTo(String) ------------------------------------------------

    @BmcProof
    void compareTo_concrete_sign() {
        Bmc.check("a".compareTo("a") == 0);
        Bmc.check("a".compareTo("b") < 0);
        Bmc.check("b".compareTo("a") > 0);
    }

    @BmcProof
    void compareTo_symbolic_reflexive() {
        String s = Bmc.anyString(4);
        Bmc.check(s.compareTo(s) == 0);            // reflexive: 0 for every string
    }

    @BmcProof
    void compareTo_symbolic_first_char_orders() {
        // For two single-char strings, compareTo's sign matches the char order — pins the actual
        // lexicographic semantics (a nondet result could not), not just reflexivity.
        char a = Bmc.anyChar();
        char b = Bmc.anyChar();
        Bmc.assume(a >= 'a' && a <= 'z' && b >= 'a' && b <= 'z');
        String sa = "" + a;
        String sb = "" + b;
        int cmp = sa.compareTo(sb);
        if (a < b) {
            Bmc.check(cmp < 0);
        } else if (a > b) {
            Bmc.check(cmp > 0);
        } else {
            Bmc.check(cmp == 0);
        }
    }

    // ---- indexOf / lastIndexOf --------------------------------------------

    @BmcProof
    void indexOf_string_concrete_hit_and_miss() {
        Bmc.check("hello".indexOf("ll") == 2);   // exact position
        Bmc.check("hello".indexOf("z") == -1);   // miss
    }

    @BmcProof
    void indexOf_char_concrete_hit_and_miss() {
        Bmc.check("hello".indexOf('e') == 1);
        Bmc.check("hello".indexOf('z') == -1);
    }

    @BmcProof
    void lastIndexOf_char_concrete_hit_and_miss() {
        Bmc.check("hello".lastIndexOf('l') == 3);
        Bmc.check("hello".lastIndexOf('z') == -1);
    }

    // ---- @NotBlank support: trim() probe + the charAt-loop pin --------------
    //
    // @NotBlank needs "non-null and not all-whitespace". The probe below records whether trim() is
    // sound on the modeled string layer (concrete + symbolic, BOTH directions). The charAt-loop pin
    // is what @NotBlank actually lowers to (length()/charAt are the natively-sound primitives), so it
    // is pinned regardless of the trim outcome.

    @BmcProof
    void trim_concrete_strips_surrounding_whitespace() {
        // Concrete, both directions: a blank string trims empty; a padded one trims to its core.
        Bmc.check("   ".trim().isEmpty());          // all-whitespace -> empty after trim
        Bmc.check(!"  x ".trim().isEmpty());        // has a non-blank char -> non-empty after trim
        Bmc.check("  x ".trim().length() == 1);
    }

    @BmcProof
    void trim_symbolic_blankness_matches_a_charAt_scan() {
        // Symbolic, both directions: trim().isEmpty() must agree with "no char > ' '" over the
        // sound charAt primitive. If trim were nondet/unsound this equivalence could be refuted —
        // a green here is the conformance record that trim() is usable for @NotBlank.
        String s = Bmc.anyString(3);
        boolean anyNonBlank = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > ' ') {
                anyNonBlank = true;
            }
        }
        Bmc.check(s.trim().isEmpty() == !anyNonBlank);
    }

    @BmcProof
    void charAt_loop_detects_blank_vs_non_blank_both_directions() {
        // The exact shape @NotBlank lowers to (Constraints.notBlankCharAtLoop): a bounded existential
        // over charAt for a char > ' '. Concrete in both directions.
        Bmc.check(notBlankByScan("  x"));    // a non-blank char exists
        Bmc.check(!notBlankByScan("   "));   // all whitespace
        Bmc.check(!notBlankByScan(""));      // empty is blank
    }

    private static boolean notBlankByScan(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > ' ') {
                return true;
            }
        }
        return false;
    }
}
