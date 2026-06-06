package org.bmc4j;

import org.cprover.CProver;
import org.cprover.CProverString;

/**
 * The ergonomic facade developers use inside {@link BmcProof} methods.
 *
 * <ul>
 *   <li>{@code anyX()} introduces a symbolic input — JBMC explores every value at once.</li>
 *   <li>{@code anyInt(lo, hi)}, {@code anyPositiveInt()}, … collapse a symbolic input and its
 *       domain assumption into one call.</li>
 *   <li>{@link #assume} narrows the input domain for anything the helpers don't cover
 *       (e.g. relations between two inputs).</li>
 *   <li>{@link #check} states a property; JBMC fails the proof if any allowed input breaks it.</li>
 * </ul>
 *
 * {@code check} is implemented as a thrown {@link AssertionError}, which JBMC
 * reports as a violation — this works regardless of how the host JVM treats
 * Java {@code assert} statements.
 */
public final class Bmc {

    private Bmc() {
    }

    public static int anyInt() {
        return CProver.nondetInt();
    }

    /**
     * A symbolic int constrained to {@code [minInclusive, maxInclusive]} — collapses
     * the common {@code anyInt()} + {@code assume(lo <= x && x <= hi)} pair into one call.
     */
    public static int anyInt(int __minInclusive, int __maxInclusive) {
        int value = CProver.nondetInt();
        CProver.assume(value >= __minInclusive && value <= __maxInclusive);
        return value;
    }

    /** A symbolic int {@code >= 1}. */
    public static int anyPositiveInt() {
        int value = CProver.nondetInt();
        CProver.assume(value >= 1);
        return value;
    }

    /** A symbolic int {@code >= 0}. */
    public static int anyNonNegativeInt() {
        int value = CProver.nondetInt();
        CProver.assume(value >= 0);
        return value;
    }

    public static long anyLong() {
        return CProver.nondetLong();
    }

    /** A symbolic long constrained to {@code [minInclusive, maxInclusive]}. */
    public static long anyLong(long __minInclusive, long __maxInclusive) {
        long value = CProver.nondetLong();
        CProver.assume(value >= __minInclusive && value <= __maxInclusive);
        return value;
    }

    public static boolean anyBoolean() {
        return CProver.nondetBoolean();
    }

    /** A symbolic {@code short}. */
    public static short anyShort() {
        return CProver.nondetShort();
    }

    /** A symbolic {@code byte}. */
    public static byte anyByte() {
        return CProver.nondetByte();
    }

    /** A symbolic {@code char}. */
    public static char anyChar() {
        return CProver.nondetChar();
    }

    /**
     * A symbolic {@code double}. <b>Includes NaN and the infinities</b> — the full IEEE-754 domain.
     * Because every ordered comparison with NaN is {@code false}, a bound like
     * {@code assume(x >= 0)} does NOT exclude NaN; use {@link #anyDouble(double, double)} when you
     * need a finite, ordered value. (bmc4j discourages {@code double} in proofs — floating point
     * makes the solver much slower; prefer integer models where you can.)
     */
    public static double anyDouble() {
        return CProver.nondetDouble();
    }

    /**
     * A symbolic {@code double} constrained to {@code [minInclusive, maxInclusive]}. <b>Excludes
     * NaN</b> by construction: the range assumption {@code x >= lo && x <= hi} is {@code false} for
     * NaN (and for the infinities outside the range), so the solver cannot pick them. Pass a finite
     * {@code lo}/{@code hi}.
     */
    public static double anyDouble(double __minInclusive, double __maxInclusive) {
        double value = CProver.nondetDouble();
        CProver.assume(value >= __minInclusive && value <= __maxInclusive);
        return value;
    }

    /**
     * A symbolic {@code float}. <b>Includes NaN and the infinities</b> — the full IEEE-754 domain;
     * see {@link #anyDouble()} for the NaN-comparison caveat. Prefer integer models where possible.
     */
    public static float anyFloat() {
        return CProver.nondetFloat();
    }

    /**
     * A symbolic {@code float} constrained to {@code [minInclusive, maxInclusive]}. <b>Excludes
     * NaN</b> by construction (the range assumption is {@code false} for NaN). Pass a finite
     * {@code lo}/{@code hi}.
     */
    public static float anyFloat(float __minInclusive, float __maxInclusive) {
        float value = CProver.nondetFloat();
        CProver.assume(value >= __minInclusive && value <= __maxInclusive);
        return value;
    }

    /**
     * A symbolic element of {@code values} — JBMC considers every entry. Handy for
     * enums: {@code Suit s = Bmc.anyOf(Suit.values());}. (Branch on the result with
     * {@code ==}/{@code if}, not {@code switch} — see the enums example.)
     */
    public static <T> T anyOf(T[] values) {
        return values[anyInt(0, values.length - 1)];
    }

    /**
     * A symbolic element of an explicit value list — JBMC considers every entry. The ergonomic form
     * for ad-hoc domains: {@code String region = Bmc.anyOf("us", "eu");}. (For {@code Enum.values()}
     * the array overload {@link #anyOf(Object[])} applies — pass the array directly.)
     */
    @SafeVarargs
    public static <T> T anyOf(T first, T... rest) {
        // index 0 selects `first`; 1..rest.length select from `rest`.
        int idx = anyInt(0, rest.length);
        if (idx == 0) {
            return first;
        }
        return rest[idx - 1];
    }

    /**
     * A symbolic element of a {@code List} — JBMC considers every entry. For collection-shaped
     * domains: {@code anyOf(List.of("us", "eu"))}. The list must be non-empty.
     */
    public static <T> T anyOf(java.util.List<T> values) {
        return values.get(anyInt(0, values.size() - 1));
    }

    /**
     * A symbolic string of length {@code 0..maxLength}. <b>A length is required</b>: the bundled
     * sound string operations ({@code equals}, etc.) reason character-by-character, so they unwind
     * to the string's length — bounding it keeps proofs tractable. Pick the smallest bound your
     * values actually use (an ISO code, a mode name, an identifier).
     *
     * <pre>{@code
     * String region = Bmc.anyString(2);
     * Bmc.assume(region.equals("us") || region.equals("eu"));   // sound equality (see below)
     * Config.route(region);                                     // proven for every supported region
     * }</pre>
     */
    public static String anyString(int maxLength) {
        String s = CProver.nondetWithoutNull();
        CProver.assume(s.length() >= 0 && s.length() <= maxLength);
        return s;
    }

    /**
     * A symbolic string whose length is constrained to {@code [minLength, maxLength]} (both
     * inclusive). The string content is otherwise unconstrained (any UTF-16 char) — use
     * {@link #anyString(int, String)} or {@link #anyAsciiString(int)} when you also want to bound
     * the <em>characters</em>. Never {@code null} (the result is a non-null nondet string).
     *
     * <p><b>Length semantics:</b> {@code minLength <= s.length() <= maxLength}. Pass
     * {@code anyString(1, n)} for a non-empty string, {@code anyString(n, n)} for an exactly-{@code n}
     * string. The character read/compare operations bmc4j makes sound ({@code equals},
     * {@code charAt}, …) unwind to the length, so keep {@code maxLength} as small as your values
     * actually need.
     *
     * <pre>{@code
     * String code = Bmc.anyString(2, 3);   // every 2- or 3-char string
     * Bmc.check(code.length() >= 2 && code.length() <= 3);
     * }</pre>
     *
     * @throws IllegalArgumentException if {@code minLength < 0} or {@code maxLength < minLength}
     */
    public static String anyString(int minLength, int maxLength) {
        if (minLength < 0 || maxLength < minLength) {
            throw new IllegalArgumentException(
                    "require 0 <= minLength <= maxLength, got minLength=" + minLength
                            + ", maxLength=" + maxLength);
        }
        String s = CProver.nondetWithoutNull();
        CProver.assume(s.length() >= minLength && s.length() <= maxLength);
        return s;
    }

    /**
     * A symbolic string of length {@code 0..maxLength} whose <b>every character is drawn from
     * {@code alphabet}</b>. This folds the per-character domain assumption into the helper — the
     * string analogue of {@link #anyInt(int, int)} — so proofs range only over realistic inputs
     * (identifiers, digit strings, ISO codes) instead of all of UTF-16, which is both a large SAT
     * shrink per character and frees you from hand-rolling the {@code assume} + {@code charAt} loop.
     *
     * <p><b>Length semantics:</b> {@code 0 <= s.length() <= maxLength} (the empty string is
     * included). Every in-range index {@code i} (i.e. {@code i < s.length()}) satisfies
     * {@code alphabet.indexOf(s.charAt(i)) >= 0}.
     *
     * <p><b>Soundness:</b> the constraint is enforced by assuming, for each of the {@code maxLength}
     * possible positions, that either the position is past the (symbolic) length or the character
     * there equals one of the {@code alphabet} characters. The character is read with the same sound
     * primitive ({@code CProverString.charAt}) the bundled string operations use, so a proof that
     * later reads {@code s.charAt(i)} / compares {@code s} sees exactly these constrained characters
     * — there is no separate, unconstrained content the solver could pick. Duplicate characters in
     * {@code alphabet} are harmless. {@code alphabet} must be non-empty (an empty alphabet would
     * force the string to be empty; that is rejected to catch mistakes).
     *
     * <pre>{@code
     * String id = Bmc.anyString(4, "abc");   // every string of <=4 chars over {a,b,c}
     * for (int i = 0; i < id.length(); i++) {
     *     char c = id.charAt(i);
     *     Bmc.check(c == 'a' || c == 'b' || c == 'c');   // holds for all of them
     * }
     * }</pre>
     *
     * @throws IllegalArgumentException if {@code maxLength < 0} or {@code alphabet} is empty
     */
    public static String anyString(int maxLength, String alphabet) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("require maxLength >= 0, got " + maxLength);
        }
        if (alphabet == null || alphabet.isEmpty()) {
            throw new IllegalArgumentException("alphabet must be non-empty");
        }
        String s = CProver.nondetWithoutNull();
        int len = s.length();
        CProver.assume(len >= 0 && len <= maxLength);
        int alphaLen = alphabet.length();
        // Loop bound is the STATIC maxLength so JBMC unwinds it deterministically. For each position
        // that is actually within the symbolic length, assume its char is one of the alphabet chars.
        for (int i = 0; i < maxLength; i++) {
            if (i < len) {
                char c = CProverString.charAt(s, i);
                boolean inAlphabet = false;
                for (int j = 0; j < alphaLen; j++) {
                    if (c == CProverString.charAt(alphabet, j)) {
                        inAlphabet = true;
                    }
                }
                CProver.assume(inAlphabet);
            }
        }
        return s;
    }

    /** Lowest printable-ASCII code point (space), inclusive bound for {@link #anyAsciiString(int)}. */
    private static final char ASCII_PRINTABLE_MIN = 0x20;

    /** Highest printable-ASCII code point (tilde {@code ~}), inclusive bound for {@link #anyAsciiString(int)}. */
    private static final char ASCII_PRINTABLE_MAX = 0x7E;

    /**
     * A symbolic string of length {@code 0..maxLength} whose every character is <b>printable
     * ASCII</b> — the range {@code U+0020} (space) through {@code U+007E} ({@code ~}) inclusive.
     * Convenience for the common "realistic text input" case: it excludes control characters and the
     * vast non-ASCII UTF-16 range by construction, mirroring how {@link #anyDouble(double, double)}
     * excludes NaN.
     *
     * <p><b>Length semantics:</b> {@code 0 <= s.length() <= maxLength} (empty string included).
     * <b>Character semantics:</b> every in-range index satisfies
     * {@code 0x20 <= s.charAt(i) <= 0x7E}.
     *
     * <p><b>Soundness:</b> enforced exactly like {@link #anyString(int, String)} but with a single
     * range comparison per position instead of an alphabet membership test (cheaper than enumerating
     * the 95 printable chars). The character is read with the sound {@code CProverString.charAt}
     * primitive, so later sound reads/compares of the string honour the bound.
     *
     * <pre>{@code
     * String name = Bmc.anyAsciiString(8);
     * for (int i = 0; i < name.length(); i++) {
     *     char c = name.charAt(i);
     *     Bmc.check(c >= 0x20 && c <= 0x7E);   // holds for all of them
     * }
     * }</pre>
     *
     * @throws IllegalArgumentException if {@code maxLength < 0}
     */
    public static String anyAsciiString(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("require maxLength >= 0, got " + maxLength);
        }
        String s = CProver.nondetWithoutNull();
        int len = s.length();
        CProver.assume(len >= 0 && len <= maxLength);
        for (int i = 0; i < maxLength; i++) {
            if (i < len) {
                char c = CProverString.charAt(s, i);
                CProver.assume(c >= ASCII_PRINTABLE_MIN && c <= ASCII_PRINTABLE_MAX);
            }
        }
        return s;
    }

    // --- configuration readers (pinned to the run's REAL environment) --------
    // These read the ACTUAL value the proof run was launched with — an environment variable or a
    // system property — and pin the proof input to it. So a proof checks your logic against THIS
    // deployment's config, not over all possible values (use anyInt()/anyString() for "prove for
    // every value"). bmc4j resolves the value at analysis-setup time and bakes it into the proof
    // (see ConfigBytecode); the variable MUST be set (and parse to the requested type) or the proof
    // fails. The key must be a string literal.
    //
    //   int port = Bmc.intFromEnv("PORT");        // == the PORT this run was given
    //   Bmc.check(ServerConfig.clampPort(port) == port);   // proven for that concrete port

    /** The value of environment variable {@code key} (as set for this run), interpreted as an {@code int}. */
    public static int intFromEnv(String key) {
        return CProver.nondetInt();
    }

    /** The value of system property {@code key} (as set for this run), interpreted as an {@code int}. */
    public static int intFromProperty(String key) {
        return CProver.nondetInt();
    }

    /** The value of environment variable {@code key}, interpreted as a {@code long}. */
    public static long longFromEnv(String key) {
        return CProver.nondetLong();
    }

    /** The value of system property {@code key}, interpreted as a {@code long}. */
    public static long longFromProperty(String key) {
        return CProver.nondetLong();
    }

    /** The value of environment variable {@code key}, interpreted as a {@code boolean}.
     *  The value must be exactly {@code "true"} or {@code "false"} (case-insensitive) —
     *  anything else (e.g. {@code "1"}, {@code "yes"}) fails the proof rather than
     *  silently reading as {@code false}. */
    public static boolean boolFromEnv(String key) {
        return CProver.nondetBoolean();
    }

    /** The value of system property {@code key}, interpreted as a {@code boolean};
     *  same strict {@code "true"}/{@code "false"} format as {@link #boolFromEnv}. */
    public static boolean boolFromProperty(String key) {
        return CProver.nondetBoolean();
    }

    /** The value of environment variable {@code key}, interpreted as a {@code double}. */
    public static double doubleFromEnv(String key) {
        return CProver.nondetDouble();
    }

    /** The value of system property {@code key}, interpreted as a {@code double}. */
    public static double doubleFromProperty(String key) {
        return CProver.nondetDouble();
    }

    /** The value of environment variable {@code key} (as set for this run), as a {@code String}. */
    public static String stringFromEnv(String key) {
        return CProver.nondetWithoutNull();
    }

    /** The value of system property {@code key} (as set for this run), as a {@code String}. */
    public static String stringFromProperty(String key) {
        return CProver.nondetWithoutNull();
    }

    /** Restrict the proof to inputs for which the condition holds. */
    public static void assume(boolean __assumption) {
        CProver.assume(__assumption);
    }

    /**
     * Prune the current path: tell the analysis this point is unreachable. Equivalent to
     * {@code assume(false)}. Use it where a path represents an input you want to exclude — e.g. in a
     * {@code catch} for a constructor whose validation rejected the input, so only valid instances
     * survive (see {@code assumeValid} in the Kotlin helpers).
     */
    public static void assumeUnreachable() {
        CProver.assume(false);
    }

    /** Assert a property under check. Fails the proof if any allowed input violates it. */
    public static void check(boolean __property) {
        if (!__property) {
            throw new AssertionError("BMC check failed");
        }
    }

    /** Assert a property under check, with a message shown in the failure. */
    public static void check(boolean __property, String message) {
        if (!__property) {
            throw new AssertionError(message);
        }
    }
}
