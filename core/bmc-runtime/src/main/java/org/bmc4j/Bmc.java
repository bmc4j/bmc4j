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

    // --- symbolic arrays (CONCRETE length) -----------------------------------
    // anyArrayOfInts/anyArrayOfLongs allocate a real array whose every element is symbolic, so one
    // proof ranges over EVERY array of that length. Three things to keep in mind:
    //
    //   1. CONCRETE length only. `length` must be an ordinary int literal in the proof (e.g. 4, 8) —
    //      do NOT pass a symbolic/nondet length. Symbolic-size allocation plus a nondet-bounded fill
    //      loop is a BMC sore spot (the engine cannot statically unwind the fill). To "cover multiple
    //      sizes", write separate proofs (foo_size4, foo_size8), each with its own literal length.
    //   2. The fill loop CONSUMES THE UNWIND BUDGET. A length-N array runs N fill iterations, and the
    //      sortedness assumes below add another N-1 — both count against the proof's `unwind` bound.
    //      Size your `@BmcProof(unwind = …)` to cover the longest array loop in the proof, not just N.
    //   3. length == 0 yields an empty array (a valid, if degenerate, proof input).

    /**
     * A symbolic {@code int[]} of exactly {@code length} elements, each an unconstrained
     * {@link #anyInt()}. One proof over this array reasons about every {@code int[]} of that length.
     *
     * <p><b>Concrete length only:</b> {@code length} must be an ordinary int literal per proof — see
     * the section comment; symbolic length is unsupported. <b>Budget:</b> the fill loop adds
     * {@code length} iterations to the proof's unwind budget. {@code length == 0} returns an empty
     * array.
     *
     * @throws IllegalArgumentException if {@code length < 0}
     */
    public static int[] anyArrayOfInts(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("require length >= 0, got " + length);
        }
        int[] a = new int[length];
        for (int i = 0; i < length; i++) {
            a[i] = anyInt();
        }
        return a;
    }

    /**
     * A symbolic {@code int[]} of exactly {@code length} elements, each constrained to
     * {@code [loInclusive, hiInclusive]} via {@link #anyInt(int, int)}. <b>This ranged form is the
     * one to reach for</b>: bounding the elements keeps the symbolic domain small enough that the
     * solver stays fast, the way {@link #anyInt(int, int)} does for a scalar.
     *
     * <p><b>Concrete length only:</b> {@code length} must be an ordinary int literal per proof — see
     * the section comment; symbolic length is unsupported. <b>Budget:</b> the fill loop adds
     * {@code length} iterations to the proof's unwind budget. {@code length == 0} returns an empty
     * array. {@code anyInt(lo, hi)} enforces {@code lo <= hi}.
     *
     * @throws IllegalArgumentException if {@code length < 0}
     */
    public static int[] anyArrayOfInts(int length, int loInclusive, int hiInclusive) {
        if (length < 0) {
            throw new IllegalArgumentException("require length >= 0, got " + length);
        }
        int[] a = new int[length];
        for (int i = 0; i < length; i++) {
            a[i] = anyInt(loInclusive, hiInclusive);
        }
        return a;
    }

    /**
     * A symbolic {@code long[]} of exactly {@code length} elements, each an unconstrained
     * {@link #anyLong()}. The {@code long} analogue of {@link #anyArrayOfInts(int)}.
     *
     * <p><b>Concrete length only:</b> {@code length} must be an ordinary int literal per proof — see
     * the section comment; symbolic length is unsupported. <b>Budget:</b> the fill loop adds
     * {@code length} iterations to the proof's unwind budget. {@code length == 0} returns an empty
     * array.
     *
     * @throws IllegalArgumentException if {@code length < 0}
     */
    public static long[] anyArrayOfLongs(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("require length >= 0, got " + length);
        }
        long[] a = new long[length];
        for (int i = 0; i < length; i++) {
            a[i] = anyLong();
        }
        return a;
    }

    /**
     * A symbolic {@code long[]} of exactly {@code length} elements, each constrained to
     * {@code [loInclusive, hiInclusive]} via {@link #anyLong(long, long)}. The {@code long} analogue
     * of {@link #anyArrayOfInts(int, int, int)} — prefer the ranged form to keep proofs tractable.
     *
     * <p><b>Concrete length only:</b> {@code length} must be an ordinary int literal per proof — see
     * the section comment; symbolic length is unsupported. <b>Budget:</b> the fill loop adds
     * {@code length} iterations to the proof's unwind budget. {@code length == 0} returns an empty
     * array. {@code anyLong(lo, hi)} enforces {@code lo <= hi}.
     *
     * @throws IllegalArgumentException if {@code length < 0}
     */
    public static long[] anyArrayOfLongs(int length, long loInclusive, long hiInclusive) {
        if (length < 0) {
            throw new IllegalArgumentException("require length >= 0, got " + length);
        }
        long[] a = new long[length];
        for (int i = 0; i < length; i++) {
            a[i] = anyLong(loInclusive, hiInclusive);
        }
        return a;
    }

    // --- sortedness assumptions ----------------------------------------------
    // assumeSorted/assumeStrictlySorted narrow a symbolic array's domain to (strictly) ascending
    // order — the standard precondition for binary/interpolation search and merge-style algorithms.
    // Two things to keep in mind:
    //
    //   1. The pairwise assume loop CONSUMES THE UNWIND BUDGET: a length-N array adds N-1 iterations,
    //      on top of whatever the fill loop already cost. Size `@BmcProof(unwind = …)` accordingly.
    //   2. The `<=` vs `<` choice is LOAD-BEARING. assumeSorted (non-strict, `a[i-1] <= a[i]`) allows
    //      duplicates; assumeStrictlySorted (`a[i-1] < a[i]`) forces distinct elements. Equal adjacent
    //      elements are exactly the corner that surfaces interpolation search's divide-by-zero, so
    //      pick deliberately: use the non-strict form unless your algorithm genuinely requires
    //      distinct keys, so the proof keeps covering the duplicate case.
    //
    // On length 0 or 1 there are no adjacent pairs, so both forms are a vacuous no-op.

    /**
     * Assume {@code a} is sorted in <b>non-strict ascending</b> order: {@code a[i-1] <= a[i]} for
     * every adjacent pair. Duplicates are allowed (so the proof still covers equal-key inputs).
     *
     * <p><b>Budget:</b> the pairwise loop adds {@code a.length - 1} iterations to the unwind budget.
     * <b>Length 0/1:</b> no adjacent pairs, so this is a vacuous no-op. <b>{@code <=} vs {@code <}:</b>
     * this is the non-strict form; use {@link #assumeStrictlySorted(int[])} when you need distinct
     * elements — the distinction is exactly what surfaces (or hides) equal-element corners such as
     * interpolation search's divide-by-zero.
     */
    public static void assumeSorted(int[] a) {
        for (int i = 1; i < a.length; i++) {
            CProver.assume(a[i - 1] <= a[i]);
        }
    }

    /**
     * Assume {@code a} is sorted in <b>strict ascending</b> order: {@code a[i-1] < a[i]} for every
     * adjacent pair, so all elements are distinct.
     *
     * <p><b>Budget:</b> the pairwise loop adds {@code a.length - 1} iterations to the unwind budget.
     * <b>Length 0/1:</b> no adjacent pairs, so this is a vacuous no-op. <b>{@code <} vs {@code <=}:</b>
     * this strict form excludes equal adjacent elements; prefer {@link #assumeSorted(int[])} unless
     * your algorithm genuinely requires distinct keys, so the proof keeps covering the duplicate case.
     */
    public static void assumeStrictlySorted(int[] a) {
        for (int i = 1; i < a.length; i++) {
            CProver.assume(a[i - 1] < a[i]);
        }
    }

    /**
     * Assume {@code a} is sorted in <b>non-strict ascending</b> order: {@code a[i-1] <= a[i]} for
     * every adjacent pair (duplicates allowed). The {@code long} analogue of
     * {@link #assumeSorted(int[])}.
     *
     * <p><b>Budget:</b> the pairwise loop adds {@code a.length - 1} iterations to the unwind budget.
     * <b>Length 0/1:</b> a vacuous no-op. See {@link #assumeStrictlySorted(long[])} for the strict
     * (distinct) variant; the {@code <=} vs {@code <} choice is load-bearing.
     */
    public static void assumeSorted(long[] a) {
        for (int i = 1; i < a.length; i++) {
            CProver.assume(a[i - 1] <= a[i]);
        }
    }

    /**
     * Assume {@code a} is sorted in <b>strict ascending</b> order: {@code a[i-1] < a[i]} for every
     * adjacent pair (all elements distinct). The {@code long} analogue of
     * {@link #assumeStrictlySorted(int[])}.
     *
     * <p><b>Budget:</b> the pairwise loop adds {@code a.length - 1} iterations to the unwind budget.
     * <b>Length 0/1:</b> a vacuous no-op. Prefer {@link #assumeSorted(long[])} unless you genuinely
     * require distinct keys; the {@code <} vs {@code <=} choice is load-bearing.
     */
    public static void assumeStrictlySorted(long[] a) {
        for (int i = 1; i < a.length; i++) {
            CProver.assume(a[i - 1] < a[i]);
        }
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

    /**
     * A symbolic, NON-NULL reference of type {@code T} - a stand-in for an external/unanalyzable
     * dependency the proof holds a handle to (e.g. a repository or service interface with no analyzed
     * implementation), so a proof needs no hand-written concrete stub:
     *
     * <pre>{@code
     * UserRepository repo = Bmc.anyRef(UserRepository.class);
     * Bmc.assumeEvery(repo::findById, u -> u == null || u.age() >= 0);
     * }</pre>
     *
     * The result is a fresh nondet {@code T} over-approximating ANY implementation; its methods are
     * nondet stubs unless an {@link #assumeEvery} / {@link #assumeStable} assumed contract constrains
     * them, and the reference is non-null so a call on it doesn't trip a null-dereference check. The
     * class token names the intended type (the value is symbolic regardless); the rewrite layer
     * intrinsifies the call so the implicit erasure cast back to {@code T} holds. SOUND: a fresh nondet
     * over-approximation, never an unsound narrowing.
     */
    public static <T> T anyRef(Class<T> __type) {
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

    // --- domain split (slow-proof partitioning) ------------------------------
    // A proof that is too slow to discharge in one shot can be partitioned along the axis the user
    // knows is causing the SAT blow-up (a wide symbolic operand, a string length). domainSplit(...)
    // declares the claimed input domain (the "overall condition"); each slice(...) registers a
    // sub-domain. bmc4j then expands the ONE proof into N+1 derived runs it fans across cores:
    //   - N SLICE runs: the proof body re-verified under assume(slice_i) — "P holds over slice i".
    //   - 1 COVER run: check(overall => (slice_1 || ... || slice_n)) — the soundness gate that
    //     forbids GAPS (a point in the declared domain no slice covers). Overlap is allowed.
    // The proof passes iff the cover VERIFIED and every slice VERIFIED; a refuting slice surfaces its
    // counterexample (and cancels the rest, early-exit); an UNKNOWN slice => UNKNOWN.
    //
    // These are MARKERS, exactly like check/assume: the boolean argument is not evaluated at runtime —
    // the engine analyses the bytecode that COMPUTES it (see DomainSplitBytecode). At most ONE
    // domainSplit per proof; a slice with no preceding domainSplit, or a second domainSplit, is a
    // processing-time error. The reported verdict carries the overall condition's source so a
    // domain-scoped green is never mis-read as a full-domain proof.
    //
    //   int x = Bmc.anyInt();
    //   Bmc.domainSplit(x >= -1_000_000 && x <= 1_000_000);   // the claimed domain
    //   Bmc.slice(x < 0);
    //   Bmc.slice(x == 0);
    //   Bmc.slice(x > 0);
    //   Bmc.check(property(x));                                // body runs once per slice

    /**
     * Declare the claimed input domain of a slow proof and open its domain split. Every
     * {@link #slice(boolean)} call in the same method registers a sub-domain of this condition.
     * A marker: the boolean is analysed as bytecode, never executed. At most one per proof.
     */
    public static void domainSplit(boolean __overallCondition) {
        // No-op at runtime; the proof body is never executed. DomainSplitBytecode rewrites this call.
    }

    /**
     * Register one sub-domain of the enclosing {@link #domainSplit(boolean)}. The proof body is
     * re-verified once per slice under {@code assume(condition)}. A marker, like {@link #check}.
     */
    public static void slice(boolean __condition) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    // Fixed-arity slice overloads: a slice's defining constraints can be given as N separate booleans
    // (the slice is their CONJUNCTION). The rewriter emits them as N SEPARATE atomic assumes on the
    // slice run, never one compound assume(c1 && ... && cN) — CBMC's pre-SAT simplifier propagates an
    // atomic assumed bound (v >= lo) to prune downstream branches but does NOT crack open a conjoined
    // && to recover the individual bounds, so splitting them is what makes the bounds prune. The cover
    // contribution stays the AND of the N (a slice is the conjunction of its constraints). Fixed arity,
    // NOT varargs: a boolean[] would add array reasoning to the formula — the opposite of the goal.
    // Markers like slice(boolean): the booleans are analysed as bytecode, never executed.

    /** Register a sub-domain whose defining constraint is {@code c1 && c2} (emitted as 2 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /** Register a sub-domain whose defining constraint is {@code c1 && c2 && c3} (3 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2, boolean __c3) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /** Register a sub-domain whose defining constraint is the AND of {@code c1..c4} (4 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2, boolean __c3, boolean __c4) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /** Register a sub-domain whose defining constraint is the AND of {@code c1..c5} (5 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2, boolean __c3, boolean __c4, boolean __c5) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /** Register a sub-domain whose defining constraint is the AND of {@code c1..c6} (6 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2, boolean __c3, boolean __c4, boolean __c5,
                             boolean __c6) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /** Register a sub-domain whose defining constraint is the AND of {@code c1..c7} (7 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2, boolean __c3, boolean __c4, boolean __c5,
                             boolean __c6, boolean __c7) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /** Register a sub-domain whose defining constraint is the AND of {@code c1..c8} (8 atomic assumes). */
    public static void slice(boolean __c1, boolean __c2, boolean __c3, boolean __c4, boolean __c5,
                             boolean __c6, boolean __c7, boolean __c8) {
        // No-op at runtime; DomainSplitBytecode rewrites this call into the derived runs.
    }

    /**
     * Witness-tagging sink for a USER symbolic input (counterexample-witness plumbing).
     *
     * <p>{@code NondetTagBytecode} injects a call to one of these {@code recordNondet} overloads
     * immediately after each marked {@code Bmc.any*} call site, carrying the destination local's
     * source name and the freshly minted symbolic value: {@code Bmc.recordNondet("x", x)}. JBMC does
     * not intrinsify these methods, so the call surfaces in the {@code --json-ui} trace as a plain
     * {@code function-call} whose argument bindings ({@code arg0a} -> the
     * {@code java.lang.String.Literal.<name>} pointer, {@code arg1*} -> the value) {@code JbmcOutputParser}
     * harvests as a named input — robustly, regardless of whether the value is later boxed through a
     * {@code Triple}/carrier or minted in a helper. They are no-ops at runtime and verification-neutral:
     * an empty body the engine enters and returns from, never constraining the formula nor changing the
     * verdict.
     *
     * <p>The {@code long} overload is the common widening for every integral symbolic kind
     * ({@code int}/{@code long}/{@code short}/{@code byte}/{@code char}), so one sink serves them all;
     * dedicated overloads cover {@code boolean}/{@code float}/{@code double}, {@code String}, and the
     * object inputs ({@code anyOf}, including symbolic arrays, which carry their handle through the
     * {@code Object} overload so the heap reconstruction still renders {@code [..]}).
     */
    public static void recordNondet(String __name, long __value) {
        // No-op at runtime; JBMC enters/returns it so the (name, value) args land in the trace.
    }

    /** Witness sink for a {@code boolean} symbolic input. See {@link #recordNondet(String, long)}. */
    public static void recordNondet(String __name, boolean __value) {
        // No-op at runtime; JBMC enters/returns it so the (name, value) args land in the trace.
    }

    /** Witness sink for a {@code float} symbolic input. See {@link #recordNondet(String, long)}. */
    public static void recordNondet(String __name, float __value) {
        // No-op at runtime; JBMC enters/returns it so the (name, value) args land in the trace.
    }

    /** Witness sink for a {@code double} symbolic input. See {@link #recordNondet(String, long)}. */
    public static void recordNondet(String __name, double __value) {
        // No-op at runtime; JBMC enters/returns it so the (name, value) args land in the trace.
    }

    /**
     * Witness sink for a {@code String} symbolic input. See {@link #recordNondet(String, long)}. The
     * value surfaces in the trace as a {@code pointer} to a {@code String.Literal} the parser decodes.
     */
    public static void recordNondet(String __name, String __value) {
        // No-op at runtime; JBMC enters/returns it so the (name, value) args land in the trace.
    }

    /**
     * Witness sink for an OBJECT symbolic input — {@code anyOf}'s chosen element and symbolic ARRAYS
     * (the array handle rides through here so the trace still carries the variable's NAME, while the
     * per-element heap reconstruction renders {@code [..]}). See {@link #recordNondet(String, long)}.
     */
    public static void recordNondet(String __name, Object __value) {
        // No-op at runtime; JBMC enters/returns it so the (name, value) args land in the trace.
    }

    // --- assumed output-contracts (assumeEvery / assumeStable) ---------------
    // assumeEvery / assumeStable let a proof ASSUME an external/unanalyzable dependency upholds an
    // output property and prove on top of it — no model, no annotation, no string method name:
    //
    //   Bmc.assumeEvery(repo::findById, user -> user == null || user.age() >= 0);
    //   Bmc.assumeEvery(repo::findById, (user, id) -> user == null || user.id() == id);  // args-aware
    //   Bmc.assumeStable(Runtime.getRuntime()::availableProcessors, n -> n == 8);        // env case
    //
    // These are PROOF-WIDE DECLARATIONS, not sequential statements (like domainSplit/check): a call
    // appearing ANYWHERE in the proof installs the micro-model for the WHOLE analysis - including calls
    // inside <clinit> and inside callees the proof doesn't control. The first argument MUST be a direct
    // method reference (bound or static); the second an inline lambda predicate. bmc4j reads the
    // target STATICALLY from the reference's LambdaMetafactory bootstrap handle (it never executes the
    // invokedynamic) and shadows the target on the analysis classpath with a constrained-nondet stub
    // `R m(args){ R r = nondet(); assume(predicate(r [,args])); return r; }`. See AssumeContractBytecode.
    //
    //   - assumeEvery is FRESH PER CALL - each call returns any output satisfying the predicate, a SOUND
    //     OVER-APPROXIMATION: a property proven this way holds for any real implementation that respects
    //     the predicate. The right default for repositories / services / factories.
    //   - assumeStable pins ONE value for the whole run (memoized; reused at every call site incl.
    //     <clinit>). The right form for a deterministic query (availableProcessors(), a config constant)
    //     whose value a local assume can't reach.
    //
    // SOUNDNESS: the micro-model is an ASSUMPTION (constrained nondet via assume, never assert). A
    // VERIFIED reached under an assumed contract is FLAGGED on the verdict ("NOT unconditional"); an
    // over-tight predicate that rules out every output surfaces as VACUOUS. The predicate is NOT
    // purity-audited (unlike a dischargeable annotation contract): an assumed contract is a user-owned
    // assertion, so an impure or effectful predicate is allowed.
    //
    // These are MARKERS: the bodies below are never executed (the engine analyses the bytecode that
    // installs the contract). Arity 0..2 of the reference is covered, output-only and args-aware.

    /** A zero-argument method reference whose output an {@link #assumeStable} / {@link #assumeEvery}
     *  constrains — e.g. {@code Runtime.getRuntime()::availableProcessors}. */
    @FunctionalInterface
    public interface Ref0<R> {
        R get();
    }

    /** A one-argument method reference — e.g. {@code repo::findById}. */
    @FunctionalInterface
    public interface Ref1<A, R> {
        R apply(A a);
    }

    /** A two-argument method reference. */
    @FunctionalInterface
    public interface Ref2<A, B, R> {
        R apply(A a, B b);
    }

    /** An output-only predicate over a reference's result. */
    @FunctionalInterface
    public interface ResultPredicate<R> {
        boolean test(R result);
    }

    /** An args-aware predicate over a one-argument reference's result and call argument. */
    @FunctionalInterface
    public interface ResultArgPredicate1<R, A> {
        boolean test(R result, A a);
    }

    /** An args-aware predicate over a two-argument reference's result and call arguments. */
    @FunctionalInterface
    public interface ResultArgPredicate2<R, A, B> {
        boolean test(R result, A a, B b);
    }

    /**
     * Assume the zero-argument dependency {@code target} returns, on every call, a value satisfying
     * {@code predicate} — fresh per call. A proof-wide declaration (see the section comment); the marker
     * body is never executed.
     */
    public static <R> void assumeEvery(Ref0<R> __target, ResultPredicate<R> __predicate) {
        // Marker: AssumeContractBytecode reads the reference + predicate statically and installs the
        // constrained-nondet shadow for the whole analysis.
    }

    /** Assume the one-argument dependency {@code target} returns, on every call, a value satisfying the
     *  output-only {@code predicate} — fresh per call. */
    public static <A, R> void assumeEvery(Ref1<A, R> __target, ResultPredicate<R> __predicate) {
        // Marker — see AssumeContractBytecode.
    }

    /** Assume the one-argument dependency {@code target} returns, on every call, a value satisfying the
     *  args-aware {@code predicate} (result AND the call argument) — fresh per call. */
    public static <A, R> void assumeEvery(Ref1<A, R> __target, ResultArgPredicate1<R, A> __predicate) {
        // Marker — see AssumeContractBytecode.
    }

    /** Assume the two-argument dependency {@code target} returns, on every call, a value satisfying the
     *  output-only {@code predicate} — fresh per call. */
    public static <A, B, R> void assumeEvery(Ref2<A, B, R> __target, ResultPredicate<R> __predicate) {
        // Marker — see AssumeContractBytecode.
    }

    /** Assume the two-argument dependency {@code target} returns, on every call, a value satisfying the
     *  args-aware {@code predicate} (result AND both call arguments) — fresh per call. */
    public static <A, B, R> void assumeEvery(Ref2<A, B, R> __target,
                                             ResultArgPredicate2<R, A, B> __predicate) {
        // Marker — see AssumeContractBytecode.
    }

    /**
     * Assume the zero-argument deterministic query {@code target} returns ONE fixed value satisfying
     * {@code predicate} for the whole run — memoized, reused at every call site including {@code
     * <clinit>}. The environment / config case (e.g.
     * {@code assumeStable(Runtime.getRuntime()::availableProcessors, n -> n == 8)}).
     */
    public static <R> void assumeStable(Ref0<R> __target, ResultPredicate<R> __predicate) {
        // Marker — see AssumeContractBytecode.
    }

    /** Assume the one-argument dependency {@code target} returns ONE fixed value satisfying
     *  {@code predicate} for the whole run (memoized). */
    public static <A, R> void assumeStable(Ref1<A, R> __target, ResultPredicate<R> __predicate) {
        // Marker — see AssumeContractBytecode.
    }
}
