package kotlin.text;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean model of Kotlin's {@code CharsKt} facade ({@code kotlin.text} char functions) — the NON-INLINE
 * residue of the {@code Char} extensions.
 *
 * <p>The everyday char predicates a proof reaches — {@code isDigit}/{@code isLetter}/{@code
 * isLetterOrDigit}/{@code isWhitespace()}/{@code isUpperCase}/{@code isLowerCase}/{@code
 * digitToInt()}/… — are {@code @InlineOnly}: they inline into the caller and the inlined body calls
 * {@link java.lang.Character} directly (which JBMC analyzes soundly). Those have NO JVM method on
 * {@code CharsKt} and need no model. What remains as a real {@code CharsKt} JVM member is this
 * non-inline residue.
 *
 * <p>Modeled (sound, delegating to {@link java.lang.Character}): {@code digitToInt}/{@code
 * digitToIntOrNull}/{@code digitToChar} (with and without an explicit radix), {@code isWhitespace}
 * (the {@code Char.isWhitespace()} property reached non-inline — Kotlin treats both Java whitespace and
 * space-char as whitespace), {@code isSurrogate}, {@code equals(Char,Char,boolean)} (the
 * ignore-case char equality), {@code checkRadix}, and the internal {@code digitOf} helper.
 *
 * <p>Waived loud ({@code @BmcUnmodelable}): the Unicode-table / locale-sensitive members —
 * {@code getCategory} (the {@code CharCategory} classification), {@code getDirectionality} (the
 * bidi {@code CharDirectionality}), and the locale-parameterized {@code lowercase}/{@code
 * uppercase}/{@code titlecase} — return rich Unicode/ICU-backed values whose faithful bounded model
 * earns nothing for a BMC proof; loud-if-reached rather than a fiction.
 */
public final class CharsKt {

    private CharsKt() {
    }

    // ---- digitToInt(char) / digitToInt(char, radix): the value of this char as a digit in the radix
    //   (default 10), throwing IllegalArgumentException if it is not a valid digit. Character.digit
    //   returns -1 for a non-digit; we convert that to the Kotlin throw.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int digitToInt(char ch) {
        return digitToInt(ch, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int digitToInt(char ch, int radix) {
        checkRadix(radix);
        int d = digitOf(ch, radix);
        if (d < 0) {
            throw new IllegalArgumentException("Char " + ch + " is not a digit in the given radix=" + radix);
        }
        return d;
    }

    // ---- digitToIntOrNull(char) / digitToIntOrNull(char, radix): the *OrNull twin — null instead of
    //   throwing on a non-digit. Returns a boxed Integer (Kotlin's Int?).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer digitToIntOrNull(char ch) {
        return digitToIntOrNull(ch, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer digitToIntOrNull(char ch, int radix) {
        checkRadix(radix);
        int d = digitOf(ch, radix);
        return d < 0 ? null : Integer.valueOf(d);
    }

    // ---- digitToChar(int) / digitToChar(int, radix): the char representation of this digit value in the
    //   radix (default 10), throwing if out of range. Character.forDigit returns '\0' for an invalid
    //   value; convert that to the Kotlin throw, and uppercase the letter digits to match Kotlin.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char digitToChar(int digit) {
        return digitToChar(digit, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char digitToChar(int digit, int radix) {
        checkRadix(radix);
        if (digit < 0 || digit >= radix) {
            throw new IllegalArgumentException("Digit " + digit + " does not represent a valid digit in radix " + radix);
        }
        // Computed directly (not Character.forDigit): 0..9 -> '0'..'9', 10..35 -> 'A'..'Z' (Kotlin
        // uppercases the letter digits). Avoids the JBMC-nondet java.lang.Character.forDigit.
        if (digit < 10) {
            return (char) ('0' + digit);
        }
        return (char) ('A' + (digit - 10));
    }

    // ---- digitOf(char, radix): the INTERNAL kotlin-stdlib helper digitToInt/digitToIntOrNull use — the
    //   value of the char in the radix, or -1 if not a valid digit (no throw). Computed DIRECTLY over the
    //   ASCII/Latin digit ranges rather than via java.lang.Character.digit (which JBMC 6.9.0 nondet-stubs,
    //   silently havocking the digit value): '0'-'9' -> 0..9, 'a'-'z'/'A'-'Z' -> 10..35, anything else or a
    //   value >= radix -> -1. (Matches Character.digit for the ASCII/Latin letter-digit range, which is the
    //   entire range a radix <= 36 can represent.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int digitOf(char ch, int radix) {
        int value;
        if (ch >= '0' && ch <= '9') {
            value = ch - '0';
        } else if (ch >= 'a' && ch <= 'z') {
            value = ch - 'a' + 10;
        } else if (ch >= 'A' && ch <= 'Z') {
            value = ch - 'A' + 10;
        } else {
            return -1;
        }
        return value < radix ? value : -1;
    }

    // ---- checkRadix(radix): the INTERNAL helper that validates 2 <= radix <= 36, returning the radix or
    //   throwing IllegalArgumentException. Used by the digit ops above.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int checkRadix(int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new IllegalArgumentException("radix " + radix + " was not in valid range " + Character.MIN_RADIX + ".." + Character.MAX_RADIX);
        }
        return radix;
    }

    // ---- isWhitespace(): Kotlin treats a char as whitespace if it is Java whitespace OR a Unicode space
    //   char. Both java.lang.Character predicates are sound under JBMC. (The property getter is non-inline
    //   on the JVM, hence reached on this facade.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
    }

    // ---- isSurrogate(): whether the char is a UTF-16 surrogate code unit. Character.isSurrogate is exact.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean isSurrogate(char ch) {
        return Character.isSurrogate(ch);
    }

    // ---- equals(Char, Char, ignoreCase): char equality, case-insensitively when ignoreCase is true.
    //   Kotlin's contract: when ignoreCase, compare uppercased, and if still unequal, lowercased (the
    //   Turkish-i style fixup). Both Character.toUpperCase/toLowerCase are sound primitives.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean equals(char a, char b, boolean ignoreCase) {
        if (a == b) {
            return true;
        }
        if (!ignoreCase) {
            return false;
        }
        if (Character.toUpperCase(a) == Character.toUpperCase(b)) {
            return true;
        }
        return Character.toLowerCase(a) == Character.toLowerCase(b);
    }

    // ---- getCategory / getDirectionality: Unicode-table classification (CharCategory / the bidi
    //   CharDirectionality). Rich Unicode-property values whose faithful bounded model earns nothing for a
    //   BMC proof, and they return stdlib enum types over Character.getType/getDirectionality tables.
    //   Loud-if-reached.
    @BmcUnmodelable(reason = "Char.category returns the Unicode CharCategory classification over "
            + "Character.getType's Unicode tables — a rich Unicode-property enum whose faithful bounded model "
            + "earns nothing for a BMC proof; loud-if-reached")
    public static CharCategory getCategory(char ch) {
        throw fail("bmc4j: unmodelled member kotlin.text.CharsKt.getCategory(char) — Unicode CharCategory classification");
    }

    @BmcUnmodelable(reason = "Char.directionality returns the Unicode bidi CharDirectionality over "
            + "Character.getDirectionality's Unicode tables — a rich Unicode-property enum whose faithful "
            + "bounded model earns nothing for a BMC proof; loud-if-reached")
    public static CharDirectionality getDirectionality(char ch) {
        throw fail("bmc4j: unmodelled member kotlin.text.CharsKt.getDirectionality(char) — Unicode CharDirectionality classification");
    }

    // ---- lowercase / uppercase / titlecase (Locale): locale-sensitive case mapping that the stdlib
    //   routes through java.lang.String.toLowerCase(Locale)/toUpperCase(Locale) (so a single char can map
    //   to MULTIPLE chars, e.g. the German sharp s) — a String-returning, locale/ICU-backed transform out
    //   of scope for a bounded char model. Loud-if-reached. (The no-Locale isUpperCase()/uppercaseChar
    //   forms are @InlineOnly → java.lang.Character and never reach this facade.)
    @BmcUnmodelable(reason = "Char.lowercase(Locale) is locale-sensitive case mapping the stdlib routes "
            + "through String.toLowerCase(Locale) (a char may map to multiple chars) — a String-returning, "
            + "locale/ICU-backed transform out of scope for a bounded char model; loud-if-reached")
    public static String lowercase(char ch, java.util.Locale locale) {
        throw fail("bmc4j: unmodelled member kotlin.text.CharsKt.lowercase(char,java.util.Locale) — locale-sensitive case mapping (String-returning)");
    }

    @BmcUnmodelable(reason = "Char.uppercase(Locale) is locale-sensitive case mapping the stdlib routes "
            + "through String.toUpperCase(Locale) (a char may map to multiple chars) — a String-returning, "
            + "locale/ICU-backed transform out of scope for a bounded char model; loud-if-reached")
    public static String uppercase(char ch, java.util.Locale locale) {
        throw fail("bmc4j: unmodelled member kotlin.text.CharsKt.uppercase(char,java.util.Locale) — locale-sensitive case mapping (String-returning)");
    }

    @BmcUnmodelable(reason = "Char.titlecase(Locale) is locale-sensitive titlecase mapping over the Unicode "
            + "titlecase tables (a char may map to multiple chars) — a String-returning, locale/ICU-backed "
            + "transform out of scope for a bounded char model; loud-if-reached")
    public static String titlecase(char ch, java.util.Locale locale) {
        throw fail("bmc4j: unmodelled member kotlin.text.CharsKt.titlecase(char,java.util.Locale) — locale-sensitive titlecase mapping (String-returning)");
    }

    @BmcUnmodelable(reason = "Char.titlecase() (default-locale) is Unicode titlecase mapping over the Unicode "
            + "titlecase tables (a char may map to multiple chars) — a String-returning transform out of scope "
            + "for a bounded char model; loud-if-reached")
    public static String titlecase(char ch) {
        throw fail("bmc4j: unmodelled member kotlin.text.CharsKt.titlecase(char) — Unicode titlecase mapping (String-returning)");
    }
}
