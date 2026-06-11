package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the NON-INLINE CollectionsKt residue modeled in the
 * models/kotlin-collections-3 pass over bmc4j's bounded java.util collection models. Each member's
 * inline-ness was VERIFIED against kotlin-stdlib 2.4.0 @Metadata (Attributes.isInline == false); the
 * lambda-taking siblings of the same name (count{}/any{}/firstOrNull{}/sumOf{}/partition{}/maxBy{}/…)
 * are inline and stay @BmcUnmodelable (loud-if-reached) — they are NOT exercised here.
 *
 * Covered families: count/any/none (no-predicate), firstOrNull/lastOrNull (Iterable + List),
 * maxOrThrow/minOrThrow, maxWith/minWith OrNull/OrThrow (Comparator), the remaining typed
 * sumOf/averageOf, filterNotNull(+To), listOfNotNull, arrayListOf(vararg), indices/lastIndex,
 * reverse (in-place), removeFirst/removeLast(+OrNull), requireNoNulls, unzip, zip(array),
 * zipWithNext, withIndex, toHashSet/toCollection, and the to-X-Array primitive snapshots.
 *
 * Each pins the observable with a concrete + (where the symbolic circuit stays in budget) a symbolic
 * check, so a wrong or nondet-stubbed facade is caught. Ranges are tight and lists ≤4 elements per the
 * bounded-proof convention. Doubles/floats are concrete only (no-symbolic-FP proof policy).
 */
class KotlinCollectionResidue3Laws {

    // ---- count / any / none ----

    @BmcProof(unwind = 4)
    fun count_is_size() {
        Bmc.check(listOf(1, 2, 3).count() == 3 && emptyList<Int>().count() == 0)
    }

    @BmcProof
    fun any_none_track_emptiness() {
        Bmc.check(listOf(1).any() && !emptyList<Int>().any() &&
            emptyList<Int>().none() && !listOf(1).none())
    }

    @BmcProof(unwind = 4)
    fun symbolic_count() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        Bmc.check(listOf(a, b).count() == 2)
    }

    // ---- firstOrNull / lastOrNull ----

    @BmcProof(unwind = 4)
    fun firstOrNull_lastOrNull_ends() {
        val xs = listOf(10, 20, 30)
        Bmc.check(xs.firstOrNull() == 10 && xs.lastOrNull() == 30)
    }

    @BmcProof
    fun firstOrNull_lastOrNull_null_on_empty() {
        Bmc.check(emptyList<Int>().firstOrNull() == null && emptyList<Int>().lastOrNull() == null)
    }

    @BmcProof(unwind = 4)
    fun symbolic_firstOrNull_lastOrNull() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val xs = listOf(a, b)
        Bmc.check(xs.firstOrNull() == a && xs.lastOrNull() == b)
    }

    // ---- maxOrThrow / minOrThrow (max()/min()) ----

    @BmcProof(unwind = 8)
    fun max_min_pick_extremes() {
        val xs = listOf(3, 1, 4, 2)
        Bmc.check(xs.max() == 4 && xs.min() == 1)
    }

    @BmcProof(unwind = 4)
    fun symbolic_max_min_bracket() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200) // strictly greater
        val xs = listOf(a, b)
        Bmc.check(xs.max() == b && xs.min() == a)
    }

    // ---- maxWith / minWith (Comparator) OrNull + OrThrow ----

    @BmcProof(unwind = 4)
    fun maxWith_minWith_use_comparator() {
        val cmp = Comparator<Int> { x, y -> x - y }
        val xs = listOf(2, 5, 1)
        Bmc.check(xs.maxWith(cmp) == 5 && xs.minWith(cmp) == 1)
    }

    @BmcProof
    fun maxWithOrNull_null_on_empty() {
        val cmp = Comparator<Int> { x, y -> x - y }
        Bmc.check(emptyList<Int>().maxWithOrNull(cmp) == null && emptyList<Int>().minWithOrNull(cmp) == null)
    }

    @BmcProof(unwind = 4)
    fun symbolic_maxWith_reverse_comparator() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        // reverse comparator -> maxWith picks the natural MINIMUM
        val cmp = Comparator<Int> { x, y -> y - x }
        Bmc.check(listOf(a, b).maxWith(cmp) == a && listOf(a, b).minWith(cmp) == b)
    }

    // ---- sumOf* / averageOf* (remaining typed forms) ----

    @BmcProof(unwind = 4)
    fun sum_typed_byte_short() {
        val bytes: List<Byte> = listOf(1.toByte(), 2.toByte(), 3.toByte())
        val shorts: List<Short> = listOf(10.toShort(), 20.toShort())
        Bmc.check(bytes.sum() == 6 && shorts.sum() == 30)
    }

    @BmcProof(unwind = 4)
    fun sum_typed_float() {
        val floats = listOf(1.5f, 2.5f)
        Bmc.check(floats.sum() == 4.0f)
    }

    @BmcProof(unwind = 4)
    fun average_typed() {
        val ints = listOf(2, 4, 6)
        val doubles = listOf(10.0, 20.0)
        Bmc.check(ints.average() == 4.0 && doubles.average() == 15.0)
    }

    @BmcProof(unwind = 4)
    fun symbolic_sumOfShort() {
        val a = Bmc.anyInt(-100, 100).toShort()
        val b = Bmc.anyInt(-100, 100).toShort()
        val xs: List<Short> = listOf(a, b)
        Bmc.check(xs.sum() == a.toInt() + b.toInt())
    }

    // ---- filterNotNull / filterNotNullTo ----

    @BmcProof(unwind = 8)
    fun filterNotNull_drops_nulls() {
        val xs: List<Int?> = listOf(1, null, 2, null, 3)
        val r = xs.filterNotNull()
        Bmc.check(r.size == 3 && r[0] == 1 && r[1] == 2 && r[2] == 3)
    }

    @BmcProof(unwind = 4)
    fun filterNotNullTo_appends_nonnull() {
        val dest = ArrayList<Int>()
        val xs: List<Int?> = listOf(1, null, 2)
        xs.filterNotNullTo(dest)
        Bmc.check(dest.size == 2 && dest[0] == 1 && dest[1] == 2)
    }

    @BmcProof(unwind = 4)
    fun symbolic_filterNotNull() {
        val a = Bmc.anyInt(-100, 100)
        val xs: List<Int?> = listOf(a, null)
        val r = xs.filterNotNull()
        Bmc.check(r.size == 1 && r[0] == a)
    }

    // ---- listOfNotNull / arrayListOf(vararg) ----

    @BmcProof(unwind = 4)
    fun listOfNotNull_filters() {
        Bmc.check(listOfNotNull(5).size == 1 && listOfNotNull<Int>(null).isEmpty() &&
            listOfNotNull(1, null, 2).size == 2)
    }

    @BmcProof(unwind = 4)
    fun arrayListOf_holds_args() {
        val a = arrayListOf(7, 8, 9)
        Bmc.check(a.size == 3 && a[0] == 7 && a[2] == 9)
    }

    // ---- indices / lastIndex ----

    @BmcProof(unwind = 4)
    fun indices_and_lastIndex() {
        val xs = listOf(10, 20, 30)
        Bmc.check(xs.lastIndex == 2 && xs.indices.first == 0 && xs.indices.last == 2)
    }

    @BmcProof
    fun indices_empty_is_empty_range() {
        Bmc.check(emptyList<Int>().lastIndex == -1 && emptyList<Int>().indices.isEmpty())
    }

    // ---- reverse (in-place) ----

    @BmcProof(unwind = 4)
    fun reverse_in_place() {
        val m = mutableListOf(1, 2, 3)
        m.reverse()
        Bmc.check(m[0] == 3 && m[1] == 2 && m[2] == 1)
    }

    @BmcProof(unwind = 4)
    fun symbolic_reverse_in_place() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val m = mutableListOf(a, b)
        m.reverse()
        Bmc.check(m[0] == b && m[1] == a)
    }

    // ---- removeFirst / removeLast (+OrNull) ----

    @BmcProof(unwind = 4)
    fun removeFirst_removeLast_pop_ends() {
        val m = mutableListOf(1, 2, 3)
        val f = m.removeFirst()
        val l = m.removeLast()
        Bmc.check(f == 1 && l == 3 && m.size == 1 && m[0] == 2)
    }

    @BmcProof
    fun removeFirstOrNull_null_on_empty() {
        val m = mutableListOf<Int>()
        Bmc.check(m.removeFirstOrNull() == null && m.removeLastOrNull() == null)
    }

    @BmcProof(unwind = 4)
    fun symbolic_removeFirst() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val m = mutableListOf(a, b)
        Bmc.check(m.removeFirst() == a && m.size == 1 && m[0] == b)
    }

    // ---- requireNoNulls ----

    @BmcProof(unwind = 4)
    fun requireNoNulls_passes_clean() {
        val xs: List<Int?> = listOf(1, 2, 3)
        val r = xs.requireNoNulls()
        Bmc.check(r.size == 3 && r[1] == 2)
    }

    // ---- unzip ----

    @BmcProof(unwind = 4)
    fun unzip_splits_pairs() {
        val (a, b) = listOf(1 to "x", 2 to "y", 3 to "z").unzip()
        Bmc.check(a.size == 3 && a[0] == 1 && a[2] == 3 && b.size == 3 && b[1] == "y")
    }

    @BmcProof(unwind = 2)
    fun symbolic_unzip() {
        val x = Bmc.anyInt(-100, 100)
        val y = Bmc.anyInt(-100, 100)
        val (firsts, seconds) = listOf(x to y).unzip()
        Bmc.check(firsts.size == 1 && firsts[0] == x && seconds[0] == y)
    }

    // ---- zip(array) / zipWithNext ----

    @BmcProof(unwind = 4)
    fun zip_array_truncates_to_shorter() {
        val ps = listOf(1, 2, 3).zip(arrayOf("a", "b"))
        Bmc.check(ps.size == 2 && ps[0].first == 1 && ps[0].second == "a" && ps[1].first == 2)
    }

    @BmcProof(unwind = 4)
    fun zipWithNext_consecutive_pairs() {
        val ps = listOf(1, 2, 3).zipWithNext()
        Bmc.check(ps.size == 2 && ps[0].first == 1 && ps[0].second == 2 && ps[1].first == 2 && ps[1].second == 3)
    }

    @BmcProof
    fun zipWithNext_empty_when_too_short() {
        Bmc.check(listOf(1).zipWithNext().isEmpty())
    }

    // ---- withIndex ----

    @BmcProof(unwind = 4)
    fun withIndex_pairs_index_value() {
        val xs = ArrayList<Int>()
        for ((i, v) in listOf(10, 20, 30).withIndex()) xs.add(i * 100 + v)
        Bmc.check(xs.size == 3 && xs[0] == 10 && xs[1] == 120 && xs[2] == 230)
    }

    // ---- toHashSet / toCollection ----

    @BmcProof(unwind = 8)
    fun toHashSet_dedups() {
        val s = listOf(1, 2, 2, 3).toHashSet()
        Bmc.check(s.size == 3 && s.contains(2) && !s.contains(9))
    }

    @BmcProof(unwind = 4)
    fun toCollection_drains_into_destination() {
        val dest = ArrayList<Int>()
        listOf(1, 2, 3).toCollection(dest)
        Bmc.check(dest.size == 3 && dest[0] == 1 && dest[2] == 3)
    }

    // ---- to*Array primitive snapshots ----

    @BmcProof(unwind = 4)
    fun toIntArray_snapshots() {
        val a = listOf(4, 5, 6).toIntArray()
        Bmc.check(a.size == 3 && a[0] == 4 && a[2] == 6)
    }

    @BmcProof(unwind = 4)
    fun toLongArray_snapshots() {
        val a = listOf(7L, 8L).toLongArray()
        Bmc.check(a.size == 2 && a[0] == 7L && a[1] == 8L)
    }

    @BmcProof(unwind = 4)
    fun symbolic_toIntArray() {
        val x = Bmc.anyInt(-100, 100)
        val y = Bmc.anyInt(-100, 100)
        val a = listOf(x, y).toIntArray()
        Bmc.check(a.size == 2 && a[0] == x && a[1] == y)
    }
}
