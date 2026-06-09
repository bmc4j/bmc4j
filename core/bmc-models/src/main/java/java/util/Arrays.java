package java.util;

import java.util.function.IntUnaryOperator;
import java.util.function.IntToLongFunction;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntFunction;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.BinaryOperator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;
import org.bmc4j.models.audit.FpTotalOrder;

/**
 * Bounded BMC model of {@link java.util.Arrays}. Covers the high-value surface over small arrays:
 * {@code asList}, {@code copyOf}/{@code copyOfRange}, {@code fill}, {@code equals}, {@code hashCode},
 * {@code sort} (insertion sort over the bound), {@code binarySearch} (sorted-assume), {@code stream},
 * and {@code setAll}. Overloads are modeled for {@code int[]} / {@code long[]} / {@code Object[]} first,
 * with the mechanical per-primitive clones (byte/char/short/boolean, plus {@code float} for the
 * comparison-free copy/store ops copyOf/copyOfRange/fill) added where they're a straight copy of the
 * proven int/long body over the bounded array.
 *
 * <p>The <b>range-bounded</b> overloads — the {@code (array, fromIndex, toIndex, ...)} variants of
 * {@code fill}/{@code sort}/{@code binarySearch}, and the {@code (a, aFrom, aTo, b, bFrom, bTo)}
 * variants of {@code equals}/{@code compare}/{@code mismatch} — are the same bodies bounded to the
 * half-open {@code [from, to)} sub-region, with the JDK's exact range checks ({@link
 * IllegalArgumentException} on {@code from > to}, {@link ArrayIndexOutOfBoundsException} on
 * {@code from < 0} or {@code to > length}, each array checked independently and in JDK order).
 *
 * <p>The <b>parallel</b> overloads are modeled as their <b>sequential</b> equivalents — the same
 * single-thread precedent the concurrency models (CompletableFuture / atomics) use: {@code
 * parallelSort} = the insertion {@code sort}, {@code parallelSetAll} = {@code setAll}, and {@code
 * parallelPrefix} = the obvious cumulative left fold ({@code a[i] = op(a[i-1], a[i])}). The JDK
 * specifies these as observably equivalent to their sequential counterparts; running them on one
 * thread under JBMC is sound because BMC has no notion of wall-clock parallelism to begin with.
 *
 * <p>The {@code float[]}/{@code double[]} {@code equals}/{@code sort}/{@code binarySearch}/{@code
 * compare}/{@code mismatch} overloads (full-array and ranged) ARE now modeled, via the IEEE total
 * order ({@code -0.0 < +0.0}, NaN largest, {@code NaN compare NaN == 0}) supplied by the {@link
 * org.bmc4j.models.audit.FpTotalOrder} helper — see that class for why the order is modeled bit-free
 * (jbmc's {@code floatToIntBits} is unsound) AND why the total order lives in an {@code org.bmc4j}
 * helper rather than a {@code java.lang.Float}/{@code Double} model (those classes are reached
 * pervasively — autoboxing, {@code <clinit>} — so modeling them put a bounded FP model on every
 * proof's classpath and crashed jbmc's solver on unrelated proofs). {@code hashCode(float[])}/
 * {@code hashCode(double[])} stay LOUD because they need {@code Float.floatToIntBits}, which is the
 * unsound intrinsic.
 *
 * <p>The remaining formatting/spliterator/deep/exotic surface is now per-member LOUD
 * ({@code @BmcUnmodelable}, honest {@code UNKNOWN} under JBMC if reached — never a silent havoc):
 * {@code deepToString}/{@code deepEquals}/{@code deepHashCode} (recursive nested-array reflection),
 * {@code spliterator} (the {@code Spliterator} interface / parallel split), {@code compareUnsigned}
 * (compare-family — the jbmc exit-6 engine crash), {@code hashCode(float[])}/{@code hashCode(double[])}
 * and {@code toString(float[])}/{@code toString(double[])} (need the unsound {@code Float.floatToIntBits}
 * / {@code Float.toString} FP-to-string), the {@code Comparator}-based {@code sort}/{@code binarySearch}/
 * {@code parallelSort} and the {@code Object[]}/{@code Comparable[]}/{@code Comparator}
 * {@code equals}/{@code compare} (comparator-devirt + the engine crash), and
 * {@code copyOf}/{@code copyOfRange(…, Class)} (reflective {@code Array.newInstance}). The natural-order
 * {@code mismatch(Object[], Object[])} (full + ranged) IS modeled (element {@code .equals}, no compare
 * intrinsic). The class-level {@code @BmcModelTail} is gone: every real member is an explicit per-member
 * decision.
 *
 * <p>Loops are bounded by the (concrete) array length, so JBMC unwinds them deterministically. The
 * {@code sort} models are plain insertion sort over the bound: quadratic but small for the array
 * sizes BMC proofs use. {@code binarySearch} models the JDK contract on an array the caller has
 * sorted (sorted-assume); on an unsorted array the result is unspecified exactly as in the JDK.
 */
public class Arrays {

    private Arrays() {
    }

    // --- asList ---------------------------------------------------------------------------------

    @SafeVarargs
    @BmcModelConforms("differential: models asList (the listOf / Arrays.asList route)")
    public static <T> List<T> asList(T... a) {
        ArrayList<T> l = new ArrayList<>();
        for (T t : a) {
            l.add(t);
        }
        return l;
    }

    // --- copyOf ---------------------------------------------------------------------------------
    // Truncates or zero/null-pads to newLength, matching the JDK. Negative newLength throws
    // NegativeArraySizeException at the array allocation, exactly as the JDK does.

    @BmcModelConforms("differential + @BmcProof: copyOf(int[], int)")
    public static int[] copyOf(int[] original, int newLength) {
        int[] out = new int[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    @BmcModelConforms("differential + @BmcProof: copyOf(long[], int)")
    public static long[] copyOf(long[] original, int newLength) {
        long[] out = new long[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOf(Object[], int)")
    public static <T> T[] copyOf(T[] original, int newLength) {
        @SuppressWarnings("unchecked")
        T[] out = (T[]) new Object[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOf(byte[], int)")
    public static byte[] copyOf(byte[] original, int newLength) {
        byte[] out = new byte[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOf(char[], int)")
    public static char[] copyOf(char[] original, int newLength) {
        char[] out = new char[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOf(short[], int)")
    public static short[] copyOf(short[] original, int newLength) {
        short[] out = new short[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOf(boolean[], int)")
    public static boolean[] copyOf(boolean[] original, int newLength) {
        boolean[] out = new boolean[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    // float copyOf is a pure bit-faithful element copy + zero-pad — no IEEE comparison is involved,
    // so it's sound exactly like the integral overloads (the NaN/-0.0 quirks only bite equals/
    // hashCode/sort/binarySearch). double stays loud per the no-double convention.
    @BmcModelConforms("differential: copyOf(float[], int) (pure copy, no IEEE compare)")
    public static float[] copyOf(float[] original, int newLength) {
        float[] out = new float[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    // double copyOf is a pure element copy + zero-pad like float — moving a double value is a plain
    // store, no IEEE comparison (the NaN/-0.0 quirks only bite equals/hashCode/sort/binarySearch, which
    // route through FpTotalOrder), so it is sound exactly like the float overload.
    @BmcModelConforms("differential (ArraysDoubleConformanceTest): copyOf(double[], int) (pure copy, no IEEE compare)")
    public static double[] copyOf(double[] original, int newLength) {
        double[] out = new double[newLength];
        int n = Math.min(original.length, newLength);
        for (int i = 0; i < n; i++) {
            out[i] = original[i];
        }
        return out;
    }

    // --- copyOfRange ----------------------------------------------------------------------------
    // out[i] = (from+i < original.length) ? original[from+i] : pad. Throws like the JDK on a bad
    // range: ArrayIndexOutOfBoundsException for from<0/from>length, IllegalArgumentException for
    // from>to.

    // JDK order: the from>to (newLength<0) check first as IllegalArgumentException; then the actual
    // copy, whose System.arraycopy throws ArrayIndexOutOfBoundsException only when from is out of
    // range AND there is something to copy (n>0). from==to past the end is a valid empty copy.

    @BmcModelConforms("differential + @BmcProof: copyOfRange(int[], int, int)")
    public static int[] copyOfRange(int[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        int[] out = new int[len];
        int n = Math.min(len, original.length - from);
        // Mirrors System.arraycopy's checks: a negative source offset (from < 0) or a negative copy
        // length n (from past the end) both raise ArrayIndexOutOfBoundsException, regardless of n==0.
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOfRange(long[], int, int)")
    public static long[] copyOfRange(long[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        long[] out = new long[len];
        int n = Math.min(len, original.length - from);
        // Mirrors System.arraycopy's checks: a negative source offset (from < 0) or a negative copy
        // length n (from past the end) both raise ArrayIndexOutOfBoundsException, regardless of n==0.
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOfRange(Object[], int, int)")
    public static <T> T[] copyOfRange(T[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        @SuppressWarnings("unchecked")
        T[] out = (T[]) new Object[len];
        int n = Math.min(len, original.length - from);
        // Mirrors System.arraycopy's checks: a negative source offset (from < 0) or a negative copy
        // length n (from past the end) both raise ArrayIndexOutOfBoundsException, regardless of n==0.
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOfRange(byte[], int, int)")
    public static byte[] copyOfRange(byte[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        byte[] out = new byte[len];
        int n = Math.min(len, original.length - from);
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOfRange(char[], int, int)")
    public static char[] copyOfRange(char[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        char[] out = new char[len];
        int n = Math.min(len, original.length - from);
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOfRange(short[], int, int)")
    public static short[] copyOfRange(short[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        short[] out = new short[len];
        int n = Math.min(len, original.length - from);
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    @BmcModelConforms("differential: copyOfRange(boolean[], int, int)")
    public static boolean[] copyOfRange(boolean[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        boolean[] out = new boolean[len];
        int n = Math.min(len, original.length - from);
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    // float copyOfRange is a pure copy + zero-pad (no IEEE compare) — sound like the integral overloads.
    @BmcModelConforms("differential: copyOfRange(float[], int, int) (pure copy, no IEEE compare)")
    public static float[] copyOfRange(float[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        float[] out = new float[len];
        int n = Math.min(len, original.length - from);
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    // double copyOfRange is a pure copy + zero-pad (no IEEE compare) — sound like the float overload.
    @BmcModelConforms("differential (ArraysDoubleConformanceTest): copyOfRange(double[], int, int) (pure copy, no IEEE compare)")
    public static double[] copyOfRange(double[] original, int from, int to) {
        int len = to - from;
        if (len < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        double[] out = new double[len];
        int n = Math.min(len, original.length - from);
        if (from < 0 || n < 0) {
            throw new ArrayIndexOutOfBoundsException(from);
        }
        for (int i = 0; i < n; i++) {
            out[i] = original[from + i];
        }
        return out;
    }

    // --- fill -----------------------------------------------------------------------------------

    @BmcModelConforms("differential + @BmcProof: fill(int[], int)")
    public static void fill(int[] a, int val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(int[], int, int, int)")
    public static void fill(int[] a, int fromIndex, int toIndex, int val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(long[], long)")
    public static void fill(long[] a, long val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(Object[], Object)")
    public static void fill(Object[] a, Object val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(char[], char)")
    public static void fill(char[] a, char val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(byte[], byte)")
    public static void fill(byte[] a, byte val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(boolean[], boolean)")
    public static void fill(boolean[] a, boolean val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(short[], short)")
    public static void fill(short[] a, short val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    // fill stores its value (no IEEE compare) so the float overload is sound. double stores identically.
    @BmcModelConforms("differential: fill(float[], float) (pure store, no IEEE compare)")
    public static void fill(float[] a, float val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential (ArraysDoubleConformanceTest): fill(double[], double) (pure store, no IEEE compare)")
    public static void fill(double[] a, double val) {
        for (int i = 0; i < a.length; i++) {
            a[i] = val;
        }
    }

    // --- fill (range) — mechanical clones of fill(int[], int, int, int) over the bounded array ---

    @BmcModelConforms("differential: fill(long[], int, int, long)")
    public static void fill(long[] a, int fromIndex, int toIndex, long val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(Object[], int, int, Object)")
    public static void fill(Object[] a, int fromIndex, int toIndex, Object val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(char[], int, int, char)")
    public static void fill(char[] a, int fromIndex, int toIndex, char val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(byte[], int, int, byte)")
    public static void fill(byte[] a, int fromIndex, int toIndex, byte val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(short[], int, int, short)")
    public static void fill(short[] a, int fromIndex, int toIndex, short val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential: fill(boolean[], int, int, boolean)")
    public static void fill(boolean[] a, int fromIndex, int toIndex, boolean val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    // fill stores its value (no IEEE compare) so the float range-fill is sound. double stores identically.
    @BmcModelConforms("differential: fill(float[], int, int, float) (pure store, no IEEE compare)")
    public static void fill(float[] a, int fromIndex, int toIndex, float val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @BmcModelConforms("differential (ArraysDoubleConformanceTest): fill(double[], int, int, double) (pure store, no IEEE compare)")
    public static void fill(double[] a, int fromIndex, int toIndex, double val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    // --- equals ---------------------------------------------------------------------------------

    @BmcModelConforms("differential + @BmcProof: equals(int[], int[])")
    public static boolean equals(int[] a, int[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(long[], long[])")
    public static boolean equals(long[] a, long[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(Object[], Object[])")
    public static boolean equals(Object[] a, Object[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            Object x = a[i];
            Object y = a2[i];
            if (!(x == null ? y == null : x.equals(y))) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(char[], char[])")
    public static boolean equals(char[] a, char[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(byte[], byte[])")
    public static boolean equals(byte[] a, byte[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(boolean[], boolean[])")
    public static boolean equals(boolean[] a, boolean[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(short[], short[])")
    public static boolean equals(short[] a, short[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    // float/double equals use the IEEE TOTAL ORDER, not plain ==: the JDK compares via
    // Float/Double.floatToIntBits, i.e. FpTotalOrder.compare(a,b)==0. So -0.0 and +0.0 are NOT equal, and two
    // NaNs ARE equal (same canonical bits) — the exact opposite of the primitive == the integral bodies
    // use. We route through the modeled FpTotalOrder.compare (sound bit-free total order); floatToIntBits
    // itself is unsound under jbmc, so hashCode(float[])/hashCode(double[]) stay loud in the tail.

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): equals(float[], float[]) via FpTotalOrder.compare total order (-0!=+0, NaN==NaN)")
    public static boolean equals(float[] a, float[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (FpTotalOrder.compare(a[i], a2[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): equals(double[], double[]) via FpTotalOrder.compare total order (-0!=+0, NaN==NaN)")
    public static boolean equals(double[] a, double[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null) {
            return false;
        }
        if (a.length != a2.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (FpTotalOrder.compare(a[i], a2[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    // --- hashCode -------------------------------------------------------------------------------
    // JDK contract: 1 for null; otherwise the 31*result+element polynomial, with element hash 0 for
    // a null Object element.

    @BmcModelConforms("differential + @BmcProof: hashCode(int[])")
    public static int hashCode(int[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (int element : a) {
            result = 31 * result + element;
        }
        return result;
    }

    @BmcModelConforms("differential: hashCode(long[])")
    public static int hashCode(long[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (long element : a) {
            int elementHash = (int) (element ^ (element >>> 32));
            result = 31 * result + elementHash;
        }
        return result;
    }

    @BmcModelConforms("differential: hashCode(Object[])")
    public static int hashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (Object element : a) {
            result = 31 * result + (element == null ? 0 : element.hashCode());
        }
        return result;
    }

    @BmcModelConforms("differential: hashCode(char[])")
    public static int hashCode(char[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (char element : a) {
            result = 31 * result + element;
        }
        return result;
    }

    @BmcModelConforms("differential: hashCode(byte[])")
    public static int hashCode(byte[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (byte element : a) {
            result = 31 * result + element;
        }
        return result;
    }

    @BmcModelConforms("differential: hashCode(boolean[])")
    public static int hashCode(boolean[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (boolean element : a) {
            result = 31 * result + (element ? 1231 : 1237);
        }
        return result;
    }

    @BmcModelConforms("differential: hashCode(short[])")
    public static int hashCode(short[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (short element : a) {
            result = 31 * result + element;
        }
        return result;
    }

    // --- sort (insertion sort over the bound) ---------------------------------------------------
    // Stable, O(n^2) — fine for the small arrays BMC proofs use, and avoids the SAT-pathological
    // recursion/partition of the JDK's dual-pivot quicksort.

    @BmcModelConforms("differential + @BmcProof: sort(int[]) (insertion sort)")
    public static void sort(int[] a) {
        for (int i = 1; i < a.length; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(long[]) (insertion sort)")
    public static void sort(long[] a) {
        for (int i = 1; i < a.length; i++) {
            long key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(Object[]) (insertion sort, Comparable elements)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sort(Object[] a) {
        for (int i = 1; i < a.length; i++) {
            Object key = a[i];
            int j = i - 1;
            while (j >= 0 && ((Comparable) a[j]).compareTo(key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    // Integral primitive sorts: direct insertion-sort clones of sort(int[]). byte/char/short have a
    // total order with no NaN/-0.0 quirks, so the int body ports verbatim. float/double sorts stay
    // loud in the tail — their IEEE total-order (NaN sorts high, -0.0 < 0.0) isn't the plain `>`.

    @BmcModelConforms("differential: sort(byte[]) (insertion sort)")
    public static void sort(byte[] a) {
        for (int i = 1; i < a.length; i++) {
            byte key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(char[]) (insertion sort)")
    public static void sort(char[] a) {
        for (int i = 1; i < a.length; i++) {
            char key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(short[]) (insertion sort)")
    public static void sort(short[] a) {
        for (int i = 1; i < a.length; i++) {
            short key = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    // float/double sorts use the IEEE TOTAL ORDER (NaN sorts last, -0.0 before +0.0), not the plain `>`
    // the integral bodies use. Insertion sort with the modeled FpTotalOrder.compare as the comparator:
    // shift while the predecessor is GREATER than the key under the total order (compare(a[j], key) > 0).

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): sort(float[]) insertion sort, FpTotalOrder.compare total order (NaN last, -0<+0)")
    public static void sort(float[] a) {
        for (int i = 1; i < a.length; i++) {
            float key = a[i];
            int j = i - 1;
            while (j >= 0 && FpTotalOrder.compare(a[j], key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): sort(double[]) insertion sort, FpTotalOrder.compare total order (NaN last, -0<+0)")
    public static void sort(double[] a) {
        for (int i = 1; i < a.length; i++) {
            double key = a[i];
            int j = i - 1;
            while (j >= 0 && FpTotalOrder.compare(a[j], key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    // --- sort (range) — insertion sort bounded to [fromIndex, toIndex) --------------------------
    // Same body as the full-array sorts, with the loop floor raised to fromIndex and the inner
    // shift stopping at fromIndex. rangeCheck throws the JDK's exact exceptions on a bad range.

    @BmcModelConforms("differential: sort(int[], int, int) (insertion sort over the range)")
    public static void sort(int[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= fromIndex && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(long[], int, int) (insertion sort over the range)")
    public static void sort(long[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            long key = a[i];
            int j = i - 1;
            while (j >= fromIndex && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(byte[], int, int) (insertion sort over the range)")
    public static void sort(byte[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            byte key = a[i];
            int j = i - 1;
            while (j >= fromIndex && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(char[], int, int) (insertion sort over the range)")
    public static void sort(char[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            char key = a[i];
            int j = i - 1;
            while (j >= fromIndex && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(short[], int, int) (insertion sort over the range)")
    public static void sort(short[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            short key = a[i];
            int j = i - 1;
            while (j >= fromIndex && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("differential: sort(Object[], int, int) (insertion sort over the range, Comparable elements)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sort(Object[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            Object key = a[i];
            int j = i - 1;
            while (j >= fromIndex && ((Comparable) a[j]).compareTo(key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): sort(float[], int, int) total order, ranged")
    public static void sort(float[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            float key = a[i];
            int j = i - 1;
            while (j >= fromIndex && FpTotalOrder.compare(a[j], key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): sort(double[], int, int) total order, ranged")
    public static void sort(double[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            double key = a[i];
            int j = i - 1;
            while (j >= fromIndex && FpTotalOrder.compare(a[j], key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    // --- binarySearch (sorted-assume) -----------------------------------------------------------
    // Models the JDK contract on an already-sorted array: returns the index of the key if present,
    // else -(insertion point) - 1. On an unsorted array the result is unspecified, exactly as in
    // the JDK.

    @BmcModelConforms("differential + @BmcProof: binarySearch(int[], int) (sorted-assume)")
    public static int binarySearch(int[] a, int key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(long[], long) (sorted-assume)")
    public static int binarySearch(long[] a, long key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    // Integral primitive binary searches: direct sorted-assume clones of binarySearch(int[], int).
    // byte/char/short order totally with no IEEE quirk. float/double stay loud in the tail.

    @BmcModelConforms("differential: binarySearch(byte[], byte) (sorted-assume)")
    public static int binarySearch(byte[] a, byte key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            byte midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(char[], char) (sorted-assume)")
    public static int binarySearch(char[] a, char key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            char midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(short[], short) (sorted-assume)")
    public static int binarySearch(short[] a, short key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            short midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    // float/double binarySearch use the IEEE TOTAL ORDER (the array is sorted by FpTotalOrder.compare).
    // Same sorted-assume contract via the modeled compare: <0 -> go right, >0 -> go left, ==0 -> found.

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): binarySearch(float[], float) sorted-assume, total order")
    public static int binarySearch(float[] a, float key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = FpTotalOrder.compare(a[mid], key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): binarySearch(double[], double) sorted-assume, total order")
    public static int binarySearch(double[] a, double key) {
        int low = 0;
        int high = a.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = FpTotalOrder.compare(a[mid], key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    // Object[] natural-order full-array search: elements are Comparable and the array is sorted in
    // their natural order (the JDK's sorted-assume contract). midVal.compareTo(key) is a plain virtual
    // call on the concrete element (boxed primitives / String), NOT a Comparator devirt — sound. The
    // ranged twin already exists below; this is the full-array form delegating to it.
    @BmcModelConforms("differential (ArraysObjectBinarySearchConformanceTest): binarySearch(Object[], Object) natural-order sorted-assume")
    public static int binarySearch(Object[] a, Object key) {
        return binarySearch(a, 0, a.length, key);
    }

    // --- binarySearch (range, sorted-assume) ----------------------------------------------------
    // Same sorted-assume search bounded to [fromIndex, toIndex): low starts at fromIndex, high at
    // toIndex-1, and an absent key returns -(insertion point)-1 with the insertion point in-range.
    // rangeCheck throws the JDK's exact exceptions on a bad range.

    @BmcModelConforms("differential: binarySearch(int[], int, int, int) (sorted-assume, ranged)")
    public static int binarySearch(int[] a, int fromIndex, int toIndex, int key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(long[], int, int, long) (sorted-assume, ranged)")
    public static int binarySearch(long[] a, int fromIndex, int toIndex, long key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(byte[], int, int, byte) (sorted-assume, ranged)")
    public static int binarySearch(byte[] a, int fromIndex, int toIndex, byte key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            byte midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(char[], int, int, char) (sorted-assume, ranged)")
    public static int binarySearch(char[] a, int fromIndex, int toIndex, char key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            char midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(short[], int, int, short) (sorted-assume, ranged)")
    public static int binarySearch(short[] a, int fromIndex, int toIndex, short key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            short midVal = a[mid];
            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("differential: binarySearch(Object[], int, int, Object) (sorted-assume, ranged, Comparable elements)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int binarySearch(Object[] a, int fromIndex, int toIndex, Object key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Comparable midVal = (Comparable) a[mid];
            int cmp = midVal.compareTo(key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): binarySearch(float[], int, int, float) sorted-assume, ranged, total order")
    public static int binarySearch(float[] a, int fromIndex, int toIndex, float key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = FpTotalOrder.compare(a[mid], key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): binarySearch(double[], int, int, double) sorted-assume, ranged, total order")
    public static int binarySearch(double[] a, int fromIndex, int toIndex, double key) {
        rangeCheck(a.length, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = FpTotalOrder.compare(a[mid], key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    // --- mismatch (first differing index, sound for integral element types) ---------------------
    // JDK contract: index of the first element that differs; if one array is a proper prefix of the
    // other, the length of the shorter; -1 if the arrays are equal (same length, same elements). A
    // null array throws NullPointerException (the JDK reads a.length). float/double mismatch stay
    // loud — they compare via FpTotalOrder.compare (NaN/-0.0 total order), not the plain ==.

    @BmcModelConforms("differential: mismatch(int[], int[])")
    public static int mismatch(int[] a, int[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(long[], long[])")
    public static int mismatch(long[] a, long[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(byte[], byte[])")
    public static int mismatch(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(char[], char[])")
    public static int mismatch(char[] a, char[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(short[], short[])")
    public static int mismatch(short[] a, short[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(boolean[], boolean[])")
    public static int mismatch(boolean[] a, boolean[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    // Object[] mismatch uses element .equals (Objects.equals — null-safe), NOT a primitive compare
    // intrinsic, so it sidesteps the jbmc exit-6 compare-family crash that walls the primitive
    // compare/compareUnsigned and the Comparator mismatch overloads. Natural-order, full + ranged.

    @BmcModelConforms("differential (ArraysObjectMismatchConformanceTest): mismatch(Object[], Object[]) via element .equals")
    public static int mismatch(Object[] a, Object[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            Object x = a[i];
            Object y = b[i];
            if (!(x == null ? y == null : x.equals(y))) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("differential (ArraysObjectMismatchConformanceTest): mismatch(Object[], int, int, Object[], int, int) via element .equals")
    public static int mismatch(Object[] a, int aFromIndex, int aToIndex, Object[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            Object x = a[aFromIndex + i];
            Object y = b[bFromIndex + i];
            if (!(x == null ? y == null : x.equals(y))) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    // float/double mismatch detect a difference via the IEEE TOTAL ORDER (FpTotalOrder.compare != 0),
    // not plain == — so a -0.0 vs +0.0 pair IS a mismatch and a NaN vs NaN pair is NOT.

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): mismatch(float[], float[]) via FpTotalOrder.compare total order")
    public static int mismatch(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (FpTotalOrder.compare(a[i], b[i]) != 0) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): mismatch(double[], double[]) via FpTotalOrder.compare total order")
    public static int mismatch(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (FpTotalOrder.compare(a[i], b[i]) != 0) {
                return i;
            }
        }
        return a.length == b.length ? -1 : len;
    }

    // --- compare (lexicographic, sound for integral element types) ------------------------------
    // JDK contract: 0 if equal; the signed Type.compare of the first differing element; if one is a
    // proper prefix of the other, a.length - b.length. Uses the same total order as the per-type
    // Type.compare (e.g. char/boolean compare as unsigned/false<true). float/double compare stay loud.

    @BmcModelConforms("differential: compare(int[], int[])")
    public static int compare(int[] a, int[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        return a.length - b.length;
    }

    @BmcModelConforms("differential: compare(long[], long[])")
    public static int compare(long[] a, long[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        return a.length - b.length;
    }

    @BmcModelConforms("differential: compare(byte[], byte[])")
    public static int compare(byte[] a, byte[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        return a.length - b.length;
    }

    // char compares UNSIGNED (Character.compare(x, y) == x - y over the unsigned 16-bit range), which
    // the plain char `<` already gives — chars are unsigned, so this is sound.
    @BmcModelConforms("differential: compare(char[], char[])")
    public static int compare(char[] a, char[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        return a.length - b.length;
    }

    @BmcModelConforms("differential: compare(short[], short[])")
    public static int compare(short[] a, short[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        return a.length - b.length;
    }

    // boolean compares false < true (Boolean.compare), which the plain `(!a[i] && b[i])` test gives.
    @BmcModelConforms("differential: compare(boolean[], boolean[])")
    public static int compare(boolean[] a, boolean[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return !a[i] ? -1 : 1;  // false < true
            }
        }
        return a.length - b.length;
    }

    // float/double compare are lexicographic over the IEEE TOTAL ORDER: the first element where
    // FpTotalOrder.compare != 0 decides the sign; a proper prefix gives a.length - b.length.

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): compare(float[], float[]) lexicographic via FpTotalOrder.compare")
    public static int compare(float[] a, float[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = FpTotalOrder.compare(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return a.length - b.length;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): compare(double[], double[]) lexicographic via FpTotalOrder.compare")
    public static int compare(double[] a, double[] b) {
        if (a == b) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = FpTotalOrder.compare(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return a.length - b.length;
    }

    // --- equals (two ranges) --------------------------------------------------------------------
    // JDK contract: compares a[aFrom, aTo) against b[bFrom, bTo). false if the ranges differ in
    // length; otherwise true iff every paired element is equal. Each array's range is validated
    // independently in JDK order (a first, then b): IllegalArgumentException on from>to, then
    // ArrayIndexOutOfBoundsException on from<0 / to>length.

    @BmcModelConforms("differential: equals(int[], int, int, int[], int, int)")
    public static boolean equals(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(long[], int, int, long[], int, int)")
    public static boolean equals(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(byte[], int, int, byte[], int, int)")
    public static boolean equals(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(char[], int, int, char[], int, int)")
    public static boolean equals(char[] a, int aFromIndex, int aToIndex, char[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(short[], int, int, short[], int, int)")
    public static boolean equals(short[] a, int aFromIndex, int aToIndex, short[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(boolean[], int, int, boolean[], int, int)")
    public static boolean equals(boolean[] a, int aFromIndex, int aToIndex, boolean[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("differential: equals(Object[], int, int, Object[], int, int)")
    public static boolean equals(Object[] a, int aFromIndex, int aToIndex, Object[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            Object x = a[aFromIndex + i];
            Object y = b[bFromIndex + i];
            if (!(x == null ? y == null : x.equals(y))) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): equals(float[], int, int, float[], int, int) via FpTotalOrder.compare total order")
    public static boolean equals(float[] a, int aFromIndex, int aToIndex, float[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (FpTotalOrder.compare(a[aFromIndex + i], b[bFromIndex + i]) != 0) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): equals(double[], int, int, double[], int, int) via FpTotalOrder.compare total order")
    public static boolean equals(double[] a, int aFromIndex, int aToIndex, double[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        if (aLen != bLen) {
            return false;
        }
        for (int i = 0; i < aLen; i++) {
            if (FpTotalOrder.compare(a[aFromIndex + i], b[bFromIndex + i]) != 0) {
                return false;
            }
        }
        return true;
    }

    // --- mismatch (two ranges) ------------------------------------------------------------------
    // JDK contract: the relative index (from the range start) of the first differing element; if one
    // range is a proper prefix of the other, the length of the shorter range; -1 if the ranges are
    // equal. Each range is validated independently (a first, then b).

    @BmcModelConforms("differential: mismatch(int[], int, int, int[], int, int)")
    public static int mismatch(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(long[], int, int, long[], int, int)")
    public static int mismatch(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(byte[], int, int, byte[], int, int)")
    public static int mismatch(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(char[], int, int, char[], int, int)")
    public static int mismatch(char[] a, int aFromIndex, int aToIndex, char[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(short[], int, int, short[], int, int)")
    public static int mismatch(short[] a, int aFromIndex, int aToIndex, short[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("differential: mismatch(boolean[], int, int, boolean[], int, int)")
    public static int mismatch(boolean[] a, int aFromIndex, int aToIndex, boolean[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (a[aFromIndex + i] != b[bFromIndex + i]) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): mismatch(float[], int, int, float[], int, int) via FpTotalOrder.compare total order")
    public static int mismatch(float[] a, int aFromIndex, int aToIndex, float[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (FpTotalOrder.compare(a[aFromIndex + i], b[bFromIndex + i]) != 0) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): mismatch(double[], int, int, double[], int, int) via FpTotalOrder.compare total order")
    public static int mismatch(double[] a, int aFromIndex, int aToIndex, double[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            if (FpTotalOrder.compare(a[aFromIndex + i], b[bFromIndex + i]) != 0) {
                return i;
            }
        }
        return aLen == bLen ? -1 : len;
    }

    // --- compare (two ranges) -------------------------------------------------------------------
    // JDK contract: 0 if the ranges are equal; the signed comparison of the first differing element
    // (same total order as the full-array compare); if one range is a proper prefix of the other,
    // aLen - bLen. Each range is validated independently (a first, then b).

    @BmcModelConforms("differential: compare(int[], int, int, int[], int, int)")
    public static int compare(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            int x = a[aFromIndex + i];
            int y = b[bFromIndex + i];
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return aLen - bLen;
    }

    @BmcModelConforms("differential: compare(long[], int, int, long[], int, int)")
    public static int compare(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            long x = a[aFromIndex + i];
            long y = b[bFromIndex + i];
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return aLen - bLen;
    }

    @BmcModelConforms("differential: compare(byte[], int, int, byte[], int, int)")
    public static int compare(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            byte x = a[aFromIndex + i];
            byte y = b[bFromIndex + i];
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return aLen - bLen;
    }

    // char compares UNSIGNED (the plain char `<` already gives this — chars are unsigned).
    @BmcModelConforms("differential: compare(char[], int, int, char[], int, int)")
    public static int compare(char[] a, int aFromIndex, int aToIndex, char[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            char x = a[aFromIndex + i];
            char y = b[bFromIndex + i];
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return aLen - bLen;
    }

    @BmcModelConforms("differential: compare(short[], int, int, short[], int, int)")
    public static int compare(short[] a, int aFromIndex, int aToIndex, short[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            short x = a[aFromIndex + i];
            short y = b[bFromIndex + i];
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return aLen - bLen;
    }

    // boolean compares false < true (Boolean.compare).
    @BmcModelConforms("differential: compare(boolean[], int, int, boolean[], int, int)")
    public static int compare(boolean[] a, int aFromIndex, int aToIndex, boolean[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            boolean x = a[aFromIndex + i];
            boolean y = b[bFromIndex + i];
            if (x != y) {
                return !x ? -1 : 1;  // false < true
            }
        }
        return aLen - bLen;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): compare(float[], int, int, float[], int, int) lexicographic via FpTotalOrder.compare")
    public static int compare(float[] a, int aFromIndex, int aToIndex, float[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            int cmp = FpTotalOrder.compare(a[aFromIndex + i], b[bFromIndex + i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return aLen - bLen;
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): compare(double[], int, int, double[], int, int) lexicographic via FpTotalOrder.compare")
    public static int compare(double[] a, int aFromIndex, int aToIndex, double[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);
        int aLen = aToIndex - aFromIndex;
        int bLen = bToIndex - bFromIndex;
        int len = Math.min(aLen, bLen);
        for (int i = 0; i < len; i++) {
            int cmp = FpTotalOrder.compare(a[aFromIndex + i], b[bFromIndex + i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return aLen - bLen;
    }

    // --- stream ---------------------------------------------------------------------------------

    @BmcModelConforms("@BmcProof (proofs.arrays): stream(int[]) -> IntStream")
    public static IntStream stream(int[] array) {
        return IntStream.of(array);
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): stream(long[]) -> LongStream")
    public static LongStream stream(long[] array) {
        return LongStream.of(array);
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): stream(T[]) -> Stream")
    public static <T> Stream<T> stream(T[] array) {
        return Stream.of(array);
    }

    // double stream: the elements are streamed verbatim (no IEEE comparison) into the bounded
    // DoubleStream model — sound exactly like the int/long overloads; the FP quirks only bite the
    // ordering ops, which DoubleStream itself doesn't perform here.
    @BmcModelConforms("differential (ArraysDoubleConformanceTest): stream(double[]) -> DoubleStream")
    public static DoubleStream stream(double[] array) {
        return DoubleStream.of(array);
    }

    // --- stream (range) -------------------------------------------------------------------------
    // Streams array[startInclusive, endExclusive). The JDK validates via Objects.checkFromToIndex,
    // which throws ArrayIndexOutOfBoundsException (NOT IllegalArgumentException) on start>end as well
    // as on start<0 / end>length. Implemented as the bounded copy fed to the full-array stream.

    @BmcModelConforms("@BmcProof (proofs.arrays): stream(int[], int, int) -> IntStream (ranged)")
    public static IntStream stream(int[] array, int startInclusive, int endExclusive) {
        streamRangeCheck(array.length, startInclusive, endExclusive);
        int len = endExclusive - startInclusive;
        int[] sub = new int[len];
        for (int i = 0; i < len; i++) {
            sub[i] = array[startInclusive + i];
        }
        return IntStream.of(sub);
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): stream(long[], int, int) -> LongStream (ranged)")
    public static LongStream stream(long[] array, int startInclusive, int endExclusive) {
        streamRangeCheck(array.length, startInclusive, endExclusive);
        int len = endExclusive - startInclusive;
        long[] sub = new long[len];
        for (int i = 0; i < len; i++) {
            sub[i] = array[startInclusive + i];
        }
        return LongStream.of(sub);
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): stream(T[], int, int) -> Stream (ranged)")
    public static <T> Stream<T> stream(T[] array, int startInclusive, int endExclusive) {
        streamRangeCheck(array.length, startInclusive, endExclusive);
        int len = endExclusive - startInclusive;
        @SuppressWarnings("unchecked")
        T[] sub = (T[]) new Object[len];
        for (int i = 0; i < len; i++) {
            sub[i] = array[startInclusive + i];
        }
        return Stream.of(sub);
    }

    @BmcModelConforms("differential (ArraysDoubleConformanceTest): stream(double[], int, int) -> DoubleStream (ranged)")
    public static DoubleStream stream(double[] array, int startInclusive, int endExclusive) {
        streamRangeCheck(array.length, startInclusive, endExclusive);
        int len = endExclusive - startInclusive;
        double[] sub = new double[len];
        for (int i = 0; i < len; i++) {
            sub[i] = array[startInclusive + i];
        }
        return DoubleStream.of(sub);
    }

    // --- setAll ---------------------------------------------------------------------------------

    @BmcModelConforms("@BmcProof (proofs.arrays): setAll(int[], IntUnaryOperator)")
    public static void setAll(int[] array, IntUnaryOperator generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsInt(i);
        }
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): setAll(long[], IntToLongFunction)")
    public static void setAll(long[] array, IntToLongFunction generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsLong(i);
        }
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): setAll(Object[], IntFunction)")
    public static <T> void setAll(T[] array, IntFunction<? extends T> generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.apply(i);
        }
    }

    // The generator (IntToDoubleFunction) is a plain SAM call producing each element; the model just
    // stores the result (no IEEE comparison), so it is sound exactly like setAll(int[], …).
    @BmcModelConforms("differential (ArraysDoubleConformanceTest): setAll(double[], IntToDoubleFunction)")
    public static void setAll(double[] array, IntToDoubleFunction generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsDouble(i);
        }
    }

    // --- parallelSetAll (= setAll, sequential semantics) ----------------------------------------
    // The JDK specifies parallelSetAll as observably identical to setAll (each element set to
    // generator.apply(i), i independent). Modeled as the sequential setAll — BMC has no wall-clock
    // parallelism, so running on one thread is sound (the CompletableFuture/atomics precedent).

    @BmcModelConforms("@BmcProof (proofs.arrays): parallelSetAll(int[], IntUnaryOperator) (= setAll)")
    public static void parallelSetAll(int[] array, IntUnaryOperator generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsInt(i);
        }
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): parallelSetAll(long[], IntToLongFunction) (= setAll)")
    public static void parallelSetAll(long[] array, IntToLongFunction generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsLong(i);
        }
    }

    @BmcModelConforms("@BmcProof (proofs.arrays): parallelSetAll(Object[], IntFunction) (= setAll)")
    public static <T> void parallelSetAll(T[] array, IntFunction<? extends T> generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.apply(i);
        }
    }

    @BmcModelConforms("differential (ArraysDoubleConformanceTest): parallelSetAll(double[], IntToDoubleFunction) (= setAll)")
    public static void parallelSetAll(double[] array, IntToDoubleFunction generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsDouble(i);
        }
    }

    // --- parallelSort (= sort, sequential semantics) --------------------------------------------
    // The JDK specifies parallelSort to leave the array in the same sorted order as sort; only the
    // work distribution differs. Modeled as the sequential insertion sort (full-array and ranged),
    // same single-thread-equivalence precedent as parallelSetAll above.

    @BmcModelConforms("@BmcProof (proofs.arrays): parallelSort(int[]) (= sort)")
    public static void parallelSort(int[] a) {
        sort(a);
    }

    @BmcModelConforms("differential: parallelSort(long[]) (= sort)")
    public static void parallelSort(long[] a) {
        sort(a);
    }

    @BmcModelConforms("differential: parallelSort(byte[]) (= sort)")
    public static void parallelSort(byte[] a) {
        sort(a);
    }

    @BmcModelConforms("differential: parallelSort(char[]) (= sort)")
    public static void parallelSort(char[] a) {
        sort(a);
    }

    @BmcModelConforms("differential: parallelSort(short[]) (= sort)")
    public static void parallelSort(short[] a) {
        sort(a);
    }

    @BmcModelConforms("differential: parallelSort(int[], int, int) (= sort, ranged)")
    public static void parallelSort(int[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    @BmcModelConforms("differential: parallelSort(long[], int, int) (= sort, ranged)")
    public static void parallelSort(long[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    @BmcModelConforms("differential: parallelSort(byte[], int, int) (= sort, ranged)")
    public static void parallelSort(byte[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    @BmcModelConforms("differential: parallelSort(char[], int, int) (= sort, ranged)")
    public static void parallelSort(char[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    @BmcModelConforms("differential: parallelSort(short[], int, int) (= sort, ranged)")
    public static void parallelSort(short[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): parallelSort(float[]) (= total-order sort)")
    public static void parallelSort(float[] a) {
        sort(a);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): parallelSort(double[]) (= total-order sort)")
    public static void parallelSort(double[] a) {
        sort(a);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): parallelSort(float[], int, int) (= total-order sort, ranged)")
    public static void parallelSort(float[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (proofs.primitives.FloatDoubleArraysLaws): parallelSort(double[], int, int) (= total-order sort, ranged)")
    public static void parallelSort(double[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex);
    }

    // --- parallelPrefix (cumulative fold, sequential semantics) ---------------------------------
    // The JDK specifies a[i] = op(a[i-1], a[i]) accumulated left-to-right (an inclusive scan); the
    // result is independent of how the work is split. Modeled as the obvious sequential scan, both
    // full-array and ranged. A non-associative op gives an unspecified result in the JDK too, so a
    // BMC proof should use an associative op (e.g. +).

    @BmcModelConforms("@BmcProof (proofs.arrays): parallelPrefix(int[], IntBinaryOperator) (cumulative fold)")
    public static void parallelPrefix(int[] array, IntBinaryOperator op) {
        for (int i = 1; i < array.length; i++) {
            array[i] = op.applyAsInt(array[i - 1], array[i]);
        }
    }

    @BmcModelConforms("differential: parallelPrefix(long[], LongBinaryOperator) (cumulative fold)")
    public static void parallelPrefix(long[] array, LongBinaryOperator op) {
        for (int i = 1; i < array.length; i++) {
            array[i] = op.applyAsLong(array[i - 1], array[i]);
        }
    }

    @BmcModelConforms("differential: parallelPrefix(Object[], BinaryOperator) (cumulative fold)")
    public static <T> void parallelPrefix(T[] array, BinaryOperator<T> op) {
        for (int i = 1; i < array.length; i++) {
            array[i] = op.apply(array[i - 1], array[i]);
        }
    }

    // The op (DoubleBinaryOperator) is a SAM call that does any arithmetic; the model only stores its
    // result into the bounded array (no IEEE comparison), so the cumulative scan is sound like int/long.
    @BmcModelConforms("differential (ArraysDoubleConformanceTest): parallelPrefix(double[], DoubleBinaryOperator) (cumulative fold)")
    public static void parallelPrefix(double[] array, DoubleBinaryOperator op) {
        for (int i = 1; i < array.length; i++) {
            array[i] = op.applyAsDouble(array[i - 1], array[i]);
        }
    }

    @BmcModelConforms("differential: parallelPrefix(int[], int, int, IntBinaryOperator) (cumulative fold, ranged)")
    public static void parallelPrefix(int[] array, int fromIndex, int toIndex, IntBinaryOperator op) {
        rangeCheck(array.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            array[i] = op.applyAsInt(array[i - 1], array[i]);
        }
    }

    @BmcModelConforms("differential: parallelPrefix(long[], int, int, LongBinaryOperator) (cumulative fold, ranged)")
    public static void parallelPrefix(long[] array, int fromIndex, int toIndex, LongBinaryOperator op) {
        rangeCheck(array.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            array[i] = op.applyAsLong(array[i - 1], array[i]);
        }
    }

    @BmcModelConforms("differential: parallelPrefix(Object[], int, int, BinaryOperator) (cumulative fold, ranged)")
    public static <T> void parallelPrefix(T[] array, int fromIndex, int toIndex, BinaryOperator<T> op) {
        rangeCheck(array.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            array[i] = op.apply(array[i - 1], array[i]);
        }
    }

    @BmcModelConforms("differential (ArraysDoubleConformanceTest): parallelPrefix(double[], int, int, DoubleBinaryOperator) (cumulative fold, ranged)")
    public static void parallelPrefix(double[] array, int fromIndex, int toIndex, DoubleBinaryOperator op) {
        rangeCheck(array.length, fromIndex, toIndex);
        for (int i = fromIndex + 1; i < toIndex; i++) {
            array[i] = op.applyAsDouble(array[i - 1], array[i]);
        }
    }

    // --- toString (bounded textual render of a single-dimension primitive/Object array) ----------
    // JDK format: "null" for a null array, "[]" for empty, otherwise "[e0, e1, …]" with ", "
    // separators. Built with a StringBuilder over the bounded array length — bmc4j's string layer
    // desugars the appends, so the loop unwinds to the (concrete) array length. The Object[] overload
    // renders each element via String.valueOf (so a null element shows as "null"), like the JDK.
    // deepToString (recursive, nested-array detection) stays in the tail.

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(int[])")
    public static String toString(int[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(a[i]);
        }
        return b.append("]").toString();
    }

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(long[])")
    public static String toString(long[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(a[i]);
        }
        return b.append("]").toString();
    }

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(short[])")
    public static String toString(short[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(a[i]);
        }
        return b.append("]").toString();
    }

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(byte[])")
    public static String toString(byte[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(a[i]);
        }
        return b.append("]").toString();
    }

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(char[])")
    public static String toString(char[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(a[i]);
        }
        return b.append("]").toString();
    }

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(boolean[])")
    public static String toString(boolean[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(a[i]);
        }
        return b.append("]").toString();
    }

    @BmcModelConforms("differential (ArraysToStringConformanceTest): toString(Object[]) (String.valueOf per element)")
    public static String toString(Object[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(String.valueOf(a[i]));
        }
        return b.append("]").toString();
    }

    // --- loud walls (honest UNKNOWN under JBMC if reached) --------------------------------------------

    // Comparator-driven sort/binarySearch/parallelSort/equals/mismatch — devirt through the Comparator
    // interface. Object[]/Comparable[] compare and primitive compareUnsigned hit the jbmc exit-6
    // compare-family engine crash. deep* need recursive nested-array reflection. spliterator needs the
    // Spliterator interface / parallel split. hashCode/toString of float[]/double[] need the unsound
    // Float.floatToIntBits / Float.toString. copyOf/copyOfRange(…, Class) need reflective newInstance.

    @BmcUnmodelable(reason = "comparator-driven sort — devirt through the Comparator interface")
    public static <T> void sort(T[] a, Comparator<? super T> c) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.sort(java.lang.Object[], java.util.Comparator)"
                + " — a comparator-driven sort devirts through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven ranged sort — Comparator devirt")
    public static <T> void sort(T[] a, int fromIndex, int toIndex, Comparator<? super T> c) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.sort(java.lang.Object[], int, int, "
                + "java.util.Comparator) — comparator devirt through the Comparator interface; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven binarySearch — Comparator devirt")
    public static <T> int binarySearch(T[] a, T key, Comparator<? super T> c) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.binarySearch(java.lang.Object[], "
                + "java.lang.Object, java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven ranged binarySearch — Comparator devirt")
    public static <T> int binarySearch(T[] a, int fromIndex, int toIndex, T key, Comparator<? super T> c) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.binarySearch(java.lang.Object[], int, int, "
                + "java.lang.Object, java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven parallelSort — Comparator devirt")
    public static <T> void parallelSort(T[] a, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.parallelSort(java.lang.Object[], "
                + "java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven ranged parallelSort — Comparator devirt")
    public static <T> void parallelSort(T[] a, int fromIndex, int toIndex, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.parallelSort(java.lang.Object[], int, int, "
                + "java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "natural-order parallelSort of Comparable[] — Object compareTo over the array hits the jbmc compare-family engine crash")
    public static <T extends Comparable<? super T>> void parallelSort(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.parallelSort(java.lang.Comparable[]) — the "
                + "Object-element ordering hits the jbmc compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "natural-order ranged parallelSort of Comparable[] — same compare-family engine crash")
    public static <T extends Comparable<? super T>> void parallelSort(T[] a, int fromIndex, int toIndex) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.parallelSort(java.lang.Comparable[], int, int)"
                + " — the Object-element ordering hits the jbmc compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "Object[] compare — the jbmc exit-6 compare-family engine crash over arrays")
    public static <T extends Comparable<? super T>> int compare(T[] a, T[] b) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compare(java.lang.Comparable[], "
                + "java.lang.Comparable[]) — the jbmc exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "Object[] ranged compare — the jbmc exit-6 compare-family engine crash")
    public static <T extends Comparable<? super T>> int compare(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compare(java.lang.Comparable[], int, int, "
                + "java.lang.Comparable[], int, int) — the jbmc exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven compare — Comparator devirt + the compare-family engine crash")
    public static <T> int compare(T[] a, T[] b, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compare(java.lang.Object[], java.lang.Object[],"
                + " java.util.Comparator) — comparator devirt + the compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven ranged compare — Comparator devirt + the compare-family engine crash")
    public static <T> int compare(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compare(java.lang.Object[], int, int, "
                + "java.lang.Object[], int, int, java.util.Comparator) — comparator devirt + the compare-family "
                + "engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned compare — the jbmc exit-6 compare-family engine crash over arrays")
    public static int compareUnsigned(int[] a, int[] b) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(int[], int[]) — the jbmc exit-6"
                + " compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned ranged compare — the jbmc exit-6 compare-family engine crash")
    public static int compareUnsigned(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(int[], int, int, int[], int, "
                + "int) — the jbmc exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned compare — the jbmc exit-6 compare-family engine crash over arrays")
    public static int compareUnsigned(long[] a, long[] b) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(long[], long[]) — the jbmc "
                + "exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned ranged compare — the jbmc exit-6 compare-family engine crash")
    public static int compareUnsigned(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(long[], int, int, long[], int, "
                + "int) — the jbmc exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned compare — the jbmc exit-6 compare-family engine crash over arrays")
    public static int compareUnsigned(byte[] a, byte[] b) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(byte[], byte[]) — the jbmc "
                + "exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned ranged compare — the jbmc exit-6 compare-family engine crash")
    public static int compareUnsigned(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(byte[], int, int, byte[], int, "
                + "int) — the jbmc exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned compare — the jbmc exit-6 compare-family engine crash over arrays")
    public static int compareUnsigned(short[] a, short[] b) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(short[], short[]) — the jbmc "
                + "exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "unsigned ranged compare — the jbmc exit-6 compare-family engine crash")
    public static int compareUnsigned(short[] a, int aFromIndex, int aToIndex, short[] b, int bFromIndex, int bToIndex) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.compareUnsigned(short[], int, int, short[], int,"
                + " int) — the jbmc exit-6 compare-family engine crash; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven equals — Comparator devirt")
    public static <T> boolean equals(T[] a, T[] a2, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.equals(java.lang.Object[], java.lang.Object[], "
                + "java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven ranged equals — Comparator devirt")
    public static <T> boolean equals(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.equals(java.lang.Object[], int, int, "
                + "java.lang.Object[], int, int, java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven mismatch — Comparator devirt")
    public static <T> int mismatch(T[] a, T[] b, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.mismatch(java.lang.Object[], java.lang.Object[],"
                + " java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "comparator-driven ranged mismatch — Comparator devirt")
    public static <T> int mismatch(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex, Comparator<? super T> cmp) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.mismatch(java.lang.Object[], int, int, "
                + "java.lang.Object[], int, int, java.util.Comparator) — comparator devirt; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "reflective copyOf — Array.newInstance over a runtime Class")
    public static <T, U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.copyOf(java.lang.Object[], int, java.lang.Class)"
                + " — reflective Array.newInstance over a runtime Class; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "reflective copyOfRange — Array.newInstance over a runtime Class")
    public static <T, U> T[] copyOfRange(U[] original, int from, int to, Class<? extends T[]> newType) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.copyOfRange(java.lang.Object[], int, int, "
                + "java.lang.Class) — reflective Array.newInstance over a runtime Class; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "deepEquals — recursive nested-array reflection")
    public static boolean deepEquals(Object[] a1, Object[] a2) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.deepEquals(java.lang.Object[], java.lang.Object[])"
                + " — recursive nested-array reflection; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "deepHashCode — recursive nested-array reflection")
    public static int deepHashCode(Object[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.deepHashCode(java.lang.Object[]) — recursive "
                + "nested-array reflection; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "deepToString — recursive nested-array reflection")
    public static String deepToString(Object[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.deepToString(java.lang.Object[]) — recursive "
                + "nested-array reflection; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "hashCode(float[]) needs the unsound Float.floatToIntBits intrinsic")
    public static int hashCode(float[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.hashCode(float[]) — needs the unsound "
                + "Float.floatToIntBits intrinsic; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "hashCode(double[]) needs the unsound Double.doubleToLongBits intrinsic")
    public static int hashCode(double[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.hashCode(double[]) — needs the unsound "
                + "Double.doubleToLongBits intrinsic; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "toString(float[]) needs the unsound Float.toString FP-to-string")
    public static String toString(float[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.toString(float[]) — needs the unsound "
                + "Float.toString FP-to-string; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "toString(double[]) needs the unsound Double.toString FP-to-string")
    public static String toString(double[] a) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.toString(double[]) — needs the unsound "
                + "Double.toString FP-to-string; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "spliterator — the Spliterator interface / parallel split")
    public static <T> Spliterator<T> spliterator(T[] array) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(java.lang.Object[]) — the "
                + "Spliterator interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "ranged spliterator — the Spliterator interface / parallel split")
    public static <T> Spliterator<T> spliterator(T[] array, int startInclusive, int endExclusive) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(java.lang.Object[], int, int) — the"
                + " Spliterator interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "spliterator — the Spliterator.OfInt interface / parallel split")
    public static Spliterator.OfInt spliterator(int[] array) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(int[]) — the Spliterator.OfInt "
                + "interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "ranged spliterator — the Spliterator.OfInt interface / parallel split")
    public static Spliterator.OfInt spliterator(int[] array, int startInclusive, int endExclusive) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(int[], int, int) — the "
                + "Spliterator.OfInt interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "spliterator — the Spliterator.OfLong interface / parallel split")
    public static Spliterator.OfLong spliterator(long[] array) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(long[]) — the Spliterator.OfLong "
                + "interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "ranged spliterator — the Spliterator.OfLong interface / parallel split")
    public static Spliterator.OfLong spliterator(long[] array, int startInclusive, int endExclusive) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(long[], int, int) — the "
                + "Spliterator.OfLong interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "spliterator — the Spliterator.OfDouble interface / parallel split")
    public static Spliterator.OfDouble spliterator(double[] array) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(double[]) — the Spliterator.OfDouble"
                + " interface / parallel split; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "ranged spliterator — the Spliterator.OfDouble interface / parallel split")
    public static Spliterator.OfDouble spliterator(double[] array, int startInclusive, int endExclusive) {
        throw fail("bmc4j: unmodelled member java.util.Arrays.spliterator(double[], int, int) — the "
                + "Spliterator.OfDouble interface / parallel split; honestly UNKNOWN");
    }

    // --- shared range check (mirrors java.util.Arrays.rangeCheck) --------------------------------

    private static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }
    }

    // The ranged stream overloads validate via Objects.checkFromToIndex, which (unlike rangeCheck)
    // throws ArrayIndexOutOfBoundsException — not IllegalArgumentException — when start > end.
    private static void streamRangeCheck(int arrayLength, int startInclusive, int endExclusive) {
        if (startInclusive > endExclusive) {
            throw new ArrayIndexOutOfBoundsException(
                "origin(" + startInclusive + ") > fence(" + endExclusive + ")");
        }
        if (startInclusive < 0) {
            throw new ArrayIndexOutOfBoundsException(startInclusive);
        }
        if (endExclusive > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(endExclusive);
        }
    }
}
