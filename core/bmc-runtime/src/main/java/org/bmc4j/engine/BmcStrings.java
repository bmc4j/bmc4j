package org.bmc4j.engine;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.cprover.CProver;
import org.cprover.CProverString;

/**
 * Sound replacements for {@code java.lang.String} content operations that JBMC's own models get
 * wrong (e.g. {@code equals} returns an unconstrained boolean — it can't even prove
 * {@code "x".equals("x")}). We rebuild them from primitives JBMC <em>does</em> handle soundly:
 * {@code String.length()} and {@code CProverString.charAt}. {@code StringBytecode} redirects the
 * matching {@code String} call sites here during analysis, so ordinary code that calls
 * {@code s.equals(t)} becomes provable with no engine fork.
 *
 * <p>Bounded by design: the character loops unwind to the string length, so this is sound and
 * cheap for bounded-length strings (introduce them with {@link org.bmc4j.Bmc#anyString(int)}),
 * and needs a larger {@code unwind} for long ones.
 */
public final class BmcStrings {

    private BmcStrings() {
    }

    /**
     * Sound introduction of a SYMBOLIC {@code String} whose length is bounded by {@code maxLength},
     * for string refinement OFF ({@link org.bmc4j.StringMode#CHAR_ARRAY_MODEL}). {@code StringLengthBytecode}
     * redirects the symbolic-string introduction sites here under CHAR_ARRAY_MODEL: the {@code Bmc.anyString} /
     * {@code anyAsciiString} helper bodies (bound = the helper's own {@code maxLength} parameter, so a
     * per-call bound is honored as-is) and bare {@code CProver.nondetWithoutNull()} String sites (bound
     * = the run's global {@code maxStringLength}).
     *
     * <p>Under CHAR_ARRAY_MODEL a bare {@code nondetWithoutNull()} String has a char-array backing JBMC re-havocs
     * across reads, so an {@code assume(length <= n)} pinned only one read and a later read could
     * exceed {@code n} (the length bound was effectively dropped). Building the string from a real
     * char-array of nondet length {@code 0..maxLength} gives it a STABLE backing (see the char-array
     * String model): {@code length()} and every {@code charAt} read the same array, so the bound binds
     * and any per-call {@code assume} (min length, alphabet, ASCII range) the helper adds on top
     * refines that same backing. SOUND: a fresh nondet string over every value of length
     * {@code 0..maxLength}, never a narrowing.
     *
     * <p>Bounded by design: the fill loop unwinds to {@code maxLength}, so size the proof's
     * {@code unwind} to cover it (the same budget rule {@code anyString} already documents). A negative
     * {@code maxLength} is treated as 0 (the empty string), matching the helpers' own validation that
     * rejects a negative bound before this point.
     */
    public static String anyCharBacked(int maxLength) {
        int bound = maxLength < 0 ? 0 : maxLength;
        int n = CProver.nondetInt();
        // Split into atomic bounds, not one `&&`: JBMC prunes dead branches off each atomic assume but
        // not off the conjunction (see Bmc.anyInt(int, int)).
        CProver.assume(n >= 0);
        CProver.assume(n <= bound);
        char[] data = new char[n];
        for (int i = 0; i < n; i++) {
            data[i] = CProver.nondetChar();
        }
        // new String(char[]) here resolves to the char-array String model's constructor (NOT redirected
        // back through StringBytecode, which excludes BmcStrings as an owner), so the backing is the
        // exact array: a real, stable char[] of bounded length.
        return new String(data);
    }

    /**
     * Sound materialization of a {@code String} from char data. JBMC links its native construction
     * path ({@code new String(char[])} / {@code String.valueOf(char[])}, which lower to
     * {@code CProverString.ofCharArray}) to a nondet string — the construction analogue of the unsound
     * native {@code String.equals}. We rebuild it from the one construction primitive JBMC <em>does</em>
     * model soundly: {@code StringBuilder.append(char)} + {@code toString()} (the same machinery the
     * {@code StringConcatFactory} desugar and the {@code CharArray.concatToString()} model already rely
     * on). The resulting String's {@code length()}/{@code charAt} then agree with the source chars, so it
     * composes with the sound {@link #equals}/{@link #contains}/etc. above.
     *
     * <p>{@code StringBytecode} redirects the construction call sites here during analysis. Bounded by
     * design: the append loop unwinds to {@code count}, so it is sound and cheap for bounded char arrays.
     */
    public static String ofChars(char[] data, int offset, int count) {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || count < 0 || offset + count > data.length) {
            throw new StringIndexOutOfBoundsException();   // matches String(char[],int,int) bounds checking
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(data[offset + i]);
        }
        return sb.toString();
    }

    /**
     * Sound stand-in for {@code new String(char[])} / {@code String.valueOf(char[])} (whole array).
     *
     * <p>Rebuilds via the {@code (data, offset, count)} append path. A loop-free {@code new String(data)}
     * (the char-array String model's copying constructor) was tried but reverted: it routes through the
     * model's {@code data.clone()}, which the no-refinement char-array engine explores far more expensively
     * than the bounded {@code StringBuilder.append} rebuild for the {@code new String(char[])} sites the
     * conformance suite exercises - a length-2 string needed cap ~48 and minutes, vs a few seconds here.
     */
    public static String ofChars(char[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        return ofChars(data, 0, data.length);
    }

    /**
     * Loop-free construction for a FIXED STRING LITERAL (its chars are concrete and it is never read back
     * char-by-char): the char-array String model's copying constructor in one shot. {@code StringBytecode}
     * sends user {@code new String(char[])} sites to {@link #ofChars(char[])} (the StringBuilder rebuild,
     * which the no-refinement engine explores far more cheaply for read-back); the literal-pinning pass
     * ({@code StringLengthBytecode}) sends string LITERALS here instead, so a literal LONGER than the
     * unwind bound does not cost a per-char {@code StringBuilder.append} unwind. The literal's length is
     * already concrete (the pass unrolls the char[] build), so this stays sound.
     */
    public static String ofCharsLiteral(char[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        return new String(data);
    }

    /** Sound stand-in for {@code String.valueOf(char)} / {@code Character.toString(char)} (single char). */
    public static String ofChar(char c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c);
        return sb.toString();
    }

    /** Sound stand-in for {@code receiver.equals(other)} where the receiver is a String. */
    public static boolean equals(String receiver, Object other) {
        if (receiver == null) {
            throw new NullPointerException();   // matches receiver.equals(...) on a null receiver
        }
        if (!(other instanceof String)) {
            return false;                        // String.equals(non-String) is false
        }
        String s = (String) other;
        int n = receiver.length();
        if (n != s.length()) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (CProverString.charAt(receiver, i) != CProverString.charAt(s, i)) {
                return false;
            }
        }
        return true;
    }

    /** Sound stand-in for {@code receiver.startsWith(prefix)}. */
    public static boolean startsWith(String receiver, String prefix) {
        if (receiver == null || prefix == null) {
            throw new NullPointerException();    // null receiver, or String.startsWith(null), both NPE
        }
        int p = prefix.length();
        if (p > receiver.length()) {
            return false;
        }
        for (int i = 0; i < p; i++) {
            if (CProverString.charAt(receiver, i) != CProverString.charAt(prefix, i)) {
                return false;
            }
        }
        return true;
    }

    /** Sound stand-in for {@code receiver.endsWith(suffix)}. */
    public static boolean endsWith(String receiver, String suffix) {
        if (receiver == null || suffix == null) {
            throw new NullPointerException();
        }
        int s = suffix.length();
        int offset = receiver.length() - s;
        if (offset < 0) {
            return false;
        }
        for (int i = 0; i < s; i++) {
            if (CProverString.charAt(receiver, offset + i) != CProverString.charAt(suffix, i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Null-safe object equality with {@link java.util.Objects#equals} semantics, but routing the
     * String/String case through the sound {@link #equals} above. Used by the record-{@code equals}
     * desugaring ({@code StringBytecode}) so a record with String components stays sound.
     */
    public static boolean objEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof String && b instanceof String) {
            return equals((String) a, b);
        }
        return a.equals(b);
    }

    /**
     * Sound stand-in for {@code receiver.hashCode()} on a String: the JDK polynomial
     * {@code h = 31*h + charAt(i)} rebuilt from {@code length()} + {@code charAt} (which JBMC models
     * soundly), instead of JBMC's own {@code String.hashCode} which links to an unconstrained int.
     * Used by {@link #objHashCode} so a record with String components hashes deterministically.
     */
    public static int hashCode(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        int h = 0;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            h = 31 * h + CProverString.charAt(s, i);
        }
        return h;
    }

    /**
     * Null-safe object hashCode with {@link java.util.Objects#hashCode} semantics, but routing the
     * String case through the sound {@link #hashCode(String)} above. Used by the record-{@code
     * hashCode} desugaring ({@code StringBytecode}) so reference components hash deterministically:
     * {@code null} → 0, String → content hash, any other reference → its own {@code hashCode()}.
     */
    public static int objHashCode(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof String) {
            return hashCode((String) o);
        }
        return o.hashCode();
    }

    /**
     * Sound stand-in for {@code receiver.contains(needle)}. The redirected call-site descriptor is
     * {@code (CharSequence)Z}, so the needle is typed {@code CharSequence} even though the overwhelming
     * common case is a String literal/variable.
     *
     * <p><b>String needle (the common case):</b> the sound char-by-char search below, rebuilt from
     * {@code length()} + {@code CProverString.charAt} which JBMC models soundly.
     *
     * <p><b>Non-String {@code CharSequence} needle (e.g. a {@code StringBuilder}):</b> we must NOT
     * {@code (String) needle} — that throws a {@link ClassCastException} <em>inside our own shim</em>,
     * which surfaces as a spurious counterexample pointing at bmc4j (the most trust-eroding
     * failure shape). Instead we degrade gracefully: route the needle through {@code toString()} (which
     * JBMC has no body for on an arbitrary {@code CharSequence} and so analyses as a nondet stub) and
     * run the same sound loop over the resulting nondet string. The verdict is then conservatively
     * nondet — the original non-crashing behaviour — and the {@code CharSequence.toString} stub is
     * harvested and surfaced through the stub-footnote mechanism, so the user is told the
     * non-String-needle path was havoc'd rather than it crashing or silently passing.
     */
    public static boolean contains(String receiver, CharSequence needle) {
        if (receiver == null || needle == null) {
            throw new NullPointerException();
        }
        String n;
        if (needle instanceof String) {
            n = (String) needle;                 // sound path: String needle (the common case)
        } else {
            // Non-String CharSequence: degrade to nondet via a stubbed toString(), surfaced as a stub-detection
            // footnote, instead of a spurious CCE on a hard cast.
            n = needle.toString();
        }
        int len = n.length();
        int last = receiver.length() - len;
        if (last < 0) {
            return false;
        }
        for (int start = 0; start <= last; start++) {
            boolean match = true;
            for (int j = 0; j < len; j++) {
                if (CProverString.charAt(receiver, start + j) != CProverString.charAt(n, j)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    // === byte[] -> String charset decode (Rung 1) ===========================================
    //
    // JBMC links `new String(byte[], Charset)` / `new String(byte[], String)` (a charset-decoding
    // library's `bytes -> String` accessor boils down to exactly that ctor) to a nondet native decode
    // path, so a String decoded from symbolic bytes otherwise has nondet length()/charAt — the same
    // engine blindspot as native String construction. `StringBytecode` redirects the byte[] String
    // constructors here. We decode
    // soundly in plain bytecode for the charsets whose byte->char relation is unambiguous and bounded:
    //
    //   * US-ASCII / ISO-8859-1 (Latin-1): char = byte & 0xFF, a trivially-sound identity. (ASCII's
    //     0x80..0xFF map to U+FFFD in the JDK, but those bytes are excluded from a bounded ASCII
    //     domain by construction; for the in-range bytes the two charsets agree, so we treat the
    //     ASCII path as Latin-1, sound over its 0x00..0x7F domain.)
    //   * UTF-8: the standard 1/2/3/4-byte decode state machine, code points appended to a
    //     StringBuilder (the one construction primitive JBMC models soundly). Malformed sequences
    //     decode to the U+FFFD replacement char, matching the JDK's default (REPLACE) action.
    //
    // Charset recognition is by reference identity against the JDK singletons AND name, so an
    // unrecognized charset falls through to {@link #ofBytesNondet} (the engine's original nondet
    // behaviour — conservatively UNKNOWN, never a false VERIFY). Bounded by design: the decode loop
    // unwinds to the byte count, so keep symbolic byte arrays small.

    // --- charset-specific decoders (no Charset object on the operand stack) ---------------------
    //
    // JBMC can't reason about `Charset` object identity or `Charset.name()` (the singletons come from
    // a static initializer it havocs), so a runtime branch on the Charset routes nondet. Instead,
    // `StringBytecode` recognizes the `getstatic <Charsets>.UTF_8|ISO_8859_1|US_ASCII` that feeds the
    // ctor AT REWRITE TIME and retargets straight to one of these monomorphic factories (dropping the
    // getstatic), so no Charset object ever reaches the decode and the relation is concrete.

    /** UTF-8 decode of the whole array. */
    public static String ofBytesUtf8(byte[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        return decodeUtf8(data, 0, data.length);
    }

    /** UTF-8 decode of {@code [offset, offset+length)}. */
    public static String ofBytesUtf8(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new StringIndexOutOfBoundsException();
        }
        return decodeUtf8(data, offset, length);
    }

    /** ISO-8859-1 / US-ASCII decode of the whole array. */
    public static String ofBytesLatin1(byte[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        return decodeLatin1(data, 0, data.length);
    }

    /** ISO-8859-1 / US-ASCII decode of {@code [offset, offset+length)}. */
    public static String ofBytesLatin1(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new StringIndexOutOfBoundsException();
        }
        return decodeLatin1(data, offset, length);
    }

    /** Sound stand-in for {@code new String(byte[], Charset)} (whole array). */
    public static String ofBytes(byte[] data, Charset cs) {
        if (data == null || cs == null) {
            throw new NullPointerException();
        }
        return ofBytes(data, 0, data.length, cs);
    }

    /** Sound stand-in for {@code new String(byte[], int, int, Charset)}. */
    public static String ofBytes(byte[] data, int offset, int length, Charset cs) {
        if (data == null || cs == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new StringIndexOutOfBoundsException();  // matches String(byte[],int,int,*) bounds checking
        }
        if (isUtf8(cs)) {
            return decodeUtf8(data, offset, length);
        }
        if (isLatin1OrAscii(cs)) {
            return decodeLatin1(data, offset, length);
        }
        return ofBytesNondet(data, offset, length);  // unrecognized charset: original nondet behaviour
    }

    /** Sound stand-in for {@code new String(byte[], String)} (charset by name). */
    public static String ofBytes(byte[] data, String charsetName) {
        if (data == null || charsetName == null) {
            throw new NullPointerException();
        }
        return ofBytes(data, 0, data.length, charsetName);
    }

    /** Sound stand-in for {@code new String(byte[], int, int, String)} (charset by name). */
    public static String ofBytes(byte[] data, int offset, int length, String charsetName) {
        if (data == null || charsetName == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new StringIndexOutOfBoundsException();
        }
        if (isUtf8Name(charsetName)) {
            return decodeUtf8(data, offset, length);
        }
        if (isLatin1OrAsciiName(charsetName)) {
            return decodeLatin1(data, offset, length);
        }
        return ofBytesNondet(data, offset, length);
    }

    /**
     * Sound stand-in for {@code new String(byte[])} (whole array, the JVM default charset). The
     * platform default is UTF-8 on every modern JVM (JEP 400, Java 18+), which is also bmc4j's
     * supported floor; decode as UTF-8.
     */
    public static String ofBytes(byte[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        return decodeUtf8(data, 0, data.length);
    }

    /** Sound stand-in for {@code new String(byte[], int, int)} (the JVM default charset, UTF-8). */
    public static String ofBytes(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new StringIndexOutOfBoundsException();
        }
        return decodeUtf8(data, offset, length);
    }

    private static boolean isUtf8(Charset cs) {
        return cs == StandardCharsets.UTF_8 || isUtf8Name(cs.name());
    }

    private static boolean isLatin1OrAscii(Charset cs) {
        return cs == StandardCharsets.ISO_8859_1 || cs == StandardCharsets.US_ASCII
                || isLatin1OrAsciiName(cs.name());
    }

    private static boolean isUtf8Name(String name) {
        return "UTF-8".equals(name) || "UTF8".equals(name);
    }

    private static boolean isLatin1OrAsciiName(String name) {
        return "ISO-8859-1".equals(name) || "ISO8859-1".equals(name) || "latin1".equals(name)
                || "US-ASCII".equals(name) || "ASCII".equals(name);
    }

    /** Latin-1 / ASCII decode: char = byte &amp; 0xFF, one byte to one char. Trivially sound. */
    private static String decodeLatin1(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) (data[offset + i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * UTF-8 decode state machine over a bounded byte range. 1-byte ASCII fast path; 2/3/4-byte
     * lead + continuation bytes assembled into a code point and appended (as one char, or a
     * surrogate pair for astral code points). Malformed lead/continuation bytes append the U+FFFD
     * replacement char and resync, matching the JDK's default REPLACE decode action. The append loop
     * unwinds to the byte count, so this is sound and cheap for bounded byte arrays.
     */
    private static String decodeUtf8(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < length) {
            int b0 = data[offset + i] & 0xFF;
            if (b0 < 0x80) {                 // 0xxxxxxx: 1-byte ASCII
                sb.append((char) b0);
                i += 1;
            } else if (b0 < 0xC0) {          // 10xxxxxx: stray continuation byte -> replacement
                sb.append('�');
                i += 1;
            } else if (b0 < 0xE0) {          // 110xxxxx: 2-byte sequence
                if (i + 1 < length && isCont(data[offset + i + 1])) {
                    int cp = ((b0 & 0x1F) << 6) | (data[offset + i + 1] & 0x3F);
                    if (cp < 0x80) {          // overlong encoding -> replacement
                        sb.append('�');
                        i += 1;
                    } else {
                        sb.append((char) cp);
                        i += 2;
                    }
                } else {
                    sb.append('�');
                    i += 1;
                }
            } else if (b0 < 0xF0) {          // 1110xxxx: 3-byte sequence
                if (i + 2 < length && isCont(data[offset + i + 1]) && isCont(data[offset + i + 2])) {
                    int cp = ((b0 & 0x0F) << 12) | ((data[offset + i + 1] & 0x3F) << 6)
                            | (data[offset + i + 2] & 0x3F);
                    if (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF)) {  // overlong or surrogate -> replacement
                        sb.append('�');
                        i += 1;
                    } else {
                        sb.append((char) cp);
                        i += 3;
                    }
                } else {
                    sb.append('�');
                    i += 1;
                }
            } else if (b0 < 0xF8) {          // 11110xxx: 4-byte sequence (astral, surrogate pair)
                if (i + 3 < length && isCont(data[offset + i + 1]) && isCont(data[offset + i + 2])
                        && isCont(data[offset + i + 3])) {
                    int cp = ((b0 & 0x07) << 18) | ((data[offset + i + 1] & 0x3F) << 12)
                            | ((data[offset + i + 2] & 0x3F) << 6) | (data[offset + i + 3] & 0x3F);
                    if (cp < 0x10000 || cp > 0x10FFFF) {  // overlong or out of range -> replacement
                        sb.append('�');
                        i += 1;
                    } else {
                        int v = cp - 0x10000;
                        sb.append((char) (0xD800 + (v >> 10)));    // high surrogate
                        sb.append((char) (0xDC00 + (v & 0x3FF)));  // low surrogate
                        i += 4;
                    }
                } else {
                    sb.append('�');
                    i += 1;
                }
            } else {                          // 0xF8..0xFF: invalid lead byte -> replacement
                sb.append('�');
                i += 1;
            }
        }
        return sb.toString();
    }

    /** A UTF-8 continuation byte: top two bits {@code 10xxxxxx}. */
    private static boolean isCont(byte b) {
        return (b & 0xC0) == 0x80;
    }

    /**
     * Nondet decode for an unrecognized charset: an unconstrained (non-null) String, exactly the
     * engine's ORIGINAL behaviour for a native byte[] decode. This is a deliberate fall-through — a
     * charset bmc4j can't decode soundly stays conservatively UNKNOWN, never a false VERIFY.
     */
    @SuppressWarnings("unused")
    private static String ofBytesNondet(byte[] data, int offset, int length) {
        return org.cprover.CProver.nondetWithoutNull();
    }
}
