// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j against JBMC's documented intrinsic API
// (method names/signatures only); NOT copied or adapted from diffblue/cbmc or the
// java-models-library, so no CBMC license header applies.
package org.cprover;

/**
 * Self-contained stand-in for CBMC's {@code org.cprover.CProverString} intrinsics. JBMC
 * recognises these by fully-qualified name and lowers them to its sound string operations
 * during analysis (the bodies here only need to compile/load). Shipping our own copy keeps
 * the runtime a single publishable artifact, exactly like {@link CProver}.
 *
 * <p>We expose only what we build sound String operations on top of — {@code charAt} is the
 * no-exception character read JBMC handles soundly, used to synthesise a sound
 * {@code String.equals} that the engine itself mishandles (see the strings work / issue).
 */
public final class CProverString {

    private CProverString() {
    }

    /**
     * Character at {@code index} of {@code s}, JBMC's no-bounds-exception variant.
     *
     * <p>Under string refinement (the default) JBMC recognises this by FQN and lowers it to its sound
     * built-in character read; the body is ignored. With refinement OFF (StringMode.CHAR_ARRAY_MODEL) that intrinsic
     * lowering does NOT fire, so the literal body is linked - and the old placeholder ({@code '\0'}) was
     * then unsound: every {@code BmcStrings} content op (equals/contains/hashCode), which is rebuilt from
     * {@code CProverString.charAt}, read a constant nul. We instead delegate to {@link String#charAt}.
     * Under no-refine the bundled char-array String model backs that with a real array (a sound array
     * read), so {@code BmcStrings} composes soundly; under refinement the delegation is moot (the body is
     * never executed). The bounds variance ({@code charAt} throws on out-of-range, this intrinsic is the
     * "no-exception" form) is irrelevant to {@code BmcStrings}, whose loops only ever pass in-range
     * indices ({@code i < length()}).
     */
    public static char charAt(String s, int index) {
        return s.charAt(index);
    }

    /**
     * Sound {@code int -> String}, the conversion JBMC's frontend emits for {@code Integer.toString(int)}
     * / {@code String.valueOf(int)} (it lowers those to a call on this fully-qualified intrinsic). bmc4j
     * ships its own {@link CProverString} to shadow CBMC's unsound internal string model; before this
     * method existed the class was present but lacked it, so the call site landed on a missing method and
     * JBMC nondet-stubbed it, turning every int-to-String proof UNKNOWN.
     *
     * <p>JBMC recognises this method by name+descriptor and lowers it to its sound built-in
     * {@code of_int} conversion, exactly like {@link #charAt} (so the body only has to compile/load). We
     * nonetheless give it a genuinely sound body, built from the one construction primitive JBMC models
     * soundly ({@code StringBuilder.append(char)} + {@code toString()}, the same machinery
     * {@code BmcStrings} uses) rather than delegating to {@code Integer.toString}/{@code String.valueOf},
     * which would route straight back to the unsound native path this class exists to avoid. The result
     * therefore matches {@code Integer.toString(int)} for every {@code int}, even if a devirtualization
     * fallback ever links this bytecode instead of the intrinsic.
     *
     * <p>Soundness edge: {@link Integer#MIN_VALUE} cannot be negated ({@code -MIN_VALUE} overflows back to
     * itself, the two's-complement trap), so we never negate. We peel decimal digits off the value while
     * keeping it negative (the side that can hold the full magnitude of every {@code int}), writing them
     * least-significant first into a fixed 11-char buffer (10 digits + a possible sign) from the back, then
     * append the populated tail in order via {@code StringBuilder.append(char)} (the sound primitive, not
     * {@code reverse()}). Bounded by design: the digit loop runs at most 10 times, so it is a tight,
     * tractable unwind.
     */
    public static String toString(int i) {
        if (i == 0) {
            return "0";
        }
        boolean negative = i < 0;
        // Work on the negative side so Integer.MIN_VALUE is representable (its positive twin is not).
        int n = negative ? i : -i;
        char[] buf = new char[11];          // max int is 10 digits, plus a possible '-' sign
        int pos = buf.length;
        while (n < 0) {
            int digit = -(n % 10);          // n is <= 0, so n % 10 is in [-9, 0]; negate to a 0..9 digit
            buf[--pos] = (char) ('0' + digit);
            n = n / 10;
        }
        if (negative) {
            buf[--pos] = '-';
        }
        StringBuilder sb = new StringBuilder();
        for (int k = pos; k < buf.length; k++) {
            sb.append(buf[k]);              // append(char) + toString(): the one sound construction path
        }
        return sb.toString();
    }

    /**
     * Sound {@code long -> String}, the {@code long} twin of {@link #toString(int)}: the conversion JBMC's
     * frontend emits for {@code Long.toString(long)} / {@code String.valueOf(long)} (lowered to this
     * fully-qualified intrinsic by name+descriptor). Same rationale and same bounded body as the
     * {@code int} form, widened to {@code long}: peel digits keeping the value NEGATIVE so
     * {@link Long#MIN_VALUE} is representable (its positive twin overflows), write them into a fixed
     * 20-char buffer (19 digits + a possible sign) from the back, then append the populated tail via the
     * sound {@code StringBuilder.append(char)} primitive. Bounded: the digit loop runs at most 19 times.
     */
    public static String toString(long i) {
        if (i == 0) {
            return "0";
        }
        boolean negative = i < 0;
        long n = negative ? i : -i;
        char[] buf = new char[20];          // max long is 19 digits, plus a possible '-' sign
        int pos = buf.length;
        while (n < 0) {
            int digit = (int) -(n % 10);    // n is <= 0, so n % 10 is in [-9, 0]; negate to a 0..9 digit
            buf[--pos] = (char) ('0' + digit);
            n = n / 10;
        }
        if (negative) {
            buf[--pos] = '-';
        }
        StringBuilder sb = new StringBuilder();
        for (int k = pos; k < buf.length; k++) {
            sb.append(buf[k]);
        }
        return sb.toString();
    }
}
