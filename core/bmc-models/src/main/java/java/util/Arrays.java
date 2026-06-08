package java.util;

import java.util.function.IntUnaryOperator;
import java.util.function.IntToLongFunction;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Bounded BMC model of {@link java.util.Arrays}. Covers the high-value surface over small arrays:
 * {@code asList}, {@code copyOf}/{@code copyOfRange}, {@code fill}, {@code equals}, {@code hashCode},
 * {@code sort} (insertion sort over the bound), {@code binarySearch} (sorted-assume), {@code stream},
 * and {@code setAll}. Overloads are modeled for {@code int[]} / {@code long[]} / {@code Object[]} first,
 * with the mechanical per-primitive clones (byte/char/short/boolean, plus {@code float} for the
 * comparison-free copy/store ops copyOf/copyOfRange/fill) added where they're a straight copy of the
 * proven int/long body over the bounded array. The remaining formatting/parsing/parallel/spliterator/
 * deep/exotic surface — and the {@code float[]}/{@code double[]} equals/hashCode/sort/binarySearch
 * overloads, whose IEEE NaN/-0.0 total-order quirks are NOT the plain {@code ==}/{@code >} the integral
 * bodies use — stays in the {@code BmcModelTail}: each gets a build-synthesized loud body, so reaching
 * any of them fails NAMED AND LOUD under JBMC rather than silently havocking to a nondet result.
 *
 * <p>Loops are bounded by the (concrete) array length, so JBMC unwinds them deterministically. The
 * {@code sort} models are plain insertion sort over the bound: quadratic but small for the array
 * sizes BMC proofs use. {@code binarySearch} models the JDK contract on an array the caller has
 * sorted (sorted-assume); on an unsorted array the result is unspecified exactly as in the JDK.
 */
@BmcModelTail(reason = "the remaining Arrays surface (toString/deepToString/deepEquals/deepHashCode, parallelSort/parallelPrefix/parallelSetAll, spliterator, compareUnsigned, the range-bounded mismatch/compare/equals overloads, float/double overloads with NaN/-0.0 total-order quirks, Comparator-based sort/binarySearch, and the remaining long-tail primitive overloads not mechanically cloned) is out of scope for a bounded model. All loud under JBMC")
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

    // fill stores its value (no IEEE compare) so the float overload is sound. double stays loud.
    @BmcModelConforms("differential: fill(float[], float) (pure store, no IEEE compare)")
    public static void fill(float[] a, float val) {
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

    // fill stores its value (no IEEE compare) so the float range-fill is sound. double stays loud.
    @BmcModelConforms("differential: fill(float[], int, int, float) (pure store, no IEEE compare)")
    public static void fill(float[] a, int fromIndex, int toIndex, float val) {
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

    // --- mismatch (first differing index, sound for integral element types) ---------------------
    // JDK contract: index of the first element that differs; if one array is a proper prefix of the
    // other, the length of the shorter; -1 if the arrays are equal (same length, same elements). A
    // null array throws NullPointerException (the JDK reads a.length). float/double mismatch stay
    // loud — they compare via Float/Double.compare (NaN/-0.0 total order), not the plain ==.

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
}
