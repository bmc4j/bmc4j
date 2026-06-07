package kotlin.ranges;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of the {@code kotlin.ranges.RangesKt} facade members bmc4j needs. Kotlin's INLINE
 * {@code associate}/{@code associateBy}/{@code associateWith} emit
 * {@code RangesKt.coerceAtLeast(mapCapacity(size), 16)} to compute their {@code LinkedHashMap}
 * initial capacity. The real facade reaches kotlin-stdlib internals JBMC stubs to nondet, which then
 * poisons the {@code LinkedHashMap(int)} ctor; this models the trivial {@code max(a, b)} semantics so
 * the (capacity-ignoring) bounded map model is sized soundly.
 *
 * <p>{@code coerceAtMost}/{@code coerceIn} are modeled for the same reason consumers reach them
 * directly: this class REPLACES the stdlib facade on the analysis path, so any member it lacks is a
 * JBMC nondet stub — a call to un-modeled {@code coerceIn} produced a spurious counterexample
 * ({@code coerceIn(0, 0, 95) == 96}) the moment an example used it. Other primitive overloads
 * (double/float/Comparable) remain stubs until something needs them.
 */
@BmcModelTail(reason = "exotic RangesKt facade remainder — the bulk of kotlin-stdlib's range/coerce "
        + "primitive overloads (double/float/Comparable, until/downTo/step) the bounded proofs do not "
        + "exercise; loud under JBMC if reached")
public final class RangesKt {

    private RangesKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int coerceAtLeast(int value, int minimumValue) {
        return value < minimumValue ? minimumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long coerceAtLeast(long value, long minimumValue) {
        return value < minimumValue ? minimumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int coerceAtMost(int value, int maximumValue) {
        return value > maximumValue ? maximumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long coerceAtMost(long value, long maximumValue) {
        return value > maximumValue ? maximumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int coerceIn(int value, int minimumValue, int maximumValue) {
        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum "
                    + maximumValue + " is less than minimum " + minimumValue + ".");
        }
        if (value < minimumValue) {
            return minimumValue;
        }
        if (value > maximumValue) {
            return maximumValue;
        }
        return value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long coerceIn(long value, long minimumValue, long maximumValue) {
        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum "
                    + maximumValue + " is less than minimum " + minimumValue + ".");
        }
        if (value < minimumValue) {
            return minimumValue;
        }
        if (value > maximumValue) {
            return maximumValue;
        }
        return value;
    }
}
