package kotlin.comparisons;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;
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
 *
 * <p>The whole real surface is enumerated per-member: {@code compareValues} is modeled; the scalar
 * {@code maxOf}/{@code minOf} over {@code Comparable} (2- and 3-arg) are class-level {@code @BmcNotNeeded}
 * (green-if-reached — their real stdlib bytecode analyzes soundly, JBMC falls through to the real
 * facade); and the comparator builders (compareBy/compareValuesBy/naturalOrder/reverseOrder/reversed/
 * nullsFirst/nullsLast/then/thenDescending), the vararg {@code maxOf}/{@code minOf} array forms, and the
 * explicit-{@code Comparator} overloads stay loud class-level {@code @BmcUnmodelable} walls — each was
 * probed and found to NOT analyze soundly when reached (it routes through unmodeled kotlin-stdlib
 * comparator/array internals JBMC nondet-stubs, so it must demote loudly rather than verify on a
 * fiction). So the whole real surface is per-member accounted and no {@code @BmcModelTail} catch-all is
 * needed.
 */
@BmcUnmodelable(member = "compareBy(kotlin.jvm.functions.Function1[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "compareValuesBy(java.lang.Object, java.lang.Object, kotlin.jvm.functions.Function1[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(byte, byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(double, double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(float, float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(int, int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "maxOf(java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "maxOf(java.lang.Comparable, java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "maxOf(java.lang.Comparable, java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(java.lang.Object, java.lang.Object, java.lang.Object, java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(java.lang.Object, java.lang.Object, java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(java.lang.Object, java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(long, long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOf(short, short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(byte, byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(double, double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(float, float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(int, int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "minOf(java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "minOf(java.lang.Comparable, java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "minOf(java.lang.Comparable, java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(java.lang.Object, java.lang.Object, java.lang.Object, java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(java.lang.Object, java.lang.Object, java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(java.lang.Object, java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(long, long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOf(short, short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "naturalOrder()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "nullsFirst(java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "nullsLast(java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverseOrder()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "then(java.util.Comparator, java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "thenDescending(java.util.Comparator, java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
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

    // --- remaining facade surface: declared class-level above — @BmcNotNeeded (scalar Comparable
    //     maxOf/minOf, green-if-reached) and @BmcUnmodelable (comparator builders / varargs /
    //     Comparator overloads, loud-if-reached). ---
}
