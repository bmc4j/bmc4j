package proofs.kotlinsequences

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the Kotlin Sequences facade — sequenceOf/asSequence + the common
 * map/filter intermediate ops and toList/sum/count terminals, modeled in kotlin.sequences.SequencesKt
 * (+ a Sequence interface and eager ListSequence impl) and CollectionsKt.asSequence. Previously these
 * routed through stdlib internals JBMC stubbed to nondet (silently unsound). These @BmcProof laws use
 * concrete small inputs plus symbolic laws that confirm the user lambda is actually applied over the
 * bounded model (a nondet stub could not satisfy the symbolic relations).
 */
class SequenceLaws {

    @BmcProof
    fun sequenceOf_toList_preserves_elements() {
        val xs = sequenceOf(1, 2, 3).toList()
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[2] == 3)
    }

    @BmcProof
    fun map_then_toList() {
        val xs = sequenceOf(1, 2, 3).map { it * 2 }.toList()
        Bmc.check(xs.size == 3 && xs[0] == 2 && xs[1] == 4 && xs[2] == 6)
    }

    @BmcProof
    fun map_then_sum() {
        Bmc.check(sequenceOf(1, 2, 3).map { it + 1 }.sum() == 9)
    }

    @BmcProof
    fun filter_then_count() {
        Bmc.check(sequenceOf(1, 2, 3, 4).filter { it % 2 == 0 }.count() == 2)
    }

    @BmcProof
    fun filter_then_sum() {
        Bmc.check(sequenceOf(1, 2, 3, 4).filter { it % 2 == 0 }.sum() == 6)
    }

    @BmcProof
    fun count_is_length() {
        Bmc.check(sequenceOf(5, 6, 7).count() == 3)
    }

    @BmcProof
    fun asSequence_round_trip() {
        val xs = listOf(10, 20, 30).asSequence().toList()
        Bmc.check(xs.size == 3 && xs[0] == 10 && xs[2] == 30)
    }

    @BmcProof
    fun asSequence_filter_sum() {
        Bmc.check(listOf(1, 2, 3, 4).asSequence().filter { it > 1 }.sum() == 9)
    }

    @BmcProof
    fun map_filter_chain() {
        val n = sequenceOf(1, 2, 3, 4).map { it * 3 }.filter { it % 2 == 0 }.count()
        Bmc.check(n == 2) // 3,6,9,12 -> evens 6,12
    }

    /** Symbolic law: map(*2) then sum distributes — only true if the mapper is really applied. */
    @BmcProof
    fun symbolic_map_sum() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.check(sequenceOf(a, b).map { it * 2 }.sum() == 2 * (a + b))
    }

    /** Symbolic law: filtering by a predicate then counting matches the element-wise truth. */
    @BmcProof
    fun symbolic_filter_count() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val cnt = sequenceOf(a, b).filter { it > 0 }.count()
        val expected = (if (a > 0) 1 else 0) + (if (b > 0) 1 else 0)
        Bmc.check(cnt == expected)
    }

    // ---- take(n) / drop(n) intermediate ops (SequencesKt.take / drop facade models, eager).

    @BmcProof
    fun take_keeps_prefix() {
        val t = sequenceOf(1, 2, 3, 4).take(2).toList()
        Bmc.check(t.size == 2 && t[0] == 1 && t[1] == 2)
    }

    @BmcProof
    fun drop_skips_prefix() {
        val d = sequenceOf(1, 2, 3, 4).drop(2).toList()
        Bmc.check(d.size == 2 && d[0] == 3 && d[1] == 4)
    }

    /** Symbolic take/drop law: take(1)+drop(1) partition the sequence and concatenate to it. */
    @BmcProof
    fun symbolic_take_drop_partition() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val c = Bmc.anyInt(-100, 100)
        val t = sequenceOf(a, b, c).take(1).toList()
        val d = sequenceOf(a, b, c).drop(1).toList()
        Bmc.check(t.size == 1 && t[0] == a && d.size == 2 && d[0] == b && d[1] == c)
    }

    // ---- distinct() intermediate op (SequencesKt.distinct facade model via bounded LinkedHashSet).

    @BmcProof
    fun distinct_dedups_preserving_order() {
        val d = sequenceOf(3, 1, 3, 2, 1).distinct().toList()
        Bmc.check(d.size == 3 && d[0] == 3 && d[1] == 1 && d[2] == 2)
    }

    /** Symbolic distinct law: a duplicate collapses, a distinct element is kept, in order. */
    @BmcProof
    fun symbolic_distinct_collapses_duplicates() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val d = sequenceOf(a, a, b).distinct().toList()
        Bmc.check(d.size == 2 && d[0] == a && d[1] == b)
    }

    // ---- flatMap { } intermediate op (SequencesKt.flatMap facade model: concatenate inner seqs).

    @BmcProof
    fun flatMap_concatenates() {
        val f = sequenceOf(1, 2, 3).flatMap { sequenceOf(it, it * 10) }.toList()
        Bmc.check(f.size == 6 && f[0] == 1 && f[1] == 10 && f[2] == 2 && f[3] == 20 && f[4] == 3 && f[5] == 30)
    }

    /** Symbolic flatMap law: each element expands to a pair (e, e+1), in order. */
    @BmcProof
    fun symbolic_flatMap_expands() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val f = sequenceOf(a, b).flatMap { sequenceOf(it, it + 1) }.toList()
        Bmc.check(f.size == 4 && f[0] == a && f[1] == a + 1 && f[2] == b && f[3] == b + 1)
    }

    // ---- toSet() terminal (SequencesKt.toSet facade model, LinkedHashSet, dedup via equals).

    @BmcProof
    fun toSet_dedups() {
        val s = sequenceOf(1, 2, 2, 3, 1).toSet()
        Bmc.check(s.size == 3 && s.contains(1) && s.contains(2) && s.contains(3) && !s.contains(9))
    }

    /** Symbolic toSet law: a duplicate is absorbed; membership reflects the inputs. */
    @BmcProof
    fun symbolic_toSet_membership() {
        // Tight, disjoint domains: dedup compares via the objEquals-redirected equals, so a small
        // range keeps the formula cheap while still proving duplicate-absorption + membership.
        val a = Bmc.anyInt(0, 5)
        val b = Bmc.anyInt(6, 10)
        val s = sequenceOf(a, a, b).toSet()
        Bmc.check(s.size == 2 && s.contains(a) && s.contains(b))
    }

    // ---- fold / reduce / sumOf { } terminals: the Kotlin compiler fully INLINES these over the
    // modeled Sequence.iterator()/Iterator — no facade method, exercising the bounded model directly.

    @BmcProof
    fun fold_accumulates() {
        Bmc.check(sequenceOf(1, 2, 3, 4).fold(10) { acc, e -> acc + e } == 20)
    }

    @BmcProof
    fun reduce_combines() {
        Bmc.check(sequenceOf(1, 2, 3, 4).reduce { acc, e -> acc + e } == 10)
    }

    @BmcProof
    fun sumOf_selector() {
        Bmc.check(sequenceOf(1, 2, 3).sumOf { it * 2 } == 12)
    }

    /** Symbolic fold law: folding (+) from 0 over a sequence equals the sum. */
    @BmcProof
    fun symbolic_fold_is_sum() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        Bmc.check(sequenceOf(a, b).fold(0) { acc, e -> acc + e } == a + b)
    }

    /** Symbolic sumOf law: sumOf{*3} triples the sum — only true if the selector is applied. */
    @BmcProof
    fun symbolic_sumOf_selector() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.check(sequenceOf(a, b).sumOf { it * 3 } == 3 * (a + b))
    }

    // ---- mapIndexed / filterIndexed / withIndex (SequencesKt index-aware facade models, eager).

    @BmcProof
    fun mapIndexed_combines_index_and_value() {
        val xs = sequenceOf(10, 20, 30).mapIndexed { i, v -> i + v }.toList()
        Bmc.check(xs.size == 3 && xs[0] == 10 && xs[1] == 21 && xs[2] == 32)
    }

    @BmcProof
    fun filterIndexed_keeps_even_indices() {
        val xs = sequenceOf(5, 6, 7, 8).filterIndexed { i, _ -> i % 2 == 0 }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 5 && xs[1] == 7)
    }

    @BmcProof
    fun withIndex_pairs_index_and_value() {
        val xs = sequenceOf(7, 8, 9).withIndex().toList()
        Bmc.check(xs.size == 3 &&
            xs[0].index == 0 && xs[0].value == 7 &&
            xs[1].index == 1 && xs[1].value == 8 &&
            xs[2].index == 2 && xs[2].value == 9)
    }

    /** Symbolic mapIndexed law: index*1000 + value separates the two contributions per position. */
    @BmcProof
    fun symbolic_mapIndexed_index_and_value() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        val xs = sequenceOf(a, b).mapIndexed { i, v -> i * 1000 + v }.toList()
        Bmc.check(xs.size == 2 && xs[0] == a && xs[1] == 1000 + b)
    }

    // ---- onEach / onEachIndexed (SequencesKt facade models): pass elements through unchanged.

    @BmcProof
    fun onEach_passes_elements_through() {
        val xs = sequenceOf(1, 2, 3).onEach { }.toList()
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[1] == 2 && xs[2] == 3)
    }

    @BmcProof
    fun onEachIndexed_passes_elements_through() {
        val xs = sequenceOf(4, 5).onEachIndexed { _, _ -> }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 4 && xs[1] == 5)
    }

    // ---- firstOrNull() / lastOrNull() / elementAtOrNull (no-predicate facade models).

    @BmcProof
    fun firstOrNull_on_nonempty() {
        Bmc.check(sequenceOf(11, 22, 33).firstOrNull() == 11)
    }

    @BmcProof
    fun lastOrNull_on_nonempty() {
        Bmc.check(sequenceOf(11, 22, 33).lastOrNull() == 33)
    }

    @BmcProof
    fun elementAtOrNull_in_and_out_of_range() {
        val s = sequenceOf(11, 22, 33)
        Bmc.check(s.elementAtOrNull(1) == 22 && s.elementAtOrNull(3) == null && s.elementAtOrNull(-1) == null)
    }

    /** Symbolic firstOrNull/lastOrNull law: bracket the sequence's two ends. */
    @BmcProof
    fun symbolic_first_last_orNull() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        val s = sequenceOf(a, b, c)
        Bmc.check(s.firstOrNull() == a && s.lastOrNull() == c && s.elementAtOrNull(1) == b)
    }

    // ---- chunked(size) / windowed(size, step, partialWindows) (SequencesKt facade models, eager).

    @BmcProof
    fun chunked_splits_into_groups() {
        val cs = sequenceOf(1, 2, 3, 4).chunked(2).toList()
        Bmc.check(cs.size == 2 && cs[0].size == 2 && cs[0][0] == 1 && cs[0][1] == 2 &&
            cs[1].size == 2 && cs[1][0] == 3 && cs[1][1] == 4)
    }

    @BmcProof
    fun chunked_last_group_is_partial() {
        val cs = sequenceOf(1, 2, 3).chunked(2).toList()
        Bmc.check(cs.size == 2 && cs[0].size == 2 && cs[1].size == 1 && cs[1][0] == 3)
    }

    @BmcProof
    fun windowed_full_only_drops_partial() {
        // size 2, step 1, partialWindows=false over 3 elements -> [1,2],[2,3]
        val w = sequenceOf(1, 2, 3).windowed(2, 1, false).toList()
        Bmc.check(w.size == 2 && w[0][0] == 1 && w[0][1] == 2 && w[1][0] == 2 && w[1][1] == 3)
    }

    @BmcProof
    fun windowed_partial_keeps_tail() {
        // size 2, step 2, partialWindows=true over 3 elements -> [1,2],[3]
        val w = sequenceOf(1, 2, 3).windowed(2, 2, true).toList()
        Bmc.check(w.size == 2 && w[0].size == 2 && w[1].size == 1 && w[1][0] == 3)
    }

    @BmcProof
    fun windowed_transform_applied_per_window() {
        // size 2, step 1, partialWindows=false; transform sums each window -> 1+2, 2+3
        val sums = sequenceOf(1, 2, 3).windowed(2, 1, false) { it[0] + it[1] }.toList()
        Bmc.check(sums.size == 2 && sums[0] == 3 && sums[1] == 5)
    }

    // ---- zipWithNext() / zipWithNext(transform) / zip (SequencesKt facade models, eager).

    @BmcProof
    fun zipWithNext_pairs_adjacent() {
        val ps = sequenceOf(1, 2, 3).zipWithNext().toList()
        Bmc.check(ps.size == 2 && ps[0].first == 1 && ps[0].second == 2 && ps[1].first == 2 && ps[1].second == 3)
    }

    @BmcProof
    fun zipWithNext_transform_deltas() {
        val d = sequenceOf(1, 3, 6).zipWithNext { a, b -> b - a }.toList()
        Bmc.check(d.size == 2 && d[0] == 2 && d[1] == 3)
    }

    @BmcProof
    fun zip_stops_at_shorter() {
        val z = sequenceOf(1, 2, 3).zip(sequenceOf(10, 20)).toList()
        Bmc.check(z.size == 2 && z[0].first == 1 && z[0].second == 10 && z[1].first == 2 && z[1].second == 20)
    }

    @BmcProof
    fun zip_with_transform() {
        val z = sequenceOf(1, 2, 3).zip(sequenceOf(10, 20, 30)) { a, b -> a + b }.toList()
        Bmc.check(z.size == 3 && z[0] == 11 && z[1] == 22 && z[2] == 33)
    }

    /** Symbolic zipWithNext law: adjacent deltas reconstruct the differences. */
    @BmcProof
    fun symbolic_zipWithNext_deltas() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        val d = sequenceOf(a, b, c).zipWithNext { x, y -> y - x }.toList()
        Bmc.check(d.size == 2 && d[0] == b - a && d[1] == c - b)
    }

    // ---- plus / minus (SequencesKt facade models, eager).

    @BmcProof
    fun plus_element_appends() {
        val xs = (sequenceOf(1, 2, 3) + 4).toList()
        Bmc.check(xs.size == 4 && xs[3] == 4)
    }

    @BmcProof
    fun plus_iterable_concatenates() {
        val xs = (sequenceOf(1, 2) + listOf(3, 4)).toList()
        Bmc.check(xs.size == 4 && xs[0] == 1 && xs[2] == 3 && xs[3] == 4)
    }

    @BmcProof
    fun plus_sequence_concatenates() {
        val xs = (sequenceOf(1, 2) + sequenceOf(3, 4)).toList()
        Bmc.check(xs.size == 4 && xs[3] == 4)
    }

    @BmcProof
    fun minus_element_removes_first_occurrence() {
        val xs = (sequenceOf(1, 2, 1, 3) - 1).toList()
        Bmc.check(xs.size == 3 && xs[0] == 2 && xs[1] == 1 && xs[2] == 3)
    }

    @BmcProof
    fun minus_iterable_removes_all_contained() {
        val xs = (sequenceOf(1, 2, 3, 2) - listOf(2)).toList()
        Bmc.check(xs.size == 2 && xs[0] == 1 && xs[1] == 3)
    }

    /** Symbolic plus law: appending an element grows the sequence by one with that element last. */
    @BmcProof
    fun symbolic_plus_element() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        // Build the base with the 2-arg (vararg) sequenceOf to avoid the single-element overload.
        val xs = (sequenceOf(a, b) + c).toList()
        Bmc.check(xs.size == 3 && xs[0] == a && xs[1] == b && xs[2] == c)
    }

    // ---- sorted / sortedDescending / sortedWith (SequencesKt insertion-sort facade models).

    @BmcProof
    fun sorted_natural_order() {
        val xs = sequenceOf(3, 1, 2, 1).sorted().toList()
        Bmc.check(xs.size == 4 && xs[0] == 1 && xs[1] == 1 && xs[2] == 2 && xs[3] == 3)
    }

    @BmcProof
    fun sortedDescending_reverses_order() {
        val xs = sequenceOf(1, 3, 2).sortedDescending().toList()
        Bmc.check(xs.size == 3 && xs[0] == 3 && xs[1] == 2 && xs[2] == 1)
    }

    @BmcProof
    fun sortedWith_comparator() {
        // reverse comparator => descending
        val xs = sequenceOf(1, 3, 2).sortedWith(compareByDescending { it }).toList()
        Bmc.check(xs.size == 3 && xs[0] == 3 && xs[1] == 2 && xs[2] == 1)
    }

    /** Symbolic sorted law: two elements come out in non-decreasing order, preserving the multiset. */
    @BmcProof
    fun symbolic_sorted_orders_pair() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val xs = sequenceOf(a, b).sorted().toList()
        val lo = if (a <= b) a else b
        val hi = if (a <= b) b else a
        Bmc.check(xs.size == 2 && xs[0] == lo && xs[1] == hi)
    }

    // ---- generateSequence(seed, next) (bounded eager facade model): terminating generators only.

    @BmcProof
    fun generateSequence_counts_up_until_null() {
        val xs = generateSequence(1) { if (it < 4) it + 1 else null }.toList()
        Bmc.check(xs.size == 4 && xs[0] == 1 && xs[1] == 2 && xs[2] == 3 && xs[3] == 4)
    }

    @BmcProof
    fun generateSequence_seedFunction_form() {
        val xs = generateSequence({ 2 }) { if (it < 6) it + 2 else null }.toList()
        Bmc.check(xs.size == 3 && xs[0] == 2 && xs[1] == 4 && xs[2] == 6)
    }
}
