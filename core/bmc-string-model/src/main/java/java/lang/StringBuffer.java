// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j. NOT copied or adapted from the OpenJDK StringBuffer,
// from diffblue/cbmc, or from the java-models-library.
package java.lang;

/**
 * Sound char-array-backed BMC model of {@link java.lang.StringBuffer} for JBMC's analysis classpath,
 * used ONLY under --no-refine-strings. A bmc4j proof verifies one symbolic thread of execution, so the
 * synchronization StringBuffer adds over {@link StringBuilder} is irrelevant; the model is the same
 * char-array-backed build path. See {@link String} / {@link AbstractStringBuilder}.
 */
public final class StringBuffer extends AbstractStringBuilder
        implements java.io.Serializable, Comparable<StringBuffer>, CharSequence {

    public StringBuffer() {
        super(16);
    }

    public StringBuffer(int capacity) {
        super(capacity);
    }

    public StringBuffer(String str) {
        super(16 + (str == null ? 0 : str.length()));
        append(str);
    }

    public StringBuffer(CharSequence seq) {
        super(16 + (seq == null ? 0 : seq.length()));
        append(seq);
    }

    @Override
    public StringBuffer append(char c) {
        super.append(c);
        return this;
    }

    @Override
    public StringBuffer append(String str) {
        super.append(str);
        return this;
    }

    @Override
    public StringBuffer append(CharSequence s) {
        super.append(s);
        return this;
    }

    @Override
    public StringBuffer append(CharSequence s, int start, int end) {
        super.append(s, start, end);
        return this;
    }

    @Override
    public StringBuffer append(char[] str) {
        super.append(str);
        return this;
    }

    @Override
    public int compareTo(StringBuffer another) {
        return this.toString().compareTo(another.toString());
    }

    @Override
    public String toString() {
        return new String(value, 0, count);
    }
}
