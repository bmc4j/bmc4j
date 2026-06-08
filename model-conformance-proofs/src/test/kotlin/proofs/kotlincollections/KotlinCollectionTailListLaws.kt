package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the NON-INLINE CollectionsKt list-tail members modeled in the
 * models/kotlin-collections-2 pass over bmc4j's bounded java.util collection models:
 * flatten, sortedDescending, sort/sortDescending/sortWith (in-place), asReversed/asReversedMutable,
 * takeLast/dropLast, slice (IntRange + index list), chunked, and windowed (explicit full-arg).
 *
 * Each pins the observable with a concrete + (where the symbolic circuit stays in budget) a symbolic
 * check, so a wrong or nondet-stubbed facade is caught. These are all non-inline facade JVM methods
 * verified against the kotlin-stdlib 2.4 bytecode signatures; the inline lambda-taking siblings
 * (distinctBy/takeLastWhile/sortedBy/…) stay @BmcUnmodelable (loud-if-reached) and are not exercised here.
 *
 * `windowed` is called with the EXPLICIT full-arg overload windowed(size, step, partialWindows): the
 * defaulted call windowed(size) routes through the unmodeled `windowed$default` bridge (JBMC nondet-
 * stubs it -> UNKNOWN regardless of body), so it stays in the tail and is not proven here.
 */
class KotlinCollectionTailListLaws {

    // ---- flatten ----

    @BmcProof
    fun flatten_concatenates_inner() {
        val xs = listOf(listOf(1, 2), listOf(3), listOf(4, 5)).flatten()
        Bmc.check(xs.size == 5 && xs[0] == 1 && xs[2] == 3 && xs[4] == 5)
    }

    @BmcProof
    fun symbolic_flatten() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val xs = listOf(listOf(a), listOf(b)).flatten()
        Bmc.check(xs.size == 2 && xs[0] == a && xs[1] == b)
    }

    // ---- sortedDescending (new list) ----

    @BmcProof
    fun sortedDescending_orders_high_to_low() {
        val xs = listOf(2, 5, 1, 4).sortedDescending()
        Bmc.check(xs.size == 4 && xs[0] == 5 && xs[1] == 4 && xs[2] == 2 && xs[3] == 1)
    }

    @BmcProof
    fun sortedDescending_leaves_source_unchanged() {
        val src = listOf(1, 3, 2)
        val sorted = src.sortedDescending()
        Bmc.check(sorted[0] == 3 && src[0] == 1 && src[1] == 3 && src[2] == 2)
    }

    /** Symbolic descending law: for distinct a<b, sortedDescending puts b first. */
    @BmcProof
    fun symbolic_sortedDescending() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200) // strictly greater than a
        val xs = listOf(a, b).sortedDescending()
        Bmc.check(xs.size == 2 && xs[0] == b && xs[1] == a)
    }

    // ---- sort / sortDescending / sortWith (in-place over a MutableList) ----

    @BmcProof
    fun sort_in_place_ascending() {
        val m = mutableListOf(3, 1, 2)
        m.sort()
        Bmc.check(m[0] == 1 && m[1] == 2 && m[2] == 3)
    }

    @BmcProof
    fun sortDescending_in_place() {
        val m = mutableListOf(1, 3, 2)
        m.sortDescending()
        Bmc.check(m[0] == 3 && m[1] == 2 && m[2] == 1)
    }

    @BmcProof
    fun sortWith_uses_comparator() {
        val m = mutableListOf(1, 2, 3)
        m.sortWith(Comparator { x, y -> y - x }) // reverse comparator
        Bmc.check(m[0] == 3 && m[1] == 2 && m[2] == 1)
    }

    /** Symbolic in-place sort law: for distinct a<b, sorting {b,a} yields {a,b}. */
    @BmcProof
    fun symbolic_sort_in_place() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val m = mutableListOf(b, a)
        m.sort()
        Bmc.check(m[0] == a && m[1] == b)
    }

    // ---- asReversed / asReversedMutable (reversed read observable) ----

    @BmcProof
    fun asReversed_reads_reverse_order() {
        val r = listOf(1, 2, 3).asReversed()
        Bmc.check(r.size == 3 && r[0] == 3 && r[1] == 2 && r[2] == 1)
    }

    @BmcProof
    fun asReversedMutable_reads_reverse_order() {
        val r = mutableListOf(1, 2, 3).asReversed() // asReversed on MutableList -> asReversedMutable
        Bmc.check(r.size == 3 && r[0] == 3 && r[2] == 1)
    }

    /** Symbolic asReversed law: index i reads source index (n-1-i). */
    @BmcProof
    fun symbolic_asReversed() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val r = listOf(a, b).asReversed()
        Bmc.check(r.size == 2 && r[0] == b && r[1] == a)
    }

    // ---- takeLast / dropLast ----

    @BmcProof
    fun takeLast_keeps_tail() {
        val xs = listOf(1, 2, 3, 4).takeLast(2)
        Bmc.check(xs.size == 2 && xs[0] == 3 && xs[1] == 4)
    }

    @BmcProof
    fun takeLast_clamps_to_size() {
        val xs = listOf(1, 2).takeLast(5)
        Bmc.check(xs.size == 2 && xs[0] == 1 && xs[1] == 2)
    }

    @BmcProof
    fun dropLast_drops_tail() {
        val xs = listOf(1, 2, 3, 4).dropLast(2)
        Bmc.check(xs.size == 2 && xs[0] == 1 && xs[1] == 2)
    }

    @BmcProof
    fun dropLast_overshoot_is_empty() {
        val xs = listOf(1, 2).dropLast(5)
        Bmc.check(xs.isEmpty())
    }

    /** Symbolic takeLast/dropLast partition law: takeLast(1) + dropLast(1) recover the two elements. */
    @BmcProof
    fun symbolic_takeLast_dropLast() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val src = listOf(a, b)
        val tail = src.takeLast(1)
        val head = src.dropLast(1)
        Bmc.check(tail.size == 1 && tail[0] == b && head.size == 1 && head[0] == a)
    }

    // ---- slice (IntRange + explicit index list) ----

    @BmcProof
    fun slice_intRange_inclusive() {
        val xs = listOf(10, 20, 30, 40).slice(1..2)
        Bmc.check(xs.size == 2 && xs[0] == 20 && xs[1] == 30)
    }

    @BmcProof
    fun slice_index_list() {
        val xs = listOf(10, 20, 30, 40).slice(listOf(0, 2))
        Bmc.check(xs.size == 2 && xs[0] == 10 && xs[1] == 30)
    }

    /** Symbolic slice law: slicing 0..1 of {a,b} recovers {a,b} in order. */
    @BmcProof
    fun symbolic_slice_range() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val xs = listOf(a, b).slice(0..1)
        Bmc.check(xs.size == 2 && xs[0] == a && xs[1] == b)
    }

    // ---- chunked ----

    @BmcProof
    fun chunked_even_split() {
        val cs = listOf(1, 2, 3, 4).chunked(2)
        Bmc.check(cs.size == 2 && cs[0].size == 2 && cs[0][0] == 1 && cs[1][1] == 4)
    }

    @BmcProof
    fun chunked_trailing_partial() {
        val cs = listOf(1, 2, 3).chunked(2)
        Bmc.check(cs.size == 2 && cs[0].size == 2 && cs[1].size == 1 && cs[1][0] == 3)
    }

    // ---- windowed (explicit full-arg: size, step, partialWindows) ----

    @BmcProof
    fun windowed_full_windows_only() {
        val ws = listOf(1, 2, 3, 4).windowed(2, 1, false)
        Bmc.check(ws.size == 3 && ws[0][0] == 1 && ws[0][1] == 2 && ws[2][0] == 3 && ws[2][1] == 4)
    }

    @BmcProof
    fun windowed_step_and_partial() {
        val ws = listOf(1, 2, 3, 4, 5).windowed(2, 2, true)
        Bmc.check(ws.size == 3 && ws[0][0] == 1 && ws[1][0] == 3 && ws[2].size == 1 && ws[2][0] == 5)
    }
}
