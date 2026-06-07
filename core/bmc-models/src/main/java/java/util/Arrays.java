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
 * with the mechanical per-primitive clones added where they're a straight copy (copyOf/fill/equals/
 * hashCode). The remaining formatting/parsing/double-NaN/parallel/spliterator/deep/exotic surface
 * stays in the {@code BmcModelTail}: each gets a build-synthesized loud body, so reaching any of them
 * fails NAMED AND LOUD under JBMC rather than silently havocking to a nondet result.
 *
 * <p>Loops are bounded by the (concrete) array length, so JBMC unwinds them deterministically. The
 * {@code sort} models are plain insertion sort over the bound: quadratic but small for the array
 * sizes BMC proofs use. {@code binarySearch} models the JDK contract on an array the caller has
 * sorted (sorted-assume); on an unsorted array the result is unspecified exactly as in the JDK.
 */
@BmcModelTail(reason = "the remaining Arrays surface (toString/deepToString/deepEquals/deepHashCode, parallelSort/parallelPrefix/parallelSetAll, spliterator, mismatch/compare, float/double overloads with NaN/-0.0 total-order quirks, Comparator-based sort/binarySearch, and the long-tail primitive overloads not mechanically cloned) is out of scope for a bounded model. All loud under JBMC")
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
