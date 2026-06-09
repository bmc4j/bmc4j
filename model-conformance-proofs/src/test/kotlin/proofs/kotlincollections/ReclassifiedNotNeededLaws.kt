package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Green-if-reached pins for the Kotlin facade members reclassified from a loud `@BmcUnmodelable` stub to
 * a documentary `@BmcNotNeeded` declaration (ComparisonsKt / RangesKt / CollectionsKt). bmc4j no longer
 * shadows these members with a model stub, so a reach falls through to the REAL kotlin-stdlib bytecode;
 * the reclassification rests on that real bytecode analyzing SOUNDLY under JBMC over the modeled surface
 * (bounded java.util collection models + integer/Comparable arithmetic). Each proof reaches a
 * representative reclassified member with a concrete + symbolic relation — a nondet/havoc stub could not
 * satisfy the symbolic relation, so a GREEN verdict is real evidence the member's real body was actually
 * analyzed (green-if-reached), not the old false-UNKNOWN demotion.
 *
 * Scope note: only the members PROVED green here were reclassified. The comparator builders
 * (naturalOrder/reverseOrder/nullsFirst/nullsLast/compareBy-vararg/then/…), the vararg maxOf/minOf array
 * forms and Comparator overloads, the RangesKt *RangeContains / range-object coerceIn / FP / rangeUntil
 * forms, the CollectionsKt OrNull accessors, and the Intrinsics reflective/reified/boxed-FP helpers were
 * PROBED and found NOT green-if-reached (REFUTED/UNKNOWN through the real bytecode), so they were kept as
 * loud `@BmcUnmodelable` walls — see those classes' declarations.
 */
class ReclassifiedNotNeededLaws {

    // ---- ComparisonsKt: scalar Comparable maxOf / minOf (2- and 3-arg) ---------------------------
    @BmcProof
    fun comparisons_maxOf_minOf_scalar_comparable() {
        Bmc.check(maxOf(3, 7) == 7 && minOf(3, 7) == 3)
        Bmc.check(maxOf(2, 5, 4) == 5 && minOf(2, 5, 4) == 2)
    }

    @BmcProof
    fun comparisons_symbolic_maxOf_minOf_comparable_bounds() {
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(-1000, 1000)
        val c = Bmc.anyInt(-1000, 1000)
        Bmc.check(maxOf(a, b, c) >= a && maxOf(a, b, c) >= b && maxOf(a, b, c) >= c)
        Bmc.check(minOf(a, b, c) <= a && minOf(a, b, c) <= b && minOf(a, b, c) <= c)
    }

    // ---- RangesKt: integer until/downTo/step/rangeTo/reversed + non-FP scalar coerce -------------
    @BmcProof
    fun ranges_until_downTo_step() {
        var s = 0
        for (i in 0 until 4) s += i           // until(int,int)
        var d = 0
        for (i in 3 downTo 1) d += i          // downTo(int,int)
        var n = 0
        for (i in 0..6 step 2) n++            // step(IntProgression,int)
        Bmc.check(s == 6 && d == 6 && n == 4)
    }

    @BmcProof
    fun ranges_until_downTo_long_mixed() {
        var s = 0L
        for (i in 0L until 3) s += i          // until(long,long)
        var c = 0
        for (i in 3L downTo 1L) c++           // downTo(long,long)
        Bmc.check(s == 3L && c == 3)
    }

    @BmcProof
    fun ranges_rangeTo_and_reversed() {
        Bmc.check(3 in (1..5) && 0 !in (1..5))    // rangeTo(Comparable)
        val xs = ArrayList<Int>()
        for (i in (1..3).reversed()) xs.add(i)    // reversed(IntProgression)
        Bmc.check(xs.size == 3 && xs[0] == 3 && xs[2] == 1)
    }

    @BmcProof
    fun ranges_coerce_scalar() {
        val v = Bmc.anyInt(-100, 100)
        Bmc.check(v.coerceIn(-10, 10) in -10..10)          // coerceIn(int,int,int)
        val w = Bmc.anyLong(-1000, 1000)
        val hi = Bmc.anyLong(-1000, 1000)
        val c = w.coerceAtMost(hi)
        Bmc.check(c <= hi && (c == w || c == hi))           // coerceAtMost (scalar)
    }

    // ---- CollectionsKt read-only accessors: contains/elementAt/indexOf/lastIndexOf/last ----------
    @BmcProof
    fun collections_readonly_accessors() {
        val xs = listOf(10, 20, 30, 20)
        Bmc.check(xs.contains(20) && !xs.contains(99))      // contains(Iterable,Object)
        Bmc.check(xs.elementAt(1) == 20)                    // elementAt(Iterable,int)
        Bmc.check(xs.indexOf(20) == 1 && xs.lastIndexOf(20) == 3)
        Bmc.check(xs.last() == 20)                          // last(Iterable)
    }

    @BmcProof
    fun collections_symbolic_indexOf_locates() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200) // distinct from a
        val xs = listOf(a, b)
        Bmc.check(xs.indexOf(b) == 1 && xs.contains(a) && xs.elementAt(0) == a)
    }
}
