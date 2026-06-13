// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j. NOT copied or adapted from the OpenJDK String, from
// diffblue/cbmc, or from the java-models-library.
package java.lang;

/**
 * Sound char-array-backed BMC model of {@link java.lang.String} for JBMC's analysis classpath, used
 * ONLY when string refinement is OFF (--no-refine-strings / StringMode.NONE). Under refinement (the
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
        return new String(b, beginIndex, endIndex - beginIndex);
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

    public char[] toCharArray() {
        return backing().clone();
    }
}
