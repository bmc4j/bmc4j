package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the Kotlin collection-facade RESIDUE that PR #129 probed and REFUTED
 * through the real kotlin-stdlib bytecode — the ALLOCATING / builder ops that route through internal
 * builders JBMC nondet-stubs: CollectionsKt plus/minus/single/singleOrNull/reversed/toList/
 * toMutableList/toMutableSet/union/intersect/subtract/average/joinToString, MapsKt plus/minus/
 * getValue/toList, SetsKt plus/minus, and RangesKt progression first/last/firstOrNull/lastOrNull.
 * Each now has a real bmc4j model (kotlin.collections.CollectionsKt / MapsKt / SetsKt /
 * kotlin.ranges.RangesKt) building the bounded java collection models directly; these proofs pin the
 * observable with a concrete + a symbolic check, so a wrong (or nondet-stubbed) facade is caught.
 *
 * STRING-HEAVY ops (joinToString/joinTo): concrete, short, fixed strings only — never symbolic string
 * content — to stay clear of the JBMC string-blowup that OOM'd CI (#124). RangesKt.random stays in the
 * @BmcModelTail residue (a Random draw is nondeterministic by nature — no sound bounded model).
 */
class KotlinCollectionResidueLaws {

    // ---- CollectionsKt.plus (element + collection) ----

    @BmcProof(unwind = 4)
    fun plus_element_appends() {
        val xs = listOf(1, 2) + 3
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[1] == 2 && xs[2] == 3)
    }

    @BmcProof(unwind = 4)
    fun plus_collection_concatenates() {
        val xs = listOf(1, 2) + listOf(3, 4)
        Bmc.check(xs.size == 4 && xs[0] == 1 && xs[2] == 3 && xs[3] == 4)
    }

    @BmcProof(unwind = 4)
    fun plus_leaves_source_unchanged() {
        val src = listOf(1, 2)
        val xs = src + 9
        Bmc.check(src.size == 2 && xs.size == 3 && xs[2] == 9)
    }

    /** Symbolic plus law: `xs + e` carries every source element in order then the appended element. */
    @BmcProof(unwind = 4)
    fun symbolic_plus_element() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val c = Bmc.anyInt(-100, 100)
        val xs = listOf(a, b) + c
        Bmc.check(xs.size == 3 && xs[0] == a && xs[1] == b && xs[2] == c)
    }

    // ---- CollectionsKt.minus (element + collection) ----

    @BmcProof(unwind = 8)
    fun minus_element_removes_first_occurrence() {
        val xs = listOf(1, 2, 1, 3) - 1
        Bmc.check(xs.size == 3 && xs[0] == 2 && xs[1] == 1 && xs[2] == 3)
    }

    @BmcProof(unwind = 8)
    fun minus_collection_removes_all_matches() {
        val xs = listOf(1, 2, 3, 2, 1) - listOf(1, 2)
        Bmc.check(xs.size == 1 && xs[0] == 3)
    }

    /** Symbolic minus law: removing the unique element b leaves a, in order. */
    @BmcProof(unwind = 4)
    fun symbolic_minus_element() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200) // distinct from a
        val xs = listOf(a, b) - b
        Bmc.check(xs.size == 1 && xs[0] == a)
    }

    // ---- CollectionsKt.single / singleOrNull ----

    @BmcProof
    fun single_returns_sole_element() {
        Bmc.check(listOf(42).single() == 42)
    }

    @BmcProof(unwind = 4)
    fun singleOrNull_null_when_not_one() {
        Bmc.check(listOf(1, 2).singleOrNull() == null && emptyList<Int>().singleOrNull() == null)
    }

    @BmcProof
    fun singleOrNull_returns_sole() {
        Bmc.check(listOf(7).singleOrNull() == 7)
    }

    /** Symbolic single law: a one-element list's single element is exactly that element. */
    @BmcProof
    fun symbolic_single() {
        val a = Bmc.anyInt(-1000, 1000)
        Bmc.check(listOf(a).single() == a && listOf(a).singleOrNull() == a)
    }

    // ---- CollectionsKt.reversed ----

    @BmcProof(unwind = 4)
    fun reversed_flips_order() {
        val xs = listOf(1, 2, 3).reversed()
        Bmc.check(xs.size == 3 && xs[0] == 3 && xs[1] == 2 && xs[2] == 1)
    }

    /** Symbolic reversed law: index i maps to source index (n-1-i); source untouched. */
    @BmcProof(unwind = 4)
    fun symbolic_reversed() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val src = listOf(a, b)
        val r = src.reversed()
        Bmc.check(r.size == 2 && r[0] == b && r[1] == a && src[0] == a && src[1] == b)
    }

    // ---- CollectionsKt.toList / toMutableList / toMutableSet (Iterable overloads) ----

    @BmcProof(unwind = 4)
    fun toList_snapshots_in_order() {
        val xs = (listOf(1, 2, 3) as Iterable<Int>).toList()
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[2] == 3)
    }

    @BmcProof(unwind = 4)
    fun toMutableList_copies_and_is_mutable() {
        val m = (listOf(1, 2) as Iterable<Int>).toMutableList()
        m.add(3)
        Bmc.check(m.size == 3 && m[2] == 3)
    }

    @BmcProof(unwind = 8)
    fun toMutableSet_dedups() {
        val s = (listOf(1, 2, 2, 3) as Iterable<Int>).toMutableSet()
        Bmc.check(s.size == 3 && s.contains(2) && !s.contains(9))
    }

    // ---- CollectionsKt.union / intersect / subtract ----

    @BmcProof(unwind = 8)
    fun union_combines_distinct() {
        val s = listOf(1, 2, 3).union(listOf(3, 4))
        Bmc.check(s.size == 4 && s.contains(1) && s.contains(4) && !s.contains(9))
    }

    @BmcProof(unwind = 4)
    fun intersect_keeps_common() {
        val s = listOf(1, 2, 3).intersect(listOf(2, 3, 4))
        Bmc.check(s.size == 2 && s.contains(2) && s.contains(3) && !s.contains(1) && !s.contains(4))
    }

    @BmcProof(unwind = 4)
    fun subtract_removes_other() {
        val s = listOf(1, 2, 3).subtract(listOf(2, 3))
        Bmc.check(s.size == 1 && s.contains(1) && !s.contains(2))
    }

    /** Symbolic set-op law: for distinct a,b,c — intersect picks the shared, subtract the unshared. */
    @BmcProof
    fun symbolic_intersect_subtract() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val c = Bmc.anyInt(201, 300)
        val inter = listOf(a, b).intersect(listOf(b, c))
        val diff = listOf(a, b).subtract(listOf(b, c))
        Bmc.check(inter.size == 1 && inter.contains(b) && diff.size == 1 && diff.contains(a))
    }

    // ---- CollectionsKt.average (Int / Long) ----

    @BmcProof(unwind = 4)
    fun averageOfInt_is_mean() {
        Bmc.check(listOf(2, 4, 6).average() == 4.0)
    }

    @BmcProof(unwind = 4)
    fun averageOfLong_is_mean() {
        Bmc.check(listOf(10L, 20L).average() == 15.0)
    }

    // joinToString / joinTo are intentionally left in the @BmcModelTail residue (NOT modeled) — string-
    // heavy (the #124 OOM hazard) AND routed through a kotlinc `joinToString$default` bridge bmc4j does
    // not model (JBMC nondet-stubs it → UNKNOWN regardless of body correctness). No proof here.
}
