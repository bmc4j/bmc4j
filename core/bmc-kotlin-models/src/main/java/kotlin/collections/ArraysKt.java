package kotlin.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
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
@BmcModelTail(reason = "the ~1200-member kotlin.collections.ArraysKt array-extension surface "
        + "(all/any/map/filter/fold/sort/sum/zip/windowed/… across nine element types, plus the "
        + "lambda-taking inline forms that inline into the caller) is the exotic tail; the high-value "
        + "copy/fill surface (copyInto/copyOf/copyOfRange/fill) and the hot read/convert surface "
        + "(asList/plus/contains/indexOf/lastIndexOf/toList/toMutableList/toTypedArray/first/last/single/"
        + "getOrNull) are modeled, the remainder is build-synthesized loud (member-named UNKNOWN if reached, "
        + "never a silent nondet stub)")
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

    // ============================================================================================
    // asList: ArraysKt.asList:(<E>[)Ljava/util/List; — the array → List<E> conversion. Kotlin's
    //   fun <T> Array<out T>.asList(): List<T>
    // The headline path this unblocks: kotlinx persistent-collection vararg factories
    // (persistentListOf(a) → ArraysKt.asList(elements) → addAll(thatList)). The consumer's addAll
    // ITERATES the returned list, so the result MUST be a single CONCRETE java.util.ArrayList copy
    // (an explicit element-loop into a fresh ArrayList) rather than Arrays.asList / a custom view: a
    // lone concrete ArrayList iterator devirtualizes cleanly under JBMC (the concrete-backing rule),
    // whereas an exotic/view return reintroduces the iterator-dispatch fragility this whole model
    // exists to avoid. The real facade nondet-stubs asList (unlinkable from the giant multifile facade),
    // havocking every persistent-collection vararg-factory proof to UNKNOWN.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> asList(T[] source) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Byte> asList(byte[] source) {
        ArrayList<Byte> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Short> asList(short[] source) {
        ArrayList<Short> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Integer> asList(int[] source) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Long> asList(long[] source) {
        ArrayList<Long> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Float> asList(float[] source) {
        ArrayList<Float> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Double> asList(double[] source) {
        ArrayList<Double> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Boolean> asList(boolean[] source) {
        ArrayList<Boolean> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> asList(char[] source) {
        ArrayList<Character> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    // ============================================================================================
    // toList / toMutableList: ArraysKt.toList:(<E>[)Ljava/util/List; — a NEW List<E> copy of the array
    // (toList is documented read-only, toMutableList mutable, but bmc4j's single concrete ArrayList model
    // backs both: a copy either way). Same concrete-ArrayList rationale as asList.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toList(T[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Byte> toList(byte[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Short> toList(short[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Integer> toList(int[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Long> toList(long[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Float> toList(float[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Double> toList(double[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Boolean> toList(boolean[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toList(char[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toMutableList(T[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Byte> toMutableList(byte[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Short> toMutableList(short[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Integer> toMutableList(int[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Long> toMutableList(long[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Float> toMutableList(float[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Double> toMutableList(double[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Boolean> toMutableList(boolean[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toMutableList(char[] source) {
        return asList(source);
    }

    // ============================================================================================
    // toTypedArray: ArraysKt.toTypedArray:(<prim>[)[Ljava/lang/<Boxed>; — box each primitive element
    // into a NEW boxed array. Kotlin's `intArrayOf(1,2).toTypedArray(): Array<Int>`. (No Object[] form:
    // an Array<T> has nothing to box. The Collection form stays in the tail.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Byte[] toTypedArray(byte[] source) {
        Byte[] out = new Byte[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Short[] toTypedArray(short[] source) {
        Short[] out = new Short[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer[] toTypedArray(int[] source) {
        Integer[] out = new Integer[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long[] toTypedArray(long[] source) {
        Long[] out = new Long[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Float[] toTypedArray(float[] source) {
        Float[] out = new Float[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Double[] toTypedArray(double[] source) {
        Double[] out = new Double[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Boolean[] toTypedArray(boolean[] source) {
        Boolean[] out = new Boolean[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character[] toTypedArray(char[] source) {
        Character[] out = new Character[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    // ============================================================================================
    // plus(array, element): ArraysKt.plus:(<E>[<E>)<E>[ — a NEW array one longer, with element appended.
    // Kotlin's `array + element`. Backed by Arrays.copyOf (preserves the runtime component type for the
    // Object[] form) + a single tail write, sound under JBMC over the bounded array.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] plus(T[] source, T element) {
        T[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] plus(byte[] source, byte element) {
        byte[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] plus(short[] source, short element) {
        short[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] plus(int[] source, int element) {
        int[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] plus(long[] source, long element) {
        long[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] plus(float[] source, float element) {
        float[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] plus(double[] source, double element) {
        double[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] plus(boolean[] source, boolean element) {
        boolean[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] plus(char[] source, char element) {
        char[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    // plus(array, array): ArraysKt.plus:(<E>[<E>[)<E>[ — a NEW array = source concatenated with the other.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] plus(T[] source, T[] elements) {
        T[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] plus(byte[] source, byte[] elements) {
        byte[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] plus(short[] source, short[] elements) {
        short[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] plus(int[] source, int[] elements) {
        int[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] plus(long[] source, long[] elements) {
        long[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] plus(float[] source, float[] elements) {
        float[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] plus(double[] source, double[] elements) {
        double[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] plus(boolean[] source, boolean[] elements) {
        boolean[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] plus(char[] source, char[] elements) {
        char[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    // plus(array, Collection): ArraysKt.plus:(<E>[Ljava/util/Collection;)<E>[ — a NEW array = source with
    // the collection's elements appended (iteration order). Object[] form preserves the runtime component
    // type via Arrays.copyOf; the primitive forms append each unboxed element.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] plus(T[] source, Collection<? extends T> elements) {
        T[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (T e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] plus(byte[] source, Collection<Byte> elements) {
        byte[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Byte e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] plus(short[] source, Collection<Short> elements) {
        short[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Short e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] plus(int[] source, Collection<Integer> elements) {
        int[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Integer e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] plus(long[] source, Collection<Long> elements) {
        long[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Long e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] plus(float[] source, Collection<Float> elements) {
        float[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Float e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] plus(double[] source, Collection<Double> elements) {
        double[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Double e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] plus(boolean[] source, Collection<Boolean> elements) {
        boolean[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Boolean e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] plus(char[] source, Collection<Character> elements) {
        char[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Character e : elements) {
            out[i++] = e;
        }
        return out;
    }

    // ============================================================================================
    // contains(array, element): ArraysKt.contains:(<E>[<E>)Z — linear membership test. Object[] uses
    // .equals (via indexOf); primitives use ==. (NB the float[]/double[] overloads are @Deprecated(HIDDEN)
    // synthetic on the real facade — off the audited surface — and IEEE-equality unsound, so they stay in
    // the tail, like the rest of the FP-equality residue.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean contains(T[] source, T element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(byte[] source, byte element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(short[] source, short element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(int[] source, int element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(long[] source, long element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(boolean[] source, boolean element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(char[] source, char element) {
        return indexOf(source, element) >= 0;
    }

    // ============================================================================================
    // indexOf(array, element): ArraysKt.indexOf:(<E>[<E>)I — first index of element, or -1. Object[]
    // compares with .equals (null-safe: a null element matches the first null slot); primitives with ==.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int indexOf(T[] source, T element) {
        if (element == null) {
            for (int i = 0; i < source.length; i++) {
                if (source[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < source.length; i++) {
                if (element.equals(source[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(byte[] source, byte element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(short[] source, short element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(int[] source, int element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(long[] source, long element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(boolean[] source, boolean element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(char[] source, char element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    // ============================================================================================
    // lastIndexOf(array, element): ArraysKt.lastIndexOf:(<E>[<E>)I — last index of element, or -1. Same
    // equality discipline as indexOf, scanning from the high end.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int lastIndexOf(T[] source, T element) {
        if (element == null) {
            for (int i = source.length - 1; i >= 0; i--) {
                if (source[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = source.length - 1; i >= 0; i--) {
                if (element.equals(source[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(byte[] source, byte element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(short[] source, short element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(int[] source, int element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(long[] source, long element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(boolean[] source, boolean element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(char[] source, char element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    // ============================================================================================
    // first(array): ArraysKt.first:(<E>[)<E> — element 0, or NoSuchElementException if empty (Kotlin's
    // contract). The primitive forms return the unboxed element.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T first(T[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte first(byte[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short first(short[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int first(int[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long first(long[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float first(float[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double first(double[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean first(boolean[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char first(char[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    // ============================================================================================
    // last(array): ArraysKt.last:(<E>[)<E> — the final element, or NoSuchElementException if empty.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T last(T[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte last(byte[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short last(short[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int last(int[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long last(long[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float last(float[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double last(double[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean last(boolean[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char last(char[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    // ============================================================================================
    // single(array): ArraysKt.single:(<E>[)<E> — the sole element; NoSuchElementException if empty,
    // IllegalArgumentException if more than one (Kotlin's contract).
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T single(T[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte single(byte[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short single(short[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int single(int[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long single(long[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float single(float[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double single(double[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean single(boolean[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char single(char[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    // ============================================================================================
    // getOrNull(array, index): ArraysKt.getOrNull:(<E>[I)<E-or-Boxed> — the element at index, or null if
    // out of bounds. Bounds-safe (no throw). The primitive forms return the BOXED type (Kotlin returns
    // the nullable T?, so an Int? = java.lang.Integer), null when out of range.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T getOrNull(T[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Byte getOrNull(byte[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Short getOrNull(short[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer getOrNull(int[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long getOrNull(long[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Float getOrNull(float[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Double getOrNull(double[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Boolean getOrNull(boolean[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character getOrNull(char[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }
}
