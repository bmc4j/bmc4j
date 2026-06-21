// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j. NOT copied or adapted from the OpenJDK String, from
// diffblue/cbmc, or from the java-models-library.
package java.lang;

/**
 * Sound char-array-backed BMC model of {@link java.lang.String} for JBMC's analysis classpath, used
 * ONLY when string refinement is OFF (--no-refine-strings / StringMode.CHAR_ARRAY_MODEL). Under refinement (the
 * default) JBMC supplies its own sound String model and this class is NOT on the classpath.
 *
 * <p><b>The model.</b> A String is backed by a genuine {@code char[] value}. {@link #length()} returns
 * {@code value.length}; {@link #charAt(int)} returns {@code value[i]} (a sound array read with the
 * normal bounds behaviour); construction from a {@code char[]} copies it into the backing. JBMC then
 * analyses these as plain, tractable, sound array operations - the same machinery that already verifies
 * char-array / writeByte proofs - instead of the degenerate intrinsic shells the cbmc core-models carry
 * (length -> nondetInt, charAt -> a placeholder, no real backing), which under no-refine produce a null
 * backing and a spurious {@link NullPointerException}.
 *
 * <p><b>Covered soundly:</b> {@code new String(char[])} / {@code new String(char[],int,int)} and the
 * {@code StringBuilder.append(char) + toString()} construction primitive (see the StringBuilder model),
 * then {@link #length()}, {@link #charAt(int)}, {@link #isEmpty()}, {@link #equals(Object)},
 * {@link #hashCode()}. These are the reads/comparisons every higher String operation in bmc4j's sound
 * layer ({@code BmcStrings}, {@code anyString}) is rebuilt from, so that layer composes soundly here.
 * Also {@link #toLowerCase()} / {@link #toLowerCase(java.util.Locale)}, which map char-by-char (sound
 * and LENGTH-PRESERVING for the common ASCII/BMP case, keeping the caller's length bound) and trap the
 * handful of locale-special / expanding / context-dependent chars LOUD - see those methods.
 *
 * <p><b>Lazy backing (literals / nondet strings).</b> A String literal and a {@code nondetWithoutNull()}
 * String are materialized by JBMC WITHOUT running a constructor, so their {@code value} field is null.
 * Rather than dereference null (the spurious NPE this model exists to remove), {@link #backing()} lazily
 * installs a fresh nondet-length array on first read and caches it. The result is SOUND but
 * content-unconstrained: such a String has a nondet length and nondet chars (its literal content is not
 * recovered, because JBMC does not expose it to the model under no-refine). This conservatively yields
 * UNKNOWN for a content claim about a literal, never a false answer. A String BUILT through a constructor
 * (the common bmc4j path) has its exact content.
 *
 * <p>Bounded by design: per-char loops in the higher operations unwind to the length, so keep symbolic
 * string lengths small.
 */
public final class String implements CharSequence, Comparable<String> {

    /** The backing char array. Null for a literal / nondet String until {@link #backing()} installs a
     *  lazy nondet array on first read (hence non-final). A constructor sets it to the exact content. */
    private char[] value;

    public String() {
        this.value = new char[0];
    }

    public String(String original) {
        this.value = original.backing().clone();
    }

    public String(char[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        this.value = data.clone();
    }

    public String(char[] data, int offset, int count) {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || count < 0 || offset + count > data.length) {
            throw new StringIndexOutOfBoundsException();
        }
        char[] c = new char[count];
        for (int i = 0; i < count; i++) {
            c[i] = data[offset + i];
        }
        this.value = c;
    }

    /**
     * The backing array, lazily materialized for a literal / nondet String (whose {@code value} JBMC
     * left null because no constructor ran). The lazy array has a nondet, non-negative length and nondet
     * chars; it is cached so repeated reads of the same String are CONSISTENT (length() then charAt(i)
     * see one array). Sound but content-unconstrained - see the class note.
     */
    private char[] backing() {
        if (value == null) {
            int n = org.cprover.CProver.nondetInt();
            org.cprover.CProver.assume(n >= 0);
            value = new char[n];
        }
        return value;
    }

    public int length() {
        return backing().length;
    }

    public boolean isEmpty() {
        return backing().length == 0;
    }

    public char charAt(int index) {
        char[] b = backing();
        if (index < 0 || index >= b.length) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return b[index];
    }

    public CharSequence subSequence(int beginIndex, int endIndex) {
        return substring(beginIndex, endIndex);
    }

    public String substring(int beginIndex) {
        return substring(beginIndex, length());
    }

    public String substring(int beginIndex, int endIndex) {
        char[] b = backing();
        if (beginIndex < 0 || endIndex > b.length || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException();
        }
        // Copy the sub-range once and ADOPT it clone-free, instead of new String(b,off,count) -> ofChars's
        // StringBuilder append + toString (a wasted second copy: the toString is a String.<init> loop).
        // The copy is an element-wise loop, NOT System.arraycopy: under StringMode.CHAR_ARRAY_MODEL (string
        // refinement OFF) JBMC's char[] System.arraycopy leaves the destination chars UNCONSTRAINED (nondet),
        // so a substring read back its own chars unsoundly (the same hole that made getChars unsound below).
        // A plain indexed copy is modeled element-by-element and is sound. Bounded by `count`.
        int count = endIndex - beginIndex;
        char[] out = new char[count];
        for (int i = 0; i < count; i++) {
            out[i] = b[beginIndex + i];
        }
        return adoptChars(out);
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof String)) {
            return false;
        }
        String other = (String) anObject;
        char[] a = backing();
        char[] o = other.backing();
        if (a.length != o.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != o[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        char[] b = backing();
        int h = 0;
        for (int i = 0; i < b.length; i++) {
            h = 31 * h + b[i];
        }
        return h;
    }

    @Override
    public int compareTo(String anotherString) {
        char[] a = backing();
        char[] o = anotherString.backing();
        int lim = Math.min(a.length, o.length);
        for (int i = 0; i < lim; i++) {
            if (a[i] != o[i]) {
                return a[i] - o[i];
            }
        }
        return a.length - o.length;
    }

    @Override
    public String toString() {
        return this;
    }

    /**
     * {@code int -> String}, routed through the refinement primitive {@code CProverString.toString(int)}
     * so it funnels into the SAME choke point {@code Integer.toString} does. Under no-refine the
     * {@code @ConditionalOn} pass redirects that primitive to the bounded {@code BmcStrings.ofInt} digit
     * build, so a {@code String.valueOf(int)} result is length-bounded (<= 11) here too.
     *
     * <p>Required because this model SHADOWS {@code java.lang.String} wholesale under no-refine: without
     * its own {@code valueOf(int)} the call would land on a missing member and JBMC would nondet-stub it
     * to an unconstrained-length String (the very blowup the bound exists to remove).
     */
    public static String valueOf(int i) {
        return org.cprover.CProverString.toString(i);
    }

    /** {@code long -> String}, the {@code long} twin of {@link #valueOf(int)} (redirected under no-refine
     *  to the bounded {@code BmcStrings.ofLong}, <= 20 chars). */
    public static String valueOf(long l) {
        return org.cprover.CProverString.toString(l);
    }

    /**
     * Clone-free construction from a char array the CALLER exclusively owns: the backing is ADOPTED
     * ({@code value = data}) with NO defensive copy. For bmc4j-internal literal construction only
     * (see {@code StringLengthBytecode.emitFixedString}), where the array is freshly built per call and
     * never aliased - so skipping the copy is sound. The public {@code String(char[])} constructor keeps
     * its defensive {@code data.clone()} for USER code, which may pass a shared array.
     *
     * <p>Removes the {@code array[char].clone} the copying constructor incurred for a fixed string literal
     * under no-refine (the literal's chars are concrete and it owns the array outright, so the clone was
     * pure waste). Not a {@code java.lang.String} member - reached only by a bytecode-emitted
     * {@code INVOKESTATIC} on the no-refine analysis classpath, where this model shadows
     * {@code java.lang.String}.
     */
    public static String adoptChars(char[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        String s = new String();
        s.value = data;   // adopt: no clone, the caller hands over sole ownership of a fresh array
        return s;
    }

    public char[] toCharArray() {
        return backing().clone();
    }

    /**
     * Lowercases this String char-by-char, producing a SAME-LENGTH result. This is the whole point of
     * modelling it here: without a model the call degrades to a nondet stub that returns an
     * UNCONSTRAINED-length String, discarding the caller's length bound (an {@code anyString(2)} would
     * become any-length after {@code toLowerCase}). The char-array model keeps the length bound flowing.
     *
     * <p><b>Why the fold is open-coded (not {@code Character.toLowerCase}).</b> Under no-refine
     * ({@code StringMode.CHAR_ARRAY_MODEL}) JBMC links the cbmc core-models {@code java.lang.Character},
     * whose {@code toLowerCase} dereferences a null Unicode case table and NPEs - it is NOT a sound
     * intrinsic on this classpath (the {@code Character.toLowerCase} that IS sound lives on the
     * refinement path, which is OFF here). So the model computes the fold itself, over the ONLY domain
     * it can do so soundly without a Unicode table: 7-bit ASCII.
     *
     * <p><b>Soundness boundary (verified differentially against the real JDK across all 1069 available
     * {@code Locale}s, 2026-06).</b> Every ASCII char EXCEPT {@code 'I'} lowercases identically under
     * every locale, and {@code 'A'..'Z'} fold by the constant +32 (the rest are unchanged) - so the
     * model is EXACT and length-preserving for ASCII, the typical version-string / identifier domain.
     * Everything else is trapped LOUD (the {@code BmcUnmodelledReached} sentinel demotes the proof to
     * UNKNOWN, never a false VERIFIED - bmc4j's "loud, not silent" discipline):
     * <ul>
     *   <li>{@code 'I'} ({@code U+0049}) - Turkish/Azeri lowercase it to dotless {@code U+0131}, not
     *       {@code 'i'}. The no-arg {@link #toLowerCase()} uses the host's default locale (unknowable at
     *       verification time) and the {@link #toLowerCase(java.util.Locale)} overload could carry any
     *       locale, so {@code 'I'} cannot be folded soundly - trapped.</li>
     *   <li>Any NON-ASCII char ({@code >= 0x80}) - folding it soundly needs the Unicode case table this
     *       model deliberately does not carry, and some (the dotted-I family {@code U+00CC/00CD/0128},
     *       the expanding {@code U+0130}, the context-dependent capital sigma {@code U+03A3}) genuinely
     *       diverge from any simple char map - so all non-ASCII is conservatively trapped.</li>
     * </ul>
     * No locale broadens the ASCII-minus-{@code 'I'} safe domain (it is the agreement set over ALL
     * locales), so {@link #toLowerCase()} and {@link #toLowerCase(java.util.Locale)} share this one
     * implementation soundly. A caller needing non-ASCII case folding gets an honest UNKNOWN, never a
     * wrong VERIFIED.
     */
    public String toLowerCase() {
        char[] b = backing();
        char[] out = new char[b.length];
        for (int i = 0; i < b.length; i++) {
            char c = b[i];
            // Loud-trap 'I' (locale-sensitive: Turkish dotless-i) and ALL non-ASCII (needs a Unicode
            // case table this model does not carry, and the expanding / context-dependent cases). The
            // proof demotes to UNKNOWN, never a wrong result.
            if (c == 0x0049 || c > 0x007F) {
                throw org.bmc4j.analysis.BmcUnmodelledReached.fail(
                        "bmc4j: unmodelled member java.lang.String.toLowerCase - char U+"
                                + Integer.toHexString(c)
                                + " has a locale-sensitive or non-ASCII lowercase the char-array String "
                                + "model cannot fold soundly (ASCII-minus-'I' is the precise domain)");
            }
            // Sound, length-preserving ASCII fold: 'A'..'Z' -> +32 ('I' already trapped), else unchanged.
            out[i] = (c >= 0x0041 && c <= 0x005A) ? (char) (c + 32) : c;
        }
        // ADOPT the array we just built, clone-free. `new String(out)` would route (under no-refine) through
        // ofChars's StringBuilder append + toString rebuild, whose toString copy is a String.<init> loop that
        // dominates the profile under symbolic length (pre-sizing the builder can't fold a symbolic-length
        // dead growth branch). We already own `out` and its length, so adopt it directly: no builder, no copy.
        return adoptChars(out);
    }

    /**
     * Locale-parameterised lowercase. Routes to {@link #toLowerCase()}: the model's precise char domain
     * (ASCII minus {@code 'I'}) is the agreement set over ALL locales and everything else is trapped
     * loud, so the result is identical and sound for every {@code Locale}. See {@link #toLowerCase()} for
     * the full boundary.
     *
     * <p>The {@code locale} argument is intentionally not dereferenced: there is no {@code java.util.Locale}
     * model on the no-refine classpath, so {@code Locale} statics (e.g. {@code Locale.ROOT}) are nondet and
     * a null-check here would spuriously REFUTE on that modeling artifact, not on a real caller bug. The
     * real JDK's {@code toLowerCase(null)} NPE is therefore the one behaviour this overload does not
     * reproduce - a conservative gap that can never make a content/length claim WRONG.
     */
    public String toLowerCase(java.util.Locale locale) {
        return toLowerCase();
    }

    /**
     * Copy chars {@code [srcBegin, srcEnd)} of this String into {@code dst} starting at
     * {@code dstBegin}, reading the backing array DIRECTLY (no per-char public {@link #charAt(int)}).
     *
     * <p>It exists so that INTERNAL char copies (e.g. {@code AbstractStringBuilder.append(String)}, and
     * {@code BmcStrings.equals}/{@code charAtRaw} which read content through here under no-refine) do not
     * route through the public bounds-checking {@code charAt}. Under StringMode.CHAR_ARRAY_MODEL a public
     * {@code charAt} bounds-checks against the SYMBOLIC backing length and symex cannot prove the loop index
     * in range, so it explores the throw branch on every copied char and recursively builds a
     * StringIndexOutOfBoundsException message (itself an append/charAt) - a blowup that never happens in
     * real execution. Here the index range is in-bounds by construction (the caller copies exactly
     * {@code length()} chars off the same backing), so the direct array read has no throw branch.
     *
     * <p><b>Element-wise, NOT {@code System.arraycopy}.</b> The real JDK {@code String.getChars}
     * bulk-copies via {@code System.arraycopy}, but under StringMode.CHAR_ARRAY_MODEL (string refinement
     * OFF) JBMC's char[] {@code System.arraycopy} leaves the destination chars UNCONSTRAINED (nondet) - it
     * does not relate {@code dst[dstBegin+k]} to {@code src[srcBegin+k]}. That made every content read that
     * goes through {@code getChars} unsound: {@code BmcStrings.equals} reads both operands' chars here, so a
     * differing-content inequality like {@code !"hi".equals("ho")} could be REFUTED (the solver nondets the
     * copied chars to make them equal), even though the public {@code charAt} (a plain {@code b[i]} read) is
     * sound. A plain indexed copy loop is modeled element-by-element, so each {@code dst} char IS the
     * corresponding backing char. Bounded by the copied length.
     *
     * <p>Declared {@code public} to match the real {@code java.lang.String.getChars}, which is public.
     * It must be: JDK 25 added {@code CharSequence.getChars}, and {@code String implements CharSequence},
     * so a package-private declaration narrows that inherited public method and fails to compile on 25.
     */
    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        char[] b = backing();
        for (int i = srcBegin; i < srcEnd; i++) {
            dst[dstBegin + (i - srcBegin)] = b[i];
        }
    }
}
