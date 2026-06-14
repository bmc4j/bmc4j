// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j. NOT copied or adapted from the OpenJDK
// AbstractStringBuilder, from diffblue/cbmc, or from the java-models-library.
package java.lang;

/**
 * Sound char-array-backed BMC model of {@link java.lang.AbstractStringBuilder} (the shared base of
 * {@link StringBuilder} / {@link StringBuffer}) for JBMC's analysis classpath, used ONLY under
 * --no-refine-strings. See {@link String} for the why and the soundness argument.
 *
 * <p>This is the one CONSTRUCTION primitive bmc4j's sound String layer is built on:
 * {@code append(char)} grows a real {@code char[]} and {@link #toString()} (in the concrete subclasses)
 * copies it into a {@code new String(value, 0, count)}. So a String built via
 * {@code StringBuilder().append('a').append('b').toString()} has an exact char-array backing and sound
 * {@code length()}/{@code charAt()} under no-refine - the property the cbmc model's
 * {@code toString() -> nondetWithNull} broke (it returned a possibly-null nondet, the NPE source).
 */
abstract class AbstractStringBuilder implements Appendable, CharSequence {

    char[] value;
    int count;

    AbstractStringBuilder() {
        value = new char[16];
        count = 0;
    }

    AbstractStringBuilder(int capacity) {
        value = new char[capacity < 0 ? 0 : capacity];
        count = 0;
    }

    public int length() {
        return count;
    }

    public int capacity() {
        return value.length;
    }

    public char charAt(int index) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return value[index];
    }

    public CharSequence subSequence(int start, int end) {
        return substring(start, end);
    }

    public String substring(int start) {
        return substring(start, count);
    }

    public String substring(int start, int end) {
        if (start < 0 || end > count || start > end) {
            throw new StringIndexOutOfBoundsException();
        }
        return new String(value, start, end - start);
    }

    public void setCharAt(int index, char ch) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(index);
        }
        value[index] = ch;
    }

    /** Grow the backing so at least {@code minCapacity} chars fit; preserve the existing prefix. */
    private void ensureCapacityInternal(int minCapacity) {
        if (minCapacity > value.length) {
            int newCapacity = value.length * 2 + 2;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            char[] nv = new char[newCapacity];
            for (int i = 0; i < count; i++) {
                nv[i] = value[i];
            }
            value = nv;
        }
    }

    public AbstractStringBuilder append(char c) {
        ensureCapacityInternal(count + 1);
        value[count++] = c;
        return this;
    }

    public AbstractStringBuilder append(String str) {
        if (str == null) {
            str = "null";
        }
        int len = str.length();
        ensureCapacityInternal(count + len);
        // Bulk-copy off the String's backing array directly (mirrors the real JDK getChars/arraycopy);
        // do NOT route per char through the public bounds-checking charAt, whose throw branch under
        // StringMode.NONE blows symex up against a symbolic length. The range is in-bounds by construction.
        str.getChars(0, len, value, count);
        count += len;
        return this;
    }

    public AbstractStringBuilder append(CharSequence s) {
        if (s == null) {
            s = "null";
        }
        return append(s, 0, s.length());
    }

    public AbstractStringBuilder append(CharSequence s, int start, int end) {
        if (s == null) {
            s = "null";
        }
        if (start < 0 || end > s.length() || start > end) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacityInternal(count + (end - start));
        if (s instanceof String) {
            // Bulk-copy off the backing array directly (see append(String) / String.getChars).
            ((String) s).getChars(start, end, value, count);
            count += (end - start);
        } else {
            // A generic CharSequence has no exposed backing array; its public charAt is the only access
            // path. Real JDK does the same per-char read here.
            for (int i = start; i < end; i++) {
                value[count++] = s.charAt(i);
            }
        }
        return this;
    }

    public AbstractStringBuilder append(char[] str) {
        if (str == null) {
            return append("null");
        }
        ensureCapacityInternal(count + str.length);
        for (int i = 0; i < str.length; i++) {
            value[count++] = str[i];
        }
        return this;
    }

    @Override
    public abstract String toString();
}
