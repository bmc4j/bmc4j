package kotlin.text;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import kotlin.jvm.functions.Function1;
import kotlin.ranges.IntRange;

/**
 * Clean model of Kotlin's {@code kotlin.text.StringsKt} multifile facade. This class carries the SAME
 * fully-qualified name as the real stdlib facade, so on JBMC's analysis classpath it SHADOWS it: every
 * {@code String}/{@code CharSequence} extension a Kotlin call site emits ({@code "x".trim()} →
 * {@code StringsKt.trim((CharSequence)"x")}) binds here. An UN-modeled facade member is therefore a
 * silent JBMC nondet stub (the recurring facade disease), so the bulk of the bounded char-array
 * transforms are modeled here directly over the SOUND {@code java.lang.String} primitives JBMC's string
 * refinement handles ({@code length}/{@code charAt}/{@code substring}/{@code indexOf}/{@code replace};
 * see {@code proofs.strings.StringLaws}) — never the JBMC-unsound {@code repeat}/{@code strip}/{@code
 * isBlank} JDK ops, and never via a virtual {@code CharIterator}: a concrete {@code String} is obtained
 * with {@code .toString()} and walked BY INDEX.
 *
 * <p><b>buildString</b> is an INLINE stdlib function; from a Kotlin call site its body lands in the
 * caller (allocate {@code StringBuilder}, run the builder lambda, {@code toString()}) — all already
 * modeled. These facade JVM methods are the NON-inline / Java reach and mirror that shape; the
 * capacity-hint overload ignores the hint (the bounded StringBuilder backing is fixed-size — sound,
 * matching the collection builders' mapCapacity precedent).
 *
 * <p><b>Soundness conventions.</b> Every modeled op normalizes its {@code CharSequence}/{@code String}
 * receiver to a concrete {@code String s = cs.toString()} and then uses only by-index {@code charAt} /
 * {@code length} / {@code substring} (the sound primitives). The default (no-case-flag) overloads are
 * modeled; the {@code ignoreCase} overloads model the {@code false} branch exactly and route the
 * {@code true} branch through ASCII-only case folding (documented per method) — never locale tables.
 *
 * <p><b>Genuine walls</b> carry a loud {@link BmcUnmodelable} stub (reaching one demotes to a
 * member-named UNKNOWN, never a silent havoc): regex ops (the regex engine), locale/full-Unicode case
 * mapping (locale tables), number parse/format that hits dtoa or locale tables, and charset/encoding.
 * The large higher-order / collection-/sequence-returning remainder (map/filter/fold/associate/group/
 * window/chunk/zip and the iterator/asSequence bridges) stays in the {@link BmcModelTail} — equally
 * loud-if-reached, modeled only as the bounded proofs come to need it.
 */
@BmcModelTail(reason = "exotic StringsKt facade remainder — kotlin-stdlib's higher-order / collection- "
        + "and sequence-returning CharSequence extensions (map/filter/fold/reduce/associate/groupBy/"
        + "windowed/chunked/zip/asSequence/iterator/etc.) the bounded proofs do not exercise; loud "
        + "under JBMC if reached")
public final class StringsKt {

    private StringsKt() {
    }

    // ===================================================================================================
    // buildString (inline-shape mirror; non-inline / Java reach)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String buildString(Function1<? super StringBuilder, kotlin.Unit> builderAction) {
        StringBuilder sb = new StringBuilder();
        builderAction.invoke(sb);
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String buildString(int capacity, Function1<? super StringBuilder, kotlin.Unit> builderAction) {
        StringBuilder sb = new StringBuilder();
        builderAction.invoke(sb);
        return sb.toString();
    }

    // ===================================================================================================
    // Indices / size queries
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int getLastIndex(CharSequence cs) {
        return cs.toString().length() - 1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static IntRange getIndices(CharSequence cs) {
        return new IntRange(0, cs.toString().length() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean isBlank(CharSequence cs) {
        String s = cs.toString();
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ===================================================================================================
    // first / last / single / getOrNull (no-predicate element access; by-index, never an iterator)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char first(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        return s.charAt(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char last(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        return s.charAt(s.length() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character firstOrNull(CharSequence cs) {
        String s = cs.toString();
        return s.length() == 0 ? null : s.charAt(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character lastOrNull(CharSequence cs) {
        String s = cs.toString();
        return s.length() == 0 ? null : s.charAt(s.length() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char single(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        if (s.length() != 1) {
            throw new IllegalArgumentException("Char sequence has more than one element.");
        }
        return s.charAt(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character singleOrNull(CharSequence cs) {
        String s = cs.toString();
        return s.length() == 1 ? s.charAt(0) : null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character getOrNull(CharSequence cs, int index) {
        String s = cs.toString();
        return (index >= 0 && index < s.length()) ? s.charAt(index) : null;
    }

    // ===================================================================================================
    // trim / trimStart / trimEnd
    //   - no-arg: drop leading/trailing whitespace (Character.isWhitespace — char-by-char, sound).
    //   - char[]: drop leading/trailing chars contained in the set (membership by-index, no Set).
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trim(CharSequence cs) {
        return trimImpl(cs.toString(), true, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trim(String s, char[] chars) {
        return trimCharsImpl(s, chars, true, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trim(CharSequence cs, char[] chars) {
        return trimCharsImpl(cs.toString(), chars, true, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimStart(CharSequence cs) {
        return trimImpl(cs.toString(), true, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimStart(String s, char[] chars) {
        return trimCharsImpl(s, chars, true, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimStart(CharSequence cs, char[] chars) {
        return trimCharsImpl(cs.toString(), chars, true, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimEnd(CharSequence cs) {
        return trimImpl(cs.toString(), false, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimEnd(String s, char[] chars) {
        return trimCharsImpl(s, chars, false, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimEnd(CharSequence cs, char[] chars) {
        return trimCharsImpl(cs.toString(), chars, false, true);
    }

    private static String trimImpl(String s, boolean start, boolean end) {
        int lo = 0;
        int hi = s.length();
        if (start) {
            while (lo < hi && Character.isWhitespace(s.charAt(lo))) {
                lo++;
            }
        }
        if (end) {
            while (hi > lo && Character.isWhitespace(s.charAt(hi - 1))) {
                hi--;
            }
        }
        return s.substring(lo, hi);
    }

    private static String trimCharsImpl(String s, char[] chars, boolean start, boolean end) {
        int lo = 0;
        int hi = s.length();
        if (start) {
            while (lo < hi && inChars(s.charAt(lo), chars)) {
                lo++;
            }
        }
        if (end) {
            while (hi > lo && inChars(s.charAt(hi - 1), chars)) {
                hi--;
            }
        }
        return s.substring(lo, hi);
    }

    private static boolean inChars(char c, char[] chars) {
        for (char ch : chars) {
            if (ch == c) {
                return true;
            }
        }
        return false;
    }

    // ===================================================================================================
    // take / drop / takeLast / dropLast (bounded prefix/suffix slices)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence take(CharSequence cs, int n) {
        return takeImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String take(String s, int n) {
        return takeImpl(s, n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence drop(CharSequence cs, int n) {
        return dropImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String drop(String s, int n) {
        return dropImpl(s, n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence takeLast(CharSequence cs, int n) {
        return takeLastImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String takeLast(String s, int n) {
        return takeLastImpl(s, n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence dropLast(CharSequence cs, int n) {
        return dropLastImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String dropLast(String s, int n) {
        return dropLastImpl(s, n);
    }

    private static String takeImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return s.substring(0, Math.min(n, s.length()));
    }

    private static String dropImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return s.substring(Math.min(n, s.length()));
    }

    private static String takeLastImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        int len = s.length();
        return s.substring(len - Math.min(n, len));
    }

    private static String dropLastImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return takeImpl(s, s.length() - n < 0 ? 0 : s.length() - n);
    }

    // ===================================================================================================
    // substring / slice / subSequence over an IntRange (closed range -> [first, last])
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substring(String s, IntRange range) {
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substring(CharSequence cs, IntRange range) {
        String s = cs.toString();
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String slice(String s, IntRange range) {
        if (range.isEmpty()) {
            return "";
        }
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence slice(CharSequence cs, IntRange range) {
        String s = cs.toString();
        if (range.isEmpty()) {
            return "";
        }
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence subSequence(CharSequence cs, IntRange range) {
        String s = cs.toString();
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    // ===================================================================================================
    // substringBefore / substringAfter / *Last (delimiter by char or String; default missingDelimiter)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBefore(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBefore(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfter(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + 1, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfter(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + delimiter.length(), s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBeforeLast(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBeforeLast(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfterLast(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + 1, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfterLast(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + delimiter.length(), s.length());
    }

    // ===================================================================================================
    // removePrefix / removeSuffix / removeSurrounding / removeRange
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removePrefix(String s, CharSequence prefix) {
        String p = prefix.toString();
        if (startsWithImpl(s, p, 0)) {
            return s.substring(p.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removePrefix(CharSequence cs, CharSequence prefix) {
        String s = cs.toString();
        String p = prefix.toString();
        if (startsWithImpl(s, p, 0)) {
            return s.substring(p.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removeSuffix(String s, CharSequence suffix) {
        String suf = suffix.toString();
        if (endsWithImpl(s, suf)) {
            return s.substring(0, s.length() - suf.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeSuffix(CharSequence cs, CharSequence suffix) {
        String s = cs.toString();
        String suf = suffix.toString();
        if (endsWithImpl(s, suf)) {
            return s.substring(0, s.length() - suf.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removeSurrounding(String s, CharSequence delimiter) {
        return removeSurroundingImpl(s, delimiter.toString(), delimiter.toString());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removeSurrounding(String s, CharSequence prefix, CharSequence suffix) {
        return removeSurroundingImpl(s, prefix.toString(), suffix.toString());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeSurrounding(CharSequence cs, CharSequence delimiter) {
        return removeSurroundingImpl(cs.toString(), delimiter.toString(), delimiter.toString());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeSurrounding(CharSequence cs, CharSequence prefix, CharSequence suffix) {
        return removeSurroundingImpl(cs.toString(), prefix.toString(), suffix.toString());
    }

    private static String removeSurroundingImpl(String s, String prefix, String suffix) {
        if (s.length() >= prefix.length() + suffix.length()
                && startsWithImpl(s, prefix, 0) && endsWithImpl(s, suffix)) {
            return s.substring(prefix.length(), s.length() - suffix.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeRange(CharSequence cs, int startIndex, int endIndex) {
        String s = cs.toString();
        if (endIndex < startIndex) {
            throw new IndexOutOfBoundsException("End index (" + endIndex
                    + ") is less than start index (" + startIndex + ").");
        }
        if (endIndex == startIndex) {
            return s;
        }
        return s.substring(0, startIndex) + s.substring(endIndex, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeRange(CharSequence cs, IntRange range) {
        return removeRange(cs, range.getFirst(), range.getLast() + 1);
    }

    // ===================================================================================================
    // startsWith / endsWith / contains / indexOf / lastIndexOf
    //   The default (caseSensitive) branch uses the sound java.lang.String primitives directly; the
    //   ignoreCase=true branch routes through ASCII-only case folding (asciiLower) — never locale tables.
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(CharSequence cs, char ch, boolean ignoreCase) {
        String s = cs.toString();
        return s.length() != 0 && charEq(s.charAt(0), ch, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(CharSequence cs, CharSequence prefix, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), 0, prefix.toString(), 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(CharSequence cs, CharSequence prefix, int startIndex, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), startIndex, prefix.toString(), 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(String s, String prefix, boolean ignoreCase) {
        return regionMatchesImplBool(s, 0, prefix, 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(String s, String prefix, int startIndex, boolean ignoreCase) {
        return regionMatchesImplBool(s, startIndex, prefix, 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith(CharSequence cs, char ch, boolean ignoreCase) {
        String s = cs.toString();
        return s.length() != 0 && charEq(s.charAt(s.length() - 1), ch, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith(CharSequence cs, CharSequence suffix, boolean ignoreCase) {
        String s = cs.toString();
        String suf = suffix.toString();
        return regionMatchesImplBool(s, s.length() - suf.length(), suf, 0, suf.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith(String s, String suffix, boolean ignoreCase) {
        return regionMatchesImplBool(s, s.length() - suffix.length(), suffix, 0, suffix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(CharSequence cs, char ch, boolean ignoreCase) {
        return indexOf(cs, ch, 0, ignoreCase) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(CharSequence cs, CharSequence other, boolean ignoreCase) {
        return indexOf(cs, other.toString(), 0, ignoreCase) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(CharSequence cs, char ch, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = startIndex < 0 ? 0 : startIndex;
        for (int i = from; i < s.length(); i++) {
            if (charEq(s.charAt(i), ch, ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(CharSequence cs, String needle, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = startIndex < 0 ? 0 : startIndex;
        int last = s.length() - needle.length();
        for (int i = from; i <= last; i++) {
            if (regionMatchesImplBool(s, i, needle, 0, needle.length(), ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(CharSequence cs, char ch, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = Math.min(startIndex, s.length() - 1);
        for (int i = from; i >= 0; i--) {
            if (charEq(s.charAt(i), ch, ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(CharSequence cs, String needle, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = Math.min(startIndex, s.length() - needle.length());
        for (int i = from; i >= 0; i--) {
            if (regionMatchesImplBool(s, i, needle, 0, needle.length(), ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithImpl(String s, String prefix, int offset) {
        return regionMatchesImplBool(s, offset, prefix, 0, prefix.length(), false);
    }

    private static boolean endsWithImpl(String s, String suffix) {
        return regionMatchesImplBool(s, s.length() - suffix.length(), suffix, 0, suffix.length(), false);
    }

    // ===================================================================================================
    // regionMatches
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean regionMatches(CharSequence cs, int thisOffset, CharSequence other,
            int otherOffset, int length, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), thisOffset, other.toString(), otherOffset, length, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean regionMatches(String s, int thisOffset, String other,
            int otherOffset, int length, boolean ignoreCase) {
        return regionMatchesImplBool(s, thisOffset, other, otherOffset, length, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean regionMatchesImpl(CharSequence cs, int thisOffset, CharSequence other,
            int otherOffset, int length, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), thisOffset, other.toString(), otherOffset, length, ignoreCase);
    }

    private static boolean regionMatchesImplBool(String s, int thisOffset, String other,
            int otherOffset, int length, boolean ignoreCase) {
        if (thisOffset < 0 || otherOffset < 0
                || thisOffset > s.length() - length || otherOffset > other.length() - length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!charEq(s.charAt(thisOffset + i), other.charAt(otherOffset + i), ignoreCase)) {
                return false;
            }
        }
        return true;
    }

    // ===================================================================================================
    // compareTo / equals / contentEquals (default + ASCII ignoreCase)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean equals(String a, String b, boolean ignoreCase) {
        if (a == null) {
            return b == null;
        }
        if (b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (!charEq(a.charAt(i), b.charAt(i), ignoreCase)) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int compareTo(String a, String b, boolean ignoreCase) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            char ca = ignoreCase ? asciiLower(a.charAt(i)) : a.charAt(i);
            char cb = ignoreCase ? asciiLower(b.charAt(i)) : b.charAt(i);
            if (ca != cb) {
                return ca - cb;
            }
        }
        return a.length() - b.length();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEquals(CharSequence a, CharSequence b) {
        return contentEqualsImpl(a, b);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEquals(CharSequence a, CharSequence b, boolean ignoreCase) {
        return ignoreCase ? contentEqualsIgnoreCaseImpl(a, b) : contentEqualsImpl(a, b);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEqualsImpl(CharSequence a, CharSequence b) {
        return equals(a == null ? null : a.toString(), b == null ? null : b.toString(), false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEqualsIgnoreCaseImpl(CharSequence a, CharSequence b) {
        return equals(a == null ? null : a.toString(), b == null ? null : b.toString(), true);
    }

    // ===================================================================================================
    // commonPrefixWith / commonSuffixWith
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String commonPrefixWith(CharSequence a, CharSequence b, boolean ignoreCase) {
        String sa = a.toString();
        String sb = b.toString();
        int n = Math.min(sa.length(), sb.length());
        int i = 0;
        while (i < n && charEq(sa.charAt(i), sb.charAt(i), ignoreCase)) {
            i++;
        }
        if (hasSurrogatePairAt(sa, i - 1) || hasSurrogatePairAt(sb, i - 1)) {
            i--;
        }
        return sa.substring(0, i);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String commonSuffixWith(CharSequence a, CharSequence b, boolean ignoreCase) {
        String sa = a.toString();
        String sb = b.toString();
        int la = sa.length();
        int lb = sb.length();
        int n = Math.min(la, lb);
        int i = 0;
        while (i < n && charEq(sa.charAt(la - i - 1), sb.charAt(lb - i - 1), ignoreCase)) {
            i++;
        }
        if (hasSurrogatePairAt(sa, la - i - 1) || hasSurrogatePairAt(sb, lb - i - 1)) {
            i--;
        }
        return sa.substring(la - i, la);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean hasSurrogatePairAt(CharSequence cs, int index) {
        String s = cs.toString();
        return index >= 0 && index <= s.length() - 2
                && Character.isHighSurrogate(s.charAt(index))
                && Character.isLowSurrogate(s.charAt(index + 1));
    }

    // ===================================================================================================
    // replace / replaceFirst (char,char + String,String) and the before/after positional replacers
    //   The default branch is sound over java.lang.String.replace (native-sound; StringLaws). ignoreCase
    //   String replace routes through the by-index ASCII fold above.
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replace(String s, char oldChar, char newChar, boolean ignoreCase) {
        if (!ignoreCase) {
            return s.replace(oldChar, newChar);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(charEq(c, oldChar, true) ? newChar : c);
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replace(String s, String oldValue, String newValue, boolean ignoreCase) {
        if (oldValue.length() == 0) {
            // Kotlin inserts newValue between every char (and at both ends); model the non-empty case
            // soundly and route the empty-needle interleave through the bounded builder.
            StringBuilder sb = new StringBuilder();
            sb.append(newValue);
            for (int i = 0; i < s.length(); i++) {
                sb.append(s.charAt(i));
                sb.append(newValue);
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i <= s.length() - oldValue.length()) {
            if (regionMatchesImplBool(s, i, oldValue, 0, oldValue.length(), ignoreCase)) {
                sb.append(newValue);
                i += oldValue.length();
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        sb.append(s.substring(i, s.length()));
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceFirst(String s, char oldChar, char newChar, boolean ignoreCase) {
        int idx = indexOf(s, oldChar, 0, ignoreCase);
        if (idx < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        sb.setCharAt(idx, newChar);
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceFirst(String s, String oldValue, String newValue, boolean ignoreCase) {
        int idx = indexOf(s, oldValue, 0, ignoreCase);
        if (idx < 0) {
            return s;
        }
        return s.substring(0, idx) + newValue + s.substring(idx + oldValue.length(), s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBefore(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBefore(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBeforeLast(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBeforeLast(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfter(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, idx + 1, s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfter(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue
                : replaceRangeStr(s, idx + delimiter.length(), s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfterLast(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, idx + 1, s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfterLast(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue
                : replaceRangeStr(s, idx + delimiter.length(), s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence replaceRange(CharSequence cs, int startIndex, int endIndex, CharSequence replacement) {
        String s = cs.toString();
        if (endIndex < startIndex) {
            throw new IndexOutOfBoundsException("End index (" + endIndex
                    + ") is less than start index (" + startIndex + ").");
        }
        return s.substring(0, startIndex) + replacement.toString() + s.substring(endIndex, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence replaceRange(CharSequence cs, IntRange range, CharSequence replacement) {
        return replaceRange(cs, range.getFirst(), range.getLast() + 1, replacement);
    }

    private static String replaceRangeStr(String s, int start, int end, String replacement) {
        return s.substring(0, start) + replacement + s.substring(end, s.length());
    }

    // ===================================================================================================
    // padStart / padEnd / reversed / repeat
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence padStart(CharSequence cs, int length, char padChar) {
        return padStartImpl(cs.toString(), length, padChar);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String padStart(String s, int length, char padChar) {
        return padStartImpl(s, length, padChar);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence padEnd(CharSequence cs, int length, char padChar) {
        return padEndImpl(cs.toString(), length, padChar);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String padEnd(String s, int length, char padChar) {
        return padEndImpl(s, length, padChar);
    }

    private static String padStartImpl(String s, int length, char padChar) {
        if (length < 0) {
            throw new IllegalArgumentException("Desired length " + length + " is less than zero.");
        }
        if (length <= s.length()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(s);
        return sb.toString();
    }

    private static String padEndImpl(String s, int length, char padChar) {
        if (length < 0) {
            throw new IllegalArgumentException("Desired length " + length + " is less than zero.");
        }
        if (length <= s.length()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        for (int i = s.length(); i < length; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence reversed(CharSequence cs) {
        String s = cs.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    // repeat: built char-by-char over the bounded StringBuilder model (the JDK String.repeat is
    // JBMC-UNSOUND per StringLaws — it havocs both length and content — so this never delegates to it).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String repeat(CharSequence cs, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Count 'n' must be non-negative, but was " + n + ".");
        }
        String s = cs.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    // ===================================================================================================
    // toCharArray / concatToString (bounded char-array <-> String, by-index)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] toCharArray(String s, int startIndex, int endIndex) {
        char[] out = new char[endIndex - startIndex];
        for (int i = startIndex; i < endIndex; i++) {
            out[i - startIndex] = s.charAt(i);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String concatToString(char[] chars) {
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String concatToString(char[] chars, int startIndex, int endIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    // ===================================================================================================
    // toList / toMutableList / toSet / toHashSet (bounded element collections, by-index — no CharIterator)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toList(CharSequence cs) {
        return toMutableList(cs);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toMutableList(CharSequence cs) {
        String s = cs.toString();
        ArrayList<Character> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(s.charAt(i));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static java.util.Set<Character> toSet(CharSequence cs) {
        return toHashSet(cs);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static java.util.HashSet<Character> toHashSet(CharSequence cs) {
        String s = cs.toString();
        java.util.HashSet<Character> out = new java.util.HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(s.charAt(i));
        }
        return out;
    }

    // ===================================================================================================
    // ---- shared char helpers: ASCII-only case folding (NEVER locale tables) ----
    // ===================================================================================================

    private static char asciiLower(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    private static boolean charEq(char a, char b, boolean ignoreCase) {
        if (a == b) {
            return true;
        }
        return ignoreCase && asciiLower(a) == asciiLower(b);
    }

    // ===================================================================================================
    // ============================  GENUINE WALLS (loud @BmcUnmodelable)  ===============================
    // ===================================================================================================

    // ---- Locale-dependent / full-Unicode case mapping: needs locale tables ----
    @BmcUnmodelable(reason = "locale-dependent case mapping — needs the locale case tables")
    public static String capitalize(String a0, java.util.Locale a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.capitalize(java.lang.String,java.util.Locale) — locale-dependent case mapping — needs the locale case tables");
    }

    @BmcUnmodelable(reason = "locale-dependent case mapping — needs the locale case tables")
    public static String decapitalize(String a0, java.util.Locale a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decapitalize(java.lang.String,java.util.Locale) — locale-dependent case mapping — needs the locale case tables");
    }

    @BmcUnmodelable(reason = "full-Unicode title-case mapping of the first char — needs the Unicode case tables")
    public static String capitalize(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.capitalize(java.lang.String) — full-Unicode title-case mapping — needs the Unicode case tables");
    }

    @BmcUnmodelable(reason = "full-Unicode case mapping — needs the Unicode case tables")
    public static String decapitalize(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decapitalize(java.lang.String) — full-Unicode case mapping — needs the Unicode case tables");
    }

    @BmcUnmodelable(reason = "case-insensitive ordering comparator — full-Unicode/locale case fold over an open comparator")
    public static java.util.Comparator getCASE_INSENSITIVE_ORDER(kotlin.jvm.internal.StringCompanionObject a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.getCASE_INSENSITIVE_ORDER(kotlin.jvm.internal.StringCompanionObject) — full-Unicode/locale case-insensitive ordering");
    }

    // ---- Regex engine ----
    @BmcUnmodelable(reason = "regex engine — split over a java.util.regex.Pattern")
    public static List split(CharSequence a0, java.util.regex.Pattern a1, int a2) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.split(java.lang.CharSequence,java.util.regex.Pattern,int) — regex engine");
    }

    // ---- Number parse with radix/locale: full radix parsing + (for FP) dtoa ----
    @BmcUnmodelable(reason = "floating-point parse — needs dtoa")
    public static Double toDoubleOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toDoubleOrNull(java.lang.String) — floating-point parse needs dtoa");
    }

    @BmcUnmodelable(reason = "floating-point parse — needs dtoa")
    public static Float toFloatOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toFloatOrNull(java.lang.String) — floating-point parse needs dtoa");
    }

    @BmcUnmodelable(reason = "BigDecimal parse — needs dtoa / arbitrary-precision decimal parsing")
    public static java.math.BigDecimal toBigDecimalOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigDecimalOrNull(java.lang.String) — BigDecimal parse needs dtoa");
    }

    @BmcUnmodelable(reason = "BigDecimal parse with MathContext — needs dtoa / arbitrary-precision decimal parsing")
    public static java.math.BigDecimal toBigDecimalOrNull(String a0, java.math.MathContext a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigDecimalOrNull(java.lang.String,java.math.MathContext) — BigDecimal parse needs dtoa");
    }

    @BmcUnmodelable(reason = "BigInteger parse — arbitrary-precision radix parsing")
    public static java.math.BigInteger toBigIntegerOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigIntegerOrNull(java.lang.String) — arbitrary-precision radix parsing");
    }

    @BmcUnmodelable(reason = "BigInteger parse with radix — arbitrary-precision radix parsing")
    public static java.math.BigInteger toBigIntegerOrNull(String a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigIntegerOrNull(java.lang.String,int) — arbitrary-precision radix parsing");
    }

    @BmcUnmodelable(reason = "number-format error helper — throws a NumberFormatException constructed from locale-formatted text")
    public static Void numberFormatError(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.numberFormatError(java.lang.String) — number-format error helper");
    }

    // ---- Charset / encoding ----
    @BmcUnmodelable(reason = "charset decode — UTF-8 byte decoding")
    public static String decodeToString(byte[] a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decodeToString(byte[]) — charset decode");
    }

    @BmcUnmodelable(reason = "charset decode — UTF-8 byte decoding")
    public static String decodeToString(byte[] a0, int a1, int a2, boolean a3) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decodeToString(byte[],int,int,boolean) — charset decode");
    }

    @BmcUnmodelable(reason = "charset encode — UTF-8 byte encoding")
    public static byte[] encodeToByteArray(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.encodeToByteArray(java.lang.String) — charset encode");
    }

    @BmcUnmodelable(reason = "charset encode — UTF-8 byte encoding")
    public static byte[] encodeToByteArray(String a0, int a1, int a2, boolean a3) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.encodeToByteArray(java.lang.String,int,int,boolean) — charset encode");
    }
}
