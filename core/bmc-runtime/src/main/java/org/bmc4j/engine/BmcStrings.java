package org.bmc4j.engine;

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
}
