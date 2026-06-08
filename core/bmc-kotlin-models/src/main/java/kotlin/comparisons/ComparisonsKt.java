package kotlin.comparisons;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean model of the {@code kotlin.comparisons.ComparisonsKt} facade members bmc4j needs. The
 * {@code Comparator} that Kotlin generates for {@code sortedBy { keySelector }} (and {@code
 * compareBy}/{@code maxBy} friends) implements {@code compare} by calling
 * {@code ComparisonsKt.compareValues(a, b)} on the selected keys. The real facade reaches kotlin-
 * stdlib internals JBMC stubs to a NONDET int — which silently unsoundens any sort/min/max-by. This
 * models the documented contract: natural-ordering compare via {@code Comparable.compareTo}, with
 * nulls ordered first (a null is "less than" any non-null, two nulls are equal). Sound for boxed
 * primitives and Strings' lexicographic compareTo.
 */
@BmcModelTail(reason = "exotic ComparisonsKt facade remainder — compareBy/thenBy builders, nullsFirst/"
        + "nullsLast, min/maxOf variadics the bounded proofs do not exercise; loud under JBMC if reached")
public final class ComparisonsKt {

    private ComparisonsKt() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int compareValues(Comparable a, Comparable b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void compareBy(kotlin.jvm.functions.Function1[] a0) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.compareBy(kotlin.jvm.functions.Function1[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void compareValuesBy(java.lang.Object a0, java.lang.Object a1, kotlin.jvm.functions.Function1[] a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.compareValuesBy(java.lang.Object,java.lang.Object,kotlin.jvm.functions.Function1[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(byte a0, byte[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(byte,byte[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(double a0, double[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(double,double[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(float a0, float[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(float,float[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(int a0, int[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(int,int[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(java.lang.Comparable a0, java.lang.Comparable a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(java.lang.Comparable a0, java.lang.Comparable a1, java.lang.Comparable a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(java.lang.Comparable,java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(java.lang.Comparable a0, java.lang.Comparable[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(java.lang.Comparable,java.lang.Comparable[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(java.lang.Object a0, java.lang.Object a1, java.lang.Object a2, java.util.Comparator a3) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(java.lang.Object,java.lang.Object,java.lang.Object,java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(java.lang.Object a0, java.lang.Object a1, java.util.Comparator a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(java.lang.Object,java.lang.Object,java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(java.lang.Object a0, java.lang.Object[] a1, java.util.Comparator a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(java.lang.Object,java.lang.Object[],java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(long a0, long[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(long,long[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void maxOf(short a0, short[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.maxOf(short,short[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(byte a0, byte[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(byte,byte[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(double a0, double[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(double,double[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(float a0, float[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(float,float[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(int a0, int[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(int,int[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(java.lang.Comparable a0, java.lang.Comparable a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(java.lang.Comparable a0, java.lang.Comparable a1, java.lang.Comparable a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(java.lang.Comparable,java.lang.Comparable,java.lang.Comparable) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(java.lang.Comparable a0, java.lang.Comparable[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(java.lang.Comparable,java.lang.Comparable[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(java.lang.Object a0, java.lang.Object a1, java.lang.Object a2, java.util.Comparator a3) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(java.lang.Object,java.lang.Object,java.lang.Object,java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(java.lang.Object a0, java.lang.Object a1, java.util.Comparator a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(java.lang.Object,java.lang.Object,java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(java.lang.Object a0, java.lang.Object[] a1, java.util.Comparator a2) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(java.lang.Object,java.lang.Object[],java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(long a0, long[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(long,long[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void minOf(short a0, short[] a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.minOf(short,short[]) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void naturalOrder() {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.naturalOrder() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void nullsFirst(java.util.Comparator a0) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.nullsFirst(java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void nullsLast(java.util.Comparator a0) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.nullsLast(java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reverseOrder() {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.reverseOrder() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reversed(java.util.Comparator a0) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.reversed(java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void then(java.util.Comparator a0, java.util.Comparator a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.then(java.util.Comparator,java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcUnmodelable(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void thenDescending(java.util.Comparator a0, java.util.Comparator a1) {
        throw fail("bmc4j: unmodelled member kotlin.comparisons.ComparisonsKt.thenDescending(java.util.Comparator,java.util.Comparator) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

}
