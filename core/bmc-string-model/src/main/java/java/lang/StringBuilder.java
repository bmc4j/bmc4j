// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j. NOT copied or adapted from the OpenJDK StringBuilder,
// from diffblue/cbmc, or from the java-models-library.
package java.lang;

/**
 * Sound char-array-backed BMC model of {@link java.lang.StringBuilder} for JBMC's analysis classpath,
 * used ONLY under --no-refine-strings. {@link #toString()} copies the live backing into a real
 * {@code new String(...)}, so a String built here has an exact char-array backing. See {@link String}
 * and {@link AbstractStringBuilder} for the soundness argument.
 */
public final class StringBuilder extends AbstractStringBuilder
        implements java.io.Serializable, Comparable<StringBuilder>, CharSequence {

    public StringBuilder() {
        super(16);
    }

    public StringBuilder(int capacity) {
        super(capacity);
    }

    public StringBuilder(String str) {
        super(16 + (str == null ? 0 : str.length()));
        append(str);
    }

    public StringBuilder(CharSequence seq) {
        super(16 + (seq == null ? 0 : seq.length()));
        append(seq);
    }

    @Override
    public StringBuilder append(char c) {
        super.append(c);
        return this;
    }

    @Override
    public StringBuilder append(String str) {
        super.append(str);
        return this;
    }

    @Override
    public StringBuilder append(CharSequence s) {
        super.append(s);
        return this;
    }

    @Override
    public StringBuilder append(CharSequence s, int start, int end) {
        super.append(s, start, end);
        return this;
    }

    @Override
    public StringBuilder append(char[] str) {
        super.append(str);
        return this;
    }

    @Override
    public int compareTo(StringBuilder another) {
        return this.toString().compareTo(another.toString());
    }

    @Override
    public String toString() {
        return new String(value, 0, count);
    }
}
