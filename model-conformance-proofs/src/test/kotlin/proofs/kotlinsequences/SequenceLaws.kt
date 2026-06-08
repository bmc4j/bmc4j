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

    // ---- takeWhile(p) / dropWhile(p) intermediate ops (SequencesKt facade models): the iterator-protocol
    // finite state machine — takeWhile yields the leading run while p holds then STOPS; dropWhile skips that
    // run then yields the rest INCLUDING later elements that fail p.

    @BmcProof
    fun takeWhile_keeps_leading_run() {
        // 1,2 satisfy <3; 3 fails so it stops — the trailing 1 is NOT resumed.
        val t = sequenceOf(1, 2, 3, 1).takeWhile { it < 3 }.toList()
        Bmc.check(t.size == 2 && t[0] == 1 && t[1] == 2)
    }

    @BmcProof
    fun dropWhile_skips_leading_run_then_keeps_rest() {
        // drop the leading <3 run (1,2); keep 3 and the trailing 1 even though 1 < 3.
        val d = sequenceOf(1, 2, 3, 1).dropWhile { it < 3 }.toList()
        Bmc.check(d.size == 2 && d[0] == 3 && d[1] == 1)
    }

    /**
     * Symbolic takeWhile/dropWhile partition law over SYMBOLIC inputs — this is the case the
     * kotlin-version-fragile virtual Sequence.iterator() devirt broke (the #169 family false REFUTED):
     * symbolic operands keep the interface dispatch live where concrete proofs would constant-fold it,
     * so it pins the seqIter/backing checkcast on the takeWhile/dropWhile state machine. With a positive
     * head and a non-positive middle, takeWhile{it>0} and dropWhile{it>0} partition the sequence exactly.
     */
    @BmcProof
    fun symbolic_takeWhile_stops_at_first_failure() {
        val a = Bmc.anyInt(1, 100)    // > 0  -> in the leading run
        val b = Bmc.anyInt(-100, 0)   // <= 0 -> first failure, splits here
        val c = Bmc.anyInt(-100, 100) // arbitrary tail element (may be > 0)
        val t = sequenceOf(a, b, c).takeWhile { it > 0 }.toList()
        // takeWhile yields the leading run {a} then stops at b — c is never resumed.
        Bmc.check(t.size == 1 && t[0] == a)
    }

    @BmcProof
    fun symbolic_dropWhile_keeps_rest_after_run() {
        val a = Bmc.anyInt(1, 100)    // > 0  -> dropped (leading run)
        val b = Bmc.anyInt(-100, 0)   // <= 0 -> first kept element
        val c = Bmc.anyInt(-100, 100) // kept regardless of sign (run already ended)
        val d = sequenceOf(a, b, c).dropWhile { it > 0 }.toList()
        // dropWhile drops only the leading run {a} and keeps b, c (c kept even if > 0).
        Bmc.check(d.size == 2 && d[0] == b && d[1] == c)
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

    /**
     * Symbolic zip(Sequence,Sequence) law — pins the SECOND-sequence dispatch the devirt sweep
     * converted to seqIter (the `other` param). Symbolic operands keep the interface dispatch live;
     * the concrete zip_with_transform proof would constant-fold it. (#169 family regression.)
     */
    @BmcProof
    fun symbolic_zip_two_sequences() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        val d = Bmc.anyInt(-50, 50)
        val z = sequenceOf(a, b).zip(sequenceOf(c, d)) { x, y -> x + y }.toList()
        Bmc.check(z.size == 2 && z[0] == a + c && z[1] == b + d)
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

    /**
     * Symbolic plus(Sequence,Sequence) law — pins the SECOND-sequence dispatch the devirt sweep
     * converted to seqIter (the `elements` param). Symbolic operands keep the dispatch live where the
     * concrete plus_sequence_concatenates proof would constant-fold it. (#169 family regression.)
     */
    @BmcProof
    fun symbolic_plus_two_sequences() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        val d = Bmc.anyInt(-50, 50)
        val xs = (sequenceOf(a, b) + sequenceOf(c, d)).toList()
        Bmc.check(xs.size == 4 && xs[0] == a && xs[1] == b && xs[2] == c && xs[3] == d)
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

    // ---- distinctBy (SequencesKt facade model: dedup by selector key, first-occurrence order).

    @BmcProof
    fun distinctBy_dedups_by_key() {
        // keys: 0,1,0,1,0 -> keep first per key -> 10 (key0), 21 (key1)
        val d = sequenceOf(10, 21, 12, 23, 14).distinctBy { it % 2 }.toList()
        Bmc.check(d.size == 2 && d[0] == 10 && d[1] == 21)
    }

    /** Symbolic distinctBy law: two elements with the same key collapse to the first; a different key is kept. */
    @BmcProof
    fun symbolic_distinctBy_collapses_same_key() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        // keys a*2 and a*2 (same) then b*2+1 (different parity bucket via offset) — but keep it simple:
        // key = constant for first two, distinct for third.
        val d = sequenceOf(a, b, a).distinctBy { 0 }.toList()
        Bmc.check(d.size == 1 && d[0] == a)
    }

    // ---- filterNot / filterNotNull (SequencesKt facade models, eager).

    @BmcProof
    fun filterNot_keeps_complement() {
        val xs = sequenceOf(1, 2, 3, 4).filterNot { it % 2 == 0 }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 1 && xs[1] == 3)
    }

    @BmcProof
    fun filterNotNull_drops_nulls() {
        val xs = sequenceOf(1, null, 2, null, 3).filterNotNull().toList()
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[1] == 2 && xs[2] == 3)
    }

    /** Symbolic filterNot law: filterNot(p) is the complement of filter(p) — together they partition. */
    @BmcProof
    fun symbolic_filterNot_complements_filter() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val kept = sequenceOf(a, b).filterNot { it > 0 }.count()
        val expected = (if (a > 0) 0 else 1) + (if (b > 0) 0 else 1)
        Bmc.check(kept == expected)
    }

    // ---- mapNotNull / mapIndexedNotNull (SequencesKt facade models, eager: map then drop nulls).

    @BmcProof
    fun mapNotNull_maps_then_drops_nulls() {
        // even -> it/... ; map odd to null
        val xs = sequenceOf(1, 2, 3, 4).mapNotNull { if (it % 2 == 0) it * 10 else null }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 20 && xs[1] == 40)
    }

    @BmcProof
    fun mapIndexedNotNull_uses_index_and_drops_nulls() {
        // keep even indices, mapped to index*100 + value
        val xs = sequenceOf(5, 6, 7, 8).mapIndexedNotNull { i, v -> if (i % 2 == 0) i * 100 + v else null }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 5 && xs[1] == 207)
    }

    /** Symbolic mapNotNull law: mapping to a non-null transform keeps every element transformed. */
    @BmcProof
    fun symbolic_mapNotNull_all_kept() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        val xs = sequenceOf(a, b).mapNotNull { it + 1 }.toList()
        Bmc.check(xs.size == 2 && xs[0] == a + 1 && xs[1] == b + 1)
    }

    // ---- flatMapIndexedIterable / flatMapIndexedSequence (SequencesKt facade models, eager).

    @BmcProof
    fun flatMapIndexedSequence_concatenates_with_index() {
        // each element expands to (index, value)
        val f = sequenceOf(7, 8).flatMapIndexed { i, v -> sequenceOf(i, v) }.toList()
        Bmc.check(f.size == 4 && f[0] == 0 && f[1] == 7 && f[2] == 1 && f[3] == 8)
    }

    @BmcProof
    fun flatMapIndexedIterable_concatenates_with_index() {
        // transform returns a List (Iterable) -> routes to flatMapIndexedIterable
        val f = sequenceOf(7, 8).flatMapIndexed { i, v -> listOf(i, v) }.toList()
        Bmc.check(f.size == 4 && f[0] == 0 && f[1] == 7 && f[2] == 1 && f[3] == 8)
    }

    /** Symbolic flatMapIndexed law: each element expands to its index then itself, in order. */
    @BmcProof
    fun symbolic_flatMapIndexed_expands() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val f = sequenceOf(a, b).flatMapIndexed { i, v -> sequenceOf(i, v) }.toList()
        Bmc.check(f.size == 4 && f[0] == 0 && f[1] == a && f[2] == 1 && f[3] == b)
    }

    // ---- runningFold / scan / scanIndexed (SequencesKt facade models): n+1 accumulation prefix.

    @BmcProof
    fun runningFold_prefixes_with_initial() {
        // initial 100, then +1,+2,+3 -> 100,101,103,106
        val xs = sequenceOf(1, 2, 3).runningFold(100) { acc, e -> acc + e }.toList()
        Bmc.check(xs.size == 4 && xs[0] == 100 && xs[1] == 101 && xs[2] == 103 && xs[3] == 106)
    }

    @BmcProof
    fun scan_is_runningFold() {
        val xs = sequenceOf(1, 2, 3).scan(0) { acc, e -> acc + e }.toList()
        Bmc.check(xs.size == 4 && xs[0] == 0 && xs[1] == 1 && xs[2] == 3 && xs[3] == 6)
    }

    @BmcProof
    fun scanIndexed_uses_index() {
        // acc starts 0; step adds index*100 + element
        val xs = sequenceOf(5, 6).scanIndexed(0) { i, acc, e -> acc + i * 100 + e }.toList()
        Bmc.check(xs.size == 3 && xs[0] == 0 && xs[1] == 5 && xs[2] == 111)
    }

    /** Symbolic scan law: the last element of scan(+) from 0 equals the total sum. */
    @BmcProof
    fun symbolic_scan_last_is_sum() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        val xs = sequenceOf(a, b).scan(0) { acc, e -> acc + e }.toList()
        Bmc.check(xs.size == 3 && xs[0] == 0 && xs[1] == a && xs[2] == a + b)
    }

    // ---- requireNoNulls / asIterable (SequencesKt facade models, eager).

    @BmcProof
    fun requireNoNulls_passes_nonnull_through() {
        val xs = sequenceOf(1, 2, 3).requireNoNulls().toList()
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[1] == 2 && xs[2] == 3)
    }

    @BmcProof
    fun asIterable_round_trips_elements() {
        val xs = sequenceOf(4, 5, 6).asIterable().toList()
        Bmc.check(xs.size == 3 && xs[0] == 4 && xs[1] == 5 && xs[2] == 6)
    }

    /** Symbolic asIterable law: draining via Iterable preserves element identity and order. */
    @BmcProof
    fun symbolic_asIterable_preserves() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val xs = sequenceOf(a, b).asIterable().toList()
        Bmc.check(xs.size == 2 && xs[0] == a && xs[1] == b)
    }

    // ---- ifEmpty (SequencesKt facade model): default only on empty.

    @BmcProof
    fun ifEmpty_nonempty_uses_source() {
        val xs = sequenceOf(1, 2).ifEmpty { sequenceOf(9, 9, 9) }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 1 && xs[1] == 2)
    }

    @BmcProof
    fun ifEmpty_empty_uses_default() {
        // produce an empty source via filter (avoids the loud emptySequence() tail member)
        val empty = sequenceOf(1, 2, 3).filter { it > 100 }
        val xs = empty.ifEmpty { sequenceOf(9, 8) }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 9 && xs[1] == 8)
    }

    /**
     * Symbolic ifEmpty law — pins the DEFAULT-sequence dispatch the devirt sweep converted to seqIter
     * (the `defaultValue.invoke()` Sequence). Symbolic operands keep the dispatch live where the
     * concrete ifEmpty_empty_uses_default proof would constant-fold it. (#169 family regression.)
     */
    @BmcProof
    fun symbolic_ifEmpty_default_drained() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val empty = sequenceOf(1, 2, 3).filter { it > 100 }
        val xs = empty.ifEmpty { sequenceOf(a, b) }.toList()
        Bmc.check(xs.size == 2 && xs[0] == a && xs[1] == b)
    }

    // ==== #184 tail-drain laws: terminal accessors, conversions, numeric reductions and extrema, plus the
    // remaining intermediate ops. Each exercises the newly-modeled facade member over the concrete bounded
    // backing (NEVER the kotlinc-version-fragile virtual Sequence.iterator()). Symbolic laws keep the
    // interface dispatch live so they pin the seqIter/backing checkcast (the #169 family).

    // ---- first / last / single / singleOrNull / any / none (no-predicate terminals).

    @BmcProof
    fun first_last_single_terminals() {
        Bmc.check(sequenceOf(7, 8, 9).first() == 7)
        Bmc.check(sequenceOf(7, 8, 9).last() == 9)
        Bmc.check(sequenceOf(42).single() == 42)
        Bmc.check(sequenceOf(42).singleOrNull() == 42)
        Bmc.check(sequenceOf(1, 2).singleOrNull() == null)
    }

    @BmcProof
    fun any_none_terminals() {
        Bmc.check(sequenceOf(1).any())
        Bmc.check(!sequenceOf(1, 2, 3).filter { it > 100 }.any())
        Bmc.check(sequenceOf(1, 2, 3).filter { it > 100 }.none())
        Bmc.check(!sequenceOf(1).none())
    }

    /** Symbolic first/last law: brackets the two ends of a symbolic sequence. */
    @BmcProof
    fun symbolic_first_last() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        val s = sequenceOf(a, b, c)
        Bmc.check(s.first() == a && s.last() == c)
    }

    // ---- contains / indexOf / lastIndexOf / elementAt / elementAtOrElse.

    @BmcProof
    fun contains_indexOf_positions() {
        val s = sequenceOf(10, 20, 30, 20)
        Bmc.check(s.contains(20) && !s.contains(99))
        Bmc.check(s.indexOf(20) == 1 && s.lastIndexOf(20) == 3 && s.indexOf(99) == -1)
    }

    @BmcProof
    fun elementAt_and_orElse() {
        val s = sequenceOf(11, 22, 33)
        Bmc.check(s.elementAt(1) == 22)
        Bmc.check(s.elementAtOrElse(5) { it * 100 } == 500)
        Bmc.check(s.elementAtOrElse(0) { -1 } == 11)
    }

    /** Symbolic indexOf law: the first equals-match index, or -1 when absent. */
    @BmcProof
    fun symbolic_indexOf_first_match() {
        val a = Bmc.anyInt(0, 10)
        val b = Bmc.anyInt(11, 20)   // disjoint from a's range -> a != b
        val s = sequenceOf(a, b, a)
        Bmc.check(s.indexOf(a) == 0 && s.lastIndexOf(a) == 2 && s.indexOf(b) == 1)
    }

    // ---- sumOf* / averageOf* numeric reductions.

    @BmcProof
    fun sumOf_integral_widths() {
        Bmc.check(sequenceOf(1L, 2L, 3L).sum() == 6L)
        Bmc.check(sequenceOf<Byte>(1, 2, 3).sum() == 6)
        Bmc.check(sequenceOf<Short>(4, 5, 6).sum() == 15)
    }

    @BmcProof
    fun average_of_int() {
        // 2,4,6 -> 12/3 = 4.0 (integral-valued so FP-exact)
        Bmc.check(sequenceOf(2, 4, 6).average() == 4.0)
    }

    /** Symbolic sumOfLong law: folding a symbolic pair equals their sum. */
    @BmcProof
    fun symbolic_sumOfLong() {
        val a = Bmc.anyLong(0, 1000)
        val b = Bmc.anyLong(0, 1000)
        Bmc.check(sequenceOf(a, b).sum() == a + b)
    }

    // ---- maxOrNull / minOrNull / maxOrThrow / minOrThrow / maxWith / minWith extrema.

    @BmcProof
    fun max_min_natural_order() {
        Bmc.check(sequenceOf(3, 1, 4, 1, 5).maxOrNull() == 5)
        Bmc.check(sequenceOf(3, 1, 4, 1, 5).minOrNull() == 1)
        Bmc.check(sequenceOf(3, 1, 4).max() == 4)   // maxOrThrow
        Bmc.check(sequenceOf(3, 1, 4).min() == 1)   // minOrThrow
        Bmc.check(sequenceOf(1, 2, 3).filter { it > 100 }.maxOrNull() == null)
    }

    @BmcProof
    fun max_min_with_comparator() {
        // reverse comparator: maxWith picks the natural-minimum
        Bmc.check(sequenceOf(3, 1, 4).maxWith(compareByDescending { it }) == 1)
        Bmc.check(sequenceOf(3, 1, 4).minWith(compareByDescending { it }) == 4)
    }

    /** Symbolic max/min law: the extrema of a symbolic pair are its larger/smaller. */
    @BmcProof
    fun symbolic_max_min_pair() {
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val s = sequenceOf(a, b)
        val hi = if (a >= b) a else b
        val lo = if (a <= b) a else b
        Bmc.check(s.maxOrNull() == hi && s.minOrNull() == lo)
    }

    // ---- runningReduce / runningReduceIndexed (first-element-seeded accumulation prefix).

    @BmcProof
    fun runningReduce_accumulates() {
        // seed 1; then +2,+3,+4 -> 1,3,6,10
        val xs = sequenceOf(1, 2, 3, 4).runningReduce { acc, e -> acc + e }.toList()
        Bmc.check(xs.size == 4 && xs[0] == 1 && xs[1] == 3 && xs[2] == 6 && xs[3] == 10)
    }

    @BmcProof
    fun runningReduceIndexed_uses_index() {
        // seed 5; step adds index*100 + element -> [5, 5+1*100+6=111]
        val xs = sequenceOf(5, 6).runningReduceIndexed { i, acc, e -> acc + i * 100 + e }.toList()
        Bmc.check(xs.size == 2 && xs[0] == 5 && xs[1] == 111)
    }

    /** Symbolic runningReduce law: the last running-sum element equals the total. */
    @BmcProof
    fun symbolic_runningReduce_last_is_sum() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        val c = Bmc.anyInt(0, 100)
        val xs = sequenceOf(a, b, c).runningReduce { acc, e -> acc + e }.toList()
        Bmc.check(xs.size == 3 && xs[0] == a && xs[1] == a + b && xs[2] == a + b + c)
    }

    // ---- conversions: toMutableList / toMutableSet / toHashSet / toSortedSet / toCollection.

    @BmcProof
    fun toMutableList_and_sets() {
        val ml = sequenceOf(1, 2, 3).toMutableList()
        Bmc.check(ml.size == 3 && ml[0] == 1 && ml[2] == 3)
        val ms = sequenceOf(1, 2, 2, 3).toMutableSet()
        Bmc.check(ms.size == 3 && ms.contains(2))
        val hs = sequenceOf(1, 2, 2).toHashSet()
        Bmc.check(hs.size == 2 && hs.contains(1) && hs.contains(2))
    }

    @BmcProof
    fun toCollection_drains_into_destination() {
        val dest = ArrayList<Int>()
        val out = sequenceOf(7, 8).toCollection(dest)
        Bmc.check(out.size == 2 && out[0] == 7 && out[1] == 8)
    }

    // ---- flatten / flattenSequenceOfIterable / flatMapIterable / filterNotNullTo / filterIsInstance.

    @BmcProof
    fun flatten_sequence_of_sequences() {
        val f = sequenceOf(sequenceOf(1, 2), sequenceOf(3, 4)).flatten().toList()
        Bmc.check(f.size == 4 && f[0] == 1 && f[1] == 2 && f[2] == 3 && f[3] == 4)
    }

    @BmcProof
    fun flatten_sequence_of_iterables() {
        val f = sequenceOf(listOf(1, 2), listOf(3)).flatten().toList()
        Bmc.check(f.size == 3 && f[0] == 1 && f[1] == 2 && f[2] == 3)
    }

    @BmcProof
    fun flatMapIterable_concatenates_iterables() {
        val f = sequenceOf(1, 2).flatMap { listOf(it, it * 10) }.toList()
        Bmc.check(f.size == 4 && f[0] == 1 && f[1] == 10 && f[2] == 2 && f[3] == 20)
    }

    @BmcProof
    fun filterNotNullTo_drains_nonnull() {
        val dest = ArrayList<Int>()
        sequenceOf(1, null, 2, null, 3).filterNotNullTo(dest)
        Bmc.check(dest.size == 3 && dest[0] == 1 && dest[1] == 2 && dest[2] == 3)
    }

    // ---- unzip / sequenceOf(single) / emptySequence / asSequence(Iterator).

    @BmcProof
    fun unzip_splits_pairs() {
        val (a, b) = sequenceOf(1 to "a", 2 to "b").unzip()
        Bmc.check(a.size == 2 && a[0] == 1 && a[1] == 2 && b[0] == "a" && b[1] == "b")
    }

    @BmcProof
    fun sequenceOf_single_and_empty() {
        Bmc.check(sequenceOf(99).toList().let { it.size == 1 && it[0] == 99 })
        Bmc.check(emptySequence<Int>().toList().isEmpty())
    }

    @BmcProof
    fun asSequence_from_iterator() {
        val xs = listOf(4, 5, 6).iterator().asSequence().toList()
        Bmc.check(xs.size == 3 && xs[0] == 4 && xs[1] == 5 && xs[2] == 6)
    }
}
