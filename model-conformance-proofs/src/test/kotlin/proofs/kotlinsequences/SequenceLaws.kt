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
}
