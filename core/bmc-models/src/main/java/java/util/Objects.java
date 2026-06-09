package java.util;

import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Sound, exact BMC model of {@link java.util.Objects} — the static null-safe / bounds-check utility
 * surface. Every member is a small total function over its arguments, so the model is exact (no
 * bound, no approximation): null-safe {@code equals}/{@code hashCode}/{@code toString}, the
 * {@code requireNonNull} family (loud {@link NullPointerException} on null), {@code isNull}/
 * {@code nonNull}, {@code requireNonNullElse}/{@code requireNonNullElseGet}, the comparator-driven
 * {@code compare} (the comparator is a plain SAM call — bmc4j desugars the lambda so JBMC
 * devirtualizes its {@code compare}), and the {@code checkIndex}/{@code checkFromToIndex}/
 * {@code checkFromIndexSize} bounds helpers (return the index in range; throw
 * {@link IndexOutOfBoundsException} out of range), including the {@code int} and {@code long}
 * overloads.
 *
 * <p>{@code hash(Object...)} delegates to the (separately conformance-tested) {@link Arrays} model's
 * {@code hashCode(Object[])}. {@code deepEquals} and {@code toIdentityString} are the only members
 * that cannot be modeled soundly (recursive nested-array reflection / identity hashing); they are
 * loud stubs — see the per-member {@code @BmcUnmodelable} declarations.
 */
public final class Objects {

    private Objects() {
    }

    // --- null-safe equality / hashing / toString ------------------------------------------------

    /** Null-safe equality: {@code a == b || (a != null && a.equals(b))}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    /** Null-safe hash: {@code 0} for null, else {@code o.hashCode()}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static int hashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }

    /** Hash of a sequence of values — {@link Arrays#hashCode(Object[])} over the varargs. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static int hash(Object... values) {
        return Arrays.hashCode(values);
    }

    /** Null-safe toString: {@code "null"} for null, else {@code o.toString()}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static String toString(Object o) {
        return String.valueOf(o);
    }

    /** Null-safe toString with a caller-supplied default for null. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static String toString(Object o, String nullDefault) {
        return o != null ? o.toString() : nullDefault;
    }

    // --- null predicates / requireNonNull family ------------------------------------------------

    /** {@code true} iff {@code o} is null. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static boolean isNull(Object o) {
        return o == null;
    }

    /** {@code true} iff {@code o} is non-null. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static boolean nonNull(Object o) {
        return o != null;
    }

    /** Returns {@code o}, or throws {@link NullPointerException} if it is null. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static <T> T requireNonNull(T o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }

    /** Returns {@code o}, or throws {@link NullPointerException}({@code message}) if it is null. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static <T> T requireNonNull(T o, String message) {
        if (o == null) {
            throw new NullPointerException(message);
        }
        return o;
    }

    /**
     * Returns {@code o}, or throws {@link NullPointerException} (message from {@code messageSupplier})
     * if it is null. The supplier is a plain SAM call — only invoked on the null path, like the JDK.
     */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static <T> T requireNonNull(T o, Supplier<String> messageSupplier) {
        if (o == null) {
            throw new NullPointerException(messageSupplier == null ? null : messageSupplier.get());
        }
        return o;
    }

    /** {@code o} if non-null, else {@code defaultObj}; throws NPE if BOTH are null (JDK semantics). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static <T> T requireNonNullElse(T o, T defaultObj) {
        return o != null ? o : requireNonNull(defaultObj, "defaultObj");
    }

    /**
     * {@code o} if non-null, else the (non-null) result of {@code supplier.get()}; throws NPE if the
     * supplier is needed and yields null (or is itself null), like the JDK.
     */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static <T> T requireNonNullElseGet(T o, Supplier<? extends T> supplier) {
        return o != null ? o
                : requireNonNull(requireNonNull(supplier, "supplier").get(), "supplier.get()");
    }

    // --- comparator-driven compare --------------------------------------------------------------

    /**
     * {@code 0} if {@code a == b} (incl. both null), else {@code c.compare(a, b)}. The comparator is a
     * plain SAM call — bmc4j desugars the lambda so JBMC devirtualizes {@code compare}.
     */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static <T> int compare(T a, T b, Comparator<? super T> c) {
        return (a == b) ? 0 : c.compare(a, b);
    }

    // --- bounds checks (int overloads) ----------------------------------------------------------

    /** Returns {@code index} if {@code 0 <= index < length}, else throws {@link IndexOutOfBoundsException}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static int checkIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "Index " + index + " out of bounds for length " + length);
        }
        return index;
    }

    /**
     * Returns {@code fromIndex} if {@code 0 <= fromIndex <= toIndex <= length}, else throws
     * {@link IndexOutOfBoundsException}.
     */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static int checkFromToIndex(int fromIndex, int toIndex, int length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
            throw new IndexOutOfBoundsException(
                    "Range [" + fromIndex + ", " + toIndex + ") out of bounds for length " + length);
        }
        return fromIndex;
    }

    /**
     * Returns {@code fromIndex} if {@code 0 <= fromIndex}, {@code 0 <= size}, and
     * {@code fromIndex + size <= length}, else throws {@link IndexOutOfBoundsException}.
     */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static int checkFromIndexSize(int fromIndex, int size, int length) {
        if (length < 0 || fromIndex < 0 || size < 0 || size > length - fromIndex) {
            throw new IndexOutOfBoundsException(
                    "Range [" + fromIndex + ", " + fromIndex + " + " + size + ") out of bounds for length " + length);
        }
        return fromIndex;
    }

    // --- bounds checks (long overloads, Java 16+) -----------------------------------------------

    /** {@code long} overload of {@link #checkIndex(int, int)}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static long checkIndex(long index, long length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "Index " + index + " out of bounds for length " + length);
        }
        return index;
    }

    /** {@code long} overload of {@link #checkFromToIndex(int, int, int)}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static long checkFromToIndex(long fromIndex, long toIndex, long length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
            throw new IndexOutOfBoundsException(
                    "Range [" + fromIndex + ", " + toIndex + ") out of bounds for length " + length);
        }
        return fromIndex;
    }

    /** {@code long} overload of {@link #checkFromIndexSize(int, int, int)}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.objects)")
    public static long checkFromIndexSize(long fromIndex, long size, long length) {
        if (length < 0 || fromIndex < 0 || size < 0 || size > length - fromIndex) {
            throw new IndexOutOfBoundsException(
                    "Range [" + fromIndex + ", " + fromIndex + " + " + size + ") out of bounds for length " + length);
        }
        return fromIndex;
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) -----------------

    @org.bmc4j.models.audit.BmcUnmodelable(reason = "recursive nested-array deep equality via reflection — compare element-wise instead")
    public static boolean deepEquals(Object a, Object b) {
        throw org.bmc4j.analysis.BmcUnmodelledReached.fail(
                "bmc4j: unmodelled member java.util.Objects.deepEquals(java.lang.Object,java.lang.Object) — recursive nested-array deep equality via reflection — compare element-wise instead");
    }

    @org.bmc4j.models.audit.BmcUnmodelable(reason = "identity string built from System.identityHashCode + getClass().getName() — not soundly representable")
    public static String toIdentityString(Object o) {
        throw org.bmc4j.analysis.BmcUnmodelledReached.fail(
                "bmc4j: unmodelled member java.util.Objects.toIdentityString(java.lang.Object) — identity string via System.identityHashCode — not soundly representable");
    }
}
