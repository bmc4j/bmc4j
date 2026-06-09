package kotlin.collections;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of Kotlin's {@code ArraysKt} multifile facade for the array <b>copy/fill</b> surface.
 *
 * <p>The headline gap this closes: a {@code copyInto} call — the array-copy at the heart of the
 * kotlinx persistent-collection trie nodes ({@code persistentSetOf().add(x)} copies a child array
 * with {@code copyInto}) — binds to {@code kotlin.collections.ArraysKt.copyInto(...)} /
 * {@code copyInto$default(...)}. The real {@code ArraysKt} multifile facade ({@code ArraysKt} extends
 * {@code ArraysKt___ArraysKt} … {@code ArraysKt__ArraysJVMKt}) is far too large for JBMC to link a
 * single method out of, so it nondet-stubs {@code copyInto} — havocking the copied array and demoting
 * every proof through a persistent collection to UNKNOWN. This model class IS {@code ArraysKt} (a flat
 * class carrying the members directly), so {@code invokestatic ArraysKt.copyInto} resolves straight to
 * a sound element-copy-loop body.
 *
 * <p>Modeled surface: {@code copyInto} (Object[] + all eight primitive element types) and its
 * {@code $default} bridge that applies the {@code destinationOffset=0, startIndex=0, endIndex=size}
 * defaults; {@code copyOf}, {@code copyOfRange}, and {@code fill} (the same eight element types) — the
 * high-value array transforms whose real bodies are sound but unlinkable from the giant facade.
 * {@code copyInto} uses an explicit element-copy loop (JBMC models per-element array writes precisely
 * but treats the {@code System.arraycopy} intrinsic as a weak/havocking copy); the rest delegate to the
 * (loop-backed, separately-audited) {@link java.util.Arrays} model, which JBMC handles soundly over the
 * bounded array.
 *
 * <p>The {@code ArraysKt} facade exposes ~1300 array extension functions (all/any/map/filter/fold/
 * sort/sum/zip/windowed/… across nine element types plus the lambda-taking inline forms). The vast
 * remainder is the tail: a {@code @BmcModelTail} absorbs every undeclared real member, and the
 * build-time loud-body synthesis pass gives each a member-named loud body, so a proof reaching an
 * unmodeled array extension fails NAMED AND LOUD under JBMC rather than silently nondet-stubbing. The
 * common inline forms (the lambda-taking {@code map{}}/{@code filter{}}/{@code fold{}} etc.) inline
 * into the caller and never reach this facade at all.
 */
@BmcModelTail(reason = "the ~1300-member kotlin.collections.ArraysKt array-extension surface "
        + "(all/any/map/filter/fold/sort/sum/zip/windowed/indexOf/… across nine element types, plus the "
        + "lambda-taking inline forms that inline into the caller) is the exotic tail; only the high-value "
        + "copy/fill surface (copyInto/copyOf/copyOfRange/fill) is modeled, the remainder is build-synthesized "
        + "loud (member-named UNKNOWN if reached, never a silent nondet stub)")
public final class ArraysKt {

    private ArraysKt() {
    }

    // ============================================================================================
    // copyInto: ArraysKt.copyInto:(<E>[<E>[III)<E>[ for each element type E. Kotlin's
    //   fun <T> Array<out T>.copyInto(destination, destinationOffset=0, startIndex=0, endIndex=size)
    // copies this[startIndex until endIndex] into destination starting at destinationOffset and RETURNS
    // destination. Sound model: an explicit element-copy loop. (The real facade nondet-stubs these —
    // the array-copy inside persistent-collection trie nodes — because the method can't be linked out of
    // the giant multifile facade.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyInto(T[] source, T[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyInto(byte[] source, byte[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyInto(short[] source, short[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyInto(int[] source, int[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyInto(long[] source, long[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyInto(float[] source, float[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyInto(double[] source, double[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyInto(boolean[] source, boolean[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyInto(char[] source, char[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    // ============================================================================================
    // copyInto$default: the kotlinc-synthesized default-arguments bridge. For a call site that omits
    // destinationOffset/startIndex/endIndex, kotlinc emits
    //   copyInto$default(source, destination, destOffset, startIndex, endIndex, mask, marker)
    // where bit i of `mask` set means "argument i was defaulted". The defaults are destOffset=0,
    // startIndex=0, endIndex=source.size. These bridges are SYNTHETIC on the real facade (so the
    // per-member audit gate does not enumerate them), but JBMC links them when a Kotlin call site omits
    // arguments — exactly the persistent-collection copyInto path — so they MUST carry a sound body here
    // or the omit-args call havocs. They are not part of the audited real-member surface, so they carry
    // no @BmcModelConforms (the gate's implemented-but-unannotated check only fires for methods that
    // mirror a real, non-synthetic member).
    // ============================================================================================

    public static <T> T[] copyInto$default(T[] source, T[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static byte[] copyInto$default(byte[] source, byte[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static short[] copyInto$default(short[] source, short[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static int[] copyInto$default(int[] source, int[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static long[] copyInto$default(long[] source, long[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static float[] copyInto$default(float[] source, float[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static double[] copyInto$default(double[] source, double[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static boolean[] copyInto$default(boolean[] source, boolean[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static char[] copyInto$default(char[] source, char[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    // ============================================================================================
    // copyOf(array): ArraysKt.copyOf:(<E>[)<E>[ — a NEW array of the same length, element-wise copy.
    // Kotlin's `array.copyOf()`. Backed by java.util.Arrays.copyOf, sound under JBMC over the bounded
    // array. (Object[] form preserves the runtime component type via Arrays.copyOf.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyOf(T[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyOf(byte[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyOf(short[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyOf(int[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyOf(long[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyOf(float[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyOf(double[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyOf(boolean[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyOf(char[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    // copyOf(array, newSize): ArraysKt.copyOf:(<E>[I)<E>[ — a NEW array of newSize, truncated or
    // zero/null-padded. Kotlin's `array.copyOf(newSize)`. Arrays.copyOf has exactly this contract.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyOf(T[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyOf(byte[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyOf(short[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyOf(int[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyOf(long[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyOf(float[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyOf(double[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyOf(boolean[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyOf(char[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    // ============================================================================================
    // copyOfRange(array, fromIndex, toIndex): ArraysKt.copyOfRange:(<E>[II)<E>[ — a NEW array of the
    // half-open [fromIndex, toIndex) slice. Kotlin's `array.copyOfRange(from, to)`. Arrays.copyOfRange
    // has exactly this contract (toIndex may exceed length, zero/null-padding the tail).
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyOfRange(T[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyOfRange(byte[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyOfRange(short[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyOfRange(int[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyOfRange(long[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyOfRange(float[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyOfRange(double[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyOfRange(boolean[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyOfRange(char[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    // ============================================================================================
    // fill(array, element, fromIndex, toIndex): ArraysKt.fill:(<E>[<E>II)V — fill [fromIndex, toIndex)
    // with element, IN PLACE (returns void). Kotlin's `array.fill(value, from, to)`. java.util.Arrays.fill
    // has exactly this half-open contract.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> void fill(T[] array, T element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(byte[] array, byte element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(short[] array, short element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(int[] array, int element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(long[] array, long element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(float[] array, float element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(double[] array, double element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(boolean[] array, boolean element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(char[] array, char element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }
}
