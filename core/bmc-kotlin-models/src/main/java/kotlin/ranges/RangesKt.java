package kotlin.ranges;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotNeeded;

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

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void byteRangeContains(kotlin.ranges.ClosedRange a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.byteRangeContains(kotlin.ranges.ClosedRange,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void byteRangeContains(kotlin.ranges.ClosedRange a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.byteRangeContains(kotlin.ranges.ClosedRange,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void byteRangeContains(kotlin.ranges.ClosedRange a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.byteRangeContains(kotlin.ranges.ClosedRange,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void byteRangeContains(kotlin.ranges.OpenEndRange a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.byteRangeContains(kotlin.ranges.OpenEndRange,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void byteRangeContains(kotlin.ranges.OpenEndRange a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.byteRangeContains(kotlin.ranges.OpenEndRange,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void byteRangeContains(kotlin.ranges.OpenEndRange a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.byteRangeContains(kotlin.ranges.OpenEndRange,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void checkStepIsPositive(boolean a0, java.lang.Number a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.checkStepIsPositive(boolean,java.lang.Number) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtLeast(byte a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtLeast(byte,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtLeast(double a0, double a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtLeast(double,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtLeast(float a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtLeast(float,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtLeast(java.lang.Comparable a0, java.lang.Comparable a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtLeast(java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtLeast(short a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtLeast(short,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtMost(byte a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtMost(byte,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtMost(double a0, double a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtMost(double,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtMost(float a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtMost(float,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtMost(java.lang.Comparable a0, java.lang.Comparable a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtMost(java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceAtMost(short a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceAtMost(short,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(byte a0, byte a1, byte a2) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(byte,byte,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(double a0, double a1, double a2) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(double,double,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(float a0, float a1, float a2) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(float,float,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(int a0, kotlin.ranges.ClosedRange a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(int,kotlin.ranges.ClosedRange) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(java.lang.Comparable a0, java.lang.Comparable a1, java.lang.Comparable a2) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(java.lang.Comparable,java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(java.lang.Comparable a0, kotlin.ranges.ClosedFloatingPointRange a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(java.lang.Comparable,kotlin.ranges.ClosedFloatingPointRange) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(java.lang.Comparable a0, kotlin.ranges.ClosedRange a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(java.lang.Comparable,kotlin.ranges.ClosedRange) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(long a0, kotlin.ranges.ClosedRange a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(long,kotlin.ranges.ClosedRange) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void coerceIn(short a0, short a1, short a2) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.coerceIn(short,short,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void doubleRangeContains(kotlin.ranges.ClosedRange a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.doubleRangeContains(kotlin.ranges.ClosedRange,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void doubleRangeContains(kotlin.ranges.OpenEndRange a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.doubleRangeContains(kotlin.ranges.OpenEndRange,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(byte a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(byte,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(byte a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(byte,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(byte a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(byte,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(byte a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(byte,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(char a0, char a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(char,char) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(int a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(int,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(int a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(int,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(int a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(int,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(int a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(int,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(long a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(long,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(long a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(long,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(long a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(long,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(long a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(long,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(short a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(short,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(short a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(short,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(short a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(short,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void downTo(short a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.downTo(short,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void floatRangeContains(kotlin.ranges.ClosedRange a0, double a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.floatRangeContains(kotlin.ranges.ClosedRange,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void intRangeContains(kotlin.ranges.ClosedRange a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.intRangeContains(kotlin.ranges.ClosedRange,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void intRangeContains(kotlin.ranges.ClosedRange a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.intRangeContains(kotlin.ranges.ClosedRange,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void intRangeContains(kotlin.ranges.ClosedRange a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.intRangeContains(kotlin.ranges.ClosedRange,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void intRangeContains(kotlin.ranges.OpenEndRange a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.intRangeContains(kotlin.ranges.OpenEndRange,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void intRangeContains(kotlin.ranges.OpenEndRange a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.intRangeContains(kotlin.ranges.OpenEndRange,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void intRangeContains(kotlin.ranges.OpenEndRange a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.intRangeContains(kotlin.ranges.OpenEndRange,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void longRangeContains(kotlin.ranges.ClosedRange a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.longRangeContains(kotlin.ranges.ClosedRange,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void longRangeContains(kotlin.ranges.ClosedRange a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.longRangeContains(kotlin.ranges.ClosedRange,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void longRangeContains(kotlin.ranges.ClosedRange a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.longRangeContains(kotlin.ranges.ClosedRange,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void longRangeContains(kotlin.ranges.OpenEndRange a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.longRangeContains(kotlin.ranges.OpenEndRange,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void longRangeContains(kotlin.ranges.OpenEndRange a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.longRangeContains(kotlin.ranges.OpenEndRange,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void longRangeContains(kotlin.ranges.OpenEndRange a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.longRangeContains(kotlin.ranges.OpenEndRange,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void rangeTo(double a0, double a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.rangeTo(double,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void rangeTo(float a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.rangeTo(float,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void rangeTo(java.lang.Comparable a0, java.lang.Comparable a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.rangeTo(java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void rangeUntil(double a0, double a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.rangeUntil(double,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void rangeUntil(float a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.rangeUntil(float,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void rangeUntil(java.lang.Comparable a0, java.lang.Comparable a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.rangeUntil(java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reversed(kotlin.ranges.CharProgression a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.reversed(kotlin.ranges.CharProgression) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reversed(kotlin.ranges.IntProgression a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.reversed(kotlin.ranges.IntProgression) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reversed(kotlin.ranges.LongProgression a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.reversed(kotlin.ranges.LongProgression) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void shortRangeContains(kotlin.ranges.ClosedRange a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.shortRangeContains(kotlin.ranges.ClosedRange,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void shortRangeContains(kotlin.ranges.ClosedRange a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.shortRangeContains(kotlin.ranges.ClosedRange,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void shortRangeContains(kotlin.ranges.ClosedRange a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.shortRangeContains(kotlin.ranges.ClosedRange,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void shortRangeContains(kotlin.ranges.OpenEndRange a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.shortRangeContains(kotlin.ranges.OpenEndRange,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void shortRangeContains(kotlin.ranges.OpenEndRange a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.shortRangeContains(kotlin.ranges.OpenEndRange,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void shortRangeContains(kotlin.ranges.OpenEndRange a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.shortRangeContains(kotlin.ranges.OpenEndRange,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void step(kotlin.ranges.CharProgression a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.step(kotlin.ranges.CharProgression,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void step(kotlin.ranges.IntProgression a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.step(kotlin.ranges.IntProgression,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void step(kotlin.ranges.LongProgression a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.step(kotlin.ranges.LongProgression,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toByteExactOrNull(double a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toByteExactOrNull(double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toByteExactOrNull(float a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toByteExactOrNull(float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toByteExactOrNull(int a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toByteExactOrNull(int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toByteExactOrNull(long a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toByteExactOrNull(long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toByteExactOrNull(short a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toByteExactOrNull(short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toIntExactOrNull(double a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toIntExactOrNull(double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toIntExactOrNull(float a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toIntExactOrNull(float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toIntExactOrNull(long a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toIntExactOrNull(long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toLongExactOrNull(double a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toLongExactOrNull(double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toLongExactOrNull(float a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toLongExactOrNull(float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toShortExactOrNull(double a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toShortExactOrNull(double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toShortExactOrNull(float a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toShortExactOrNull(float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toShortExactOrNull(int a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toShortExactOrNull(int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void toShortExactOrNull(long a0) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.toShortExactOrNull(long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(byte a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(byte,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(byte a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(byte,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(byte a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(byte,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(byte a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(byte,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(char a0, char a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(char,char) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(int a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(int,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(int a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(int,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(int a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(int,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(int a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(int,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(long a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(long,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(long a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(long,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(long a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(long,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(long a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(long,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(short a0, byte a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(short,byte) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(short a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(short,int) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(short a0, long a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(short,long) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void until(short a0, short a1) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.until(short,short) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

}
