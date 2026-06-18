package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;
import org.bmc4j.Verdict;

/**
 * Conformance proofs for the sound {@code java.lang.Character} model
 * ({@code core/bmc-models/src/main/java/java/lang/Character.java}) under string refinement OFF
 * ({@code StringMode.CHAR_ARRAY_MODEL} / {@code --no-refine-strings}).
 *
 * <p><b>Why these are pinned to {@code CHAR_ARRAY_MODEL}.</b> Under refinement JBMC intercepts
 * {@code Character} with its own intrinsics (the existing {@link CharacterLaws} suite pins that path).
 * Under no-refine there is no intrinsic, so the call links the engine's core-models {@code Character},
 * whose {@code CharacterData} case/property table is ABSENT - every {@code isX}/{@code toX} dereferences
 * a nondet {@code CharacterData.of(cp)} and NPEs. This suite proves the bmc4j model makes those calls
 * SOUND there.
 *
 * <p>Each proof pins a direction a nondet model could not satisfy: concrete agreement-with-JDK on the
 * precise domain, a symbolic exact-fold / exact-classification over the safe band, idempotence where it
 * holds, and the loud boundary (a code point outside the precise domain goes to {@code UNKNOWN},
 * never a wrong VERIFIED) pinned {@code expect = UNKNOWN}.
 *
 * <h2>Precise-vs-loud boundary (probed differentially vs the JDK, 2026-06)</h2>
 * <ul>
 *   <li>{@code toLowerCase}/{@code toUpperCase}: EXACT over ASCII + the Latin-1 supplement
 *       ({@code 0x00..0xFF}); LOUD for {@code >= 0x100}. (No Turkish-{@code 'I'} trap is needed - the
 *       {@code Character} case API is locale-free, unlike {@code String.toLowerCase(Locale)}.)</li>
 *   <li>classification + {@code digit}/{@code getNumericValue}: EXACT over ASCII ({@code 0x00..0x7F});
 *       LOUD for {@code >= 0x80}.</li>
 * </ul>
 */
class CharacterModelLaws {

    // ===== toLowerCase / toUpperCase (EXACT over ASCII + Latin-1; LOUD beyond) =====================

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCase_concrete() {
        Bmc.check(Character.toLowerCase('A') == 'a');
        Bmc.check(Character.toLowerCase('a') == 'a');   // already lower: unchanged
        Bmc.check(Character.toLowerCase('5') == '5');   // non-letter: unchanged
        Bmc.check(Character.toLowerCase((char) 0x00C0) == (char) 0x00E0);   // Latin-1 A-grave -> a-grave
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toUpperCase_concrete() {
        Bmc.check(Character.toUpperCase('a') == 'A');
        Bmc.check(Character.toUpperCase('A') == 'A');
        Bmc.check(Character.toUpperCase('5') == '5');
        Bmc.check(Character.toUpperCase((char) 0x00E0) == (char) 0x00C0);   // Latin-1 a-grave -> A-grave
        Bmc.check(Character.toUpperCase((char) 0x00DF) == (char) 0x00DF);   // sharp-s stays (simple mapping)
        Bmc.check(Character.toUpperCase((char) 0x00B5) == (char) 0x039C);   // MICRO SIGN -> GREEK CAPITAL MU (non-arithmetic)
        Bmc.check(Character.toUpperCase((char) 0x00FF) == (char) 0x0178);   // y-diaeresis -> Y-diaeresis (non-arithmetic)
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCase_symbolic_ascii_upper_folds_down() {
        // Every ASCII uppercase letter folds to lowercase (+32): a nondet fold could not satisfy this.
        char c = Bmc.anyChar();
        Bmc.assume(c >= 'A' && c <= 'Z');
        Bmc.check(Character.toLowerCase(c) == (char) (c + 32));
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toUpperCase_symbolic_ascii_lower_folds_up() {
        char c = Bmc.anyChar();
        Bmc.assume(c >= 'a' && c <= 'z');
        Bmc.check(Character.toUpperCase(c) == (char) (c - 32));
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void toLowerCase_symbolic_idempotent_over_ascii() {
        // toLowerCase is idempotent: a second fold is a no-op over the whole ASCII band.
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 0x7F);
        char lo = Character.toLowerCase(c);
        Bmc.check(Character.toLowerCase(lo) == lo);
    }

    // ----- loud boundary: a code point >= 0x100 cannot be folded soundly -> UNKNOWN, never wrong ----

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void toLowerCase_beyond_latin1_is_loud_not_wrong() {
        // U+0130 (dotted capital I) lowercases to an EXPANDING sequence in the JDK - unmodelable here.
        char r = Character.toLowerCase((char) 0x0130);
        Bmc.check(r == (char) 0x0130);   // never conclusively reached (the model traps loudly first)
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void toUpperCase_beyond_latin1_is_loud_not_wrong() {
        char r = Character.toUpperCase((char) 0x0101);   // a-macron -> A-macron needs the Unicode table
        Bmc.check(r == (char) 0x0101);
    }

    // ===== classification predicates (EXACT over ASCII; LOUD beyond) ===============================

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void isDigit_concrete_and_symbolic() {
        Bmc.check(Character.isDigit('5'));
        Bmc.check(!Character.isDigit('a'));
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 0x7F);
        Bmc.check(Character.isDigit(c) == (c >= '0' && c <= '9'));
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void isLetter_concrete_and_symbolic() {
        Bmc.check(Character.isLetter('a'));
        Bmc.check(Character.isLetter('Z'));
        Bmc.check(!Character.isLetter('5'));
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 0x7F);
        boolean ascii = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        Bmc.check(Character.isLetter(c) == ascii);
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void isLetterOrDigit_symbolic_matches_isLetter_or_isDigit() {
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 0x7F);
        Bmc.check(Character.isLetterOrDigit(c) == (Character.isLetter(c) || Character.isDigit(c)));
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void isWhitespace_concrete() {
        Bmc.check(Character.isWhitespace(' '));
        Bmc.check(Character.isWhitespace('\t'));
        Bmc.check(Character.isWhitespace('\n'));
        Bmc.check(Character.isWhitespace('\r'));
        Bmc.check(!Character.isWhitespace('a'));
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void isSpaceChar_concrete() {
        Bmc.check(Character.isSpaceChar(' '));
        Bmc.check(!Character.isSpaceChar('\t'));   // TAB is whitespace but NOT a space char
        Bmc.check(!Character.isSpaceChar('a'));
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void isUpperCase_isLowerCase_symbolic() {
        char c = Bmc.anyChar();
        Bmc.assume(c >= 0 && c <= 0x7F);
        Bmc.check(Character.isUpperCase(c) == (c >= 'A' && c <= 'Z'));
        Bmc.check(Character.isLowerCase(c) == (c >= 'a' && c <= 'z'));
    }

    // ----- loud boundary: a non-ASCII code point -> UNKNOWN, never a silently wrong classification --

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void isLetter_nonAscii_is_loud_not_wrong() {
        boolean r = Character.isLetter((char) 0x00C0);   // A-grave IS a letter, but Latin-1 is beyond the band
        Bmc.check(r);   // never conclusively reached
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void isDigit_nonAscii_is_loud_not_wrong() {
        boolean r = Character.isDigit((char) 0x0660);   // ARABIC-INDIC ZERO is a digit, beyond ASCII
        Bmc.check(r);
    }

    // ===== digit(char,int) / getNumericValue (EXACT over ASCII; LOUD beyond) =======================

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void digit_concrete() {
        Bmc.check(Character.digit('7', 10) == 7);
        Bmc.check(Character.digit('a', 16) == 10);
        Bmc.check(Character.digit('f', 16) == 15);
        Bmc.check(Character.digit('g', 16) == -1);   // out of radix
        Bmc.check(Character.digit('9', 8) == -1);    // '9' not a valid octal digit
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void digit_symbolic_decimal_over_ascii_digits() {
        char c = Bmc.anyChar();
        Bmc.assume(c >= '0' && c <= '9');
        Bmc.check(Character.digit(c, 10) == c - '0');
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void getNumericValue_concrete() {
        Bmc.check(Character.getNumericValue('0') == 0);
        Bmc.check(Character.getNumericValue('9') == 9);
        Bmc.check(Character.getNumericValue('a') == 10);
        Bmc.check(Character.getNumericValue('Z') == 35);
        Bmc.check(Character.getNumericValue('-') == -1);
    }

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL, expect = Verdict.UNKNOWN)
    void digit_nonAscii_is_loud_not_wrong() {
        int r = Character.digit((char) 0x0660, 10);   // ARABIC-INDIC ZERO digit value, beyond ASCII
        Bmc.check(r == 0);
    }

    // ===== compare (total, exact over the whole char range) ========================================

    @BmcProof(stringMode = StringMode.CHAR_ARRAY_MODEL)
    void compare_symbolic_sign_matches_unsigned_order() {
        char x = Bmc.anyChar();
        char y = Bmc.anyChar();
        int c = Character.compare(x, y);
        Bmc.check((c < 0) == (x < y));   // unsigned char order: a nondet result could not satisfy this
        Bmc.check((c == 0) == (x == y));
        Bmc.check((c > 0) == (x > y));
    }
}
