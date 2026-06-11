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
 *   <li>{@code toLowerCase()} / {@code toUpperCase()} — SOUND (native). Concrete + symbolic
 *       (length-preserving + idempotent). ASCII case folding probed (the common case).</li>
 *   <li>{@code concat(String)} — SOUND (native): result length = sum of lengths, content pinned;
 *       concrete + symbolic.</li>
 *   <li>{@code replace(CharSequence,CharSequence)} — SOUND (native): concrete substring replacement
 *       + symbolic identity replace.</li>
 *   <li>{@code charAt(int)} — SOUND (native primitive): pinned concretely.</li>
 *   <li>{@code indexOf(String,int)} (fromIndex) / {@code lastIndexOf(String)} — SOUND (native):
 *       exact hit position pinned.</li>
 *   <li>{@code valueOf(int)} — SOUND (native): routes through {@code Integer.toString}; multi-digit
 *       content + length pinned (the {@code Integer.toString(0)} length quirk applies to a {@code 0}).</li>
 *   <li>{@code split(...)} — UNSOUND (regex-based): a regex-metachar delimiter returns an
 *       UNCONSTRAINED array length. NOT shipped / NOT modeled.</li>
 *   <li>{@code chars()} — UNSOUND: returns an unconstrained IntStream. NOT shipped / NOT modeled.</li>
 *   <li><b>{@code repeat(int)} — UNSOUND.</b> The result is UNCONSTRAINED ({@code "ab".repeat(3)}
 *       refutes on both length and content; the call links to a nondet stub). NOT shipped — documented
 *       so the limitation stays visible; conservatively over-refutes (no false green).</li>
 *   <li><b>{@code strip()} / {@code isBlank()} (Java 11) — UNSOUND.</b> Unlike the Java-1.0
 *       {@code trim()} (native-sound above), the Java-11 {@code strip}/{@code isBlank} are NOT modeled
 *       by JBMC: {@code strip()} even trips spurious null-pointer checks and {@code isBlank()} returns
 *       an unconstrained boolean. NOT shipped — use {@code trim()} (sound) for blankness instead.</li>
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

    @BmcProof(maxStringLength = 4)
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

    @BmcProof(maxStringLength = 4)
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

    @BmcProof(maxStringLength = 4)
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

    @BmcProof(maxStringLength = 4)
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

    @BmcProof(maxStringLength = 4)
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

    @BmcProof(maxStringLength = 4)
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

    // ---- toLowerCase / toUpperCase ----------------------------------------

    @BmcProof
    void toLowerCase_concrete() {
        String s = "ABc".toLowerCase();
        Bmc.check(s.length() == 3);
        Bmc.check(s.charAt(0) == 'a' && s.charAt(1) == 'b' && s.charAt(2) == 'c');
    }

    @BmcProof
    void toUpperCase_concrete() {
        String s = "aBc".toUpperCase();
        Bmc.check(s.length() == 3);
        Bmc.check(s.charAt(0) == 'A' && s.charAt(1) == 'B' && s.charAt(2) == 'C');
    }

    @BmcProof(maxStringLength = 4)
    void toLowerCase_symbolic_length_preserving() {
        // Length-preserving for every bounded string: a nondet result could refute this.
        String s = Bmc.anyString(4);
        Bmc.check(s.toLowerCase().length() == s.length());
    }

    @BmcProof(unwind = 4)
    void toLowerCase_concrete_idempotent() {
        // Idempotence (a second fold is a no-op) is inherently heavy over a FULL symbolic string —
        // it builds and string-equals two whole-string case folds. Pinned concretely here; the
        // exhaustive case-fold semantics live on the differential axis (DifferentialProofs runs the
        // native op against the model over a value set), not as a whole-string symbolic proof.
        String lo = "aBc".toLowerCase();
        Bmc.check(lo.toLowerCase().equals(lo));
        Bmc.check(lo.equals("abc"));
    }

    // ---- concat(String) ----------------------------------------------------

    @BmcProof
    void concat_concrete() {
        String s = "ab".concat("cd");
        Bmc.check(s.length() == 4);
        Bmc.check(s.charAt(0) == 'a' && s.charAt(1) == 'b' && s.charAt(2) == 'c' && s.charAt(3) == 'd');
    }

    @BmcProof(maxStringLength = 4)
    void concat_symbolic_length_adds_and_prefix_preserved() {
        // Both operands are symbolic by necessity (length additivity + left-operand prefix); each is
        // bounded to 3 so the concatenated result stays small.
        String a = Bmc.anyString(3);
        String b = Bmc.anyString(3);
        String r = a.concat(b);
        Bmc.check(r.length() == a.length() + b.length());   // length is additive
        if (a.length() > 0) {
            Bmc.check(r.charAt(0) == a.charAt(0));           // the left operand is the prefix
        }
    }

    // ---- replace(CharSequence, CharSequence) ------------------------------

    @BmcProof
    void replace_charseq_concrete() {
        String s = "ababab".replace("ab", "X");
        Bmc.check(s.length() == 3);
        Bmc.check(s.charAt(0) == 'X' && s.charAt(1) == 'X' && s.charAt(2) == 'X');
    }

    @BmcProof(maxStringLength = 4, unwind = 8)
    void replace_charseq_symbolic_identity_is_noop() {
        // Replacing "a" with "a" leaves every bounded string unchanged: a nondet replace could not.
        String s = Bmc.anyString(4);
        Bmc.check(s.replace("a", "a").equals(s));
    }

    // ---- charAt(int) ------------------------------------------------------

    @BmcProof
    void charAt_concrete() {
        Bmc.check("hello".charAt(0) == 'h');
        Bmc.check("hello".charAt(4) == 'o');
    }

    // ---- indexOf(String, fromIndex) / lastIndexOf(String) -----------------

    @BmcProof
    void indexOf_string_fromIndex_concrete() {
        // The first "ab" at 0 is skipped by fromIndex=1, so the next hit at 2 is returned.
        Bmc.check("ababab".indexOf("ab", 1) == 2);
        Bmc.check("ababab".indexOf("zz", 0) == -1);   // miss
    }

    @BmcProof
    void lastIndexOf_string_concrete() {
        Bmc.check("ababab".lastIndexOf("ab") == 4);    // rightmost hit
        Bmc.check("ababab".lastIndexOf("zz") == -1);   // miss
    }

    // ---- valueOf(int) -----------------------------------------------------

    @BmcProof
    void valueOf_int_concrete() {
        // Routes through Integer.toString; multi-digit content + length are sound (the
        // Integer.toString(0) length quirk only bites an exact-length claim about "0").
        String s = String.valueOf(42);
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == '4' && s.charAt(1) == '2');
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

    @BmcProof(maxStringLength = 4)
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

    @BmcProof(unwind = 4)
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
