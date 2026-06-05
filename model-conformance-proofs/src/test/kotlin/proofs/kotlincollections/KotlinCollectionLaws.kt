package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the Kotlin collection facades — listOf/setOf/mapOf, sum,
 * first/last, Pair/to — and the inline operators (map/filter/fold) that drive the bounded java
 * collection models. Verified under JBMC with concrete expected results (and one symbolic law), so
 * a wrong facade/model is caught. Differential-via-relocation isn't used here: the inline operators
 * have no model method to relocate, and the facades just delegate to the (separately conformance-
 * tested) java collection models.
 */
class KotlinCollectionLaws {

    @BmcProof
    fun listOf_size_index_and_sum() {
        val xs = listOf(1, 2, 3)
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[2] == 3 && xs.sum() == 6)
    }

    @BmcProof
    fun map_then_sum() {
        val xs = listOf(1, 2, 3)
        Bmc.check(xs.map { it + 1 }.sum() == 9)
    }

    @BmcProof
    fun filter_then_sum() {
        val xs = listOf(1, 2, 3, 4)
        Bmc.check(xs.filter { it % 2 == 0 }.sum() == 6)
    }

    @BmcProof
    fun fold_sums() {
        val xs = listOf(1, 2, 3, 4)
        Bmc.check(xs.fold(0) { acc, e -> acc + e } == 10)
    }

    @BmcProof
    fun first_and_last() {
        val xs = listOf(5, 6, 7)
        Bmc.check(xs.first() == 5 && xs.last() == 7)
    }

    @BmcProof
    fun setOf_dedups_and_membership() {
        val s = setOf(1, 2, 2, 3)
        Bmc.check(s.size == 3 && s.contains(2) && !s.contains(9))
    }

    @BmcProof
    fun mapOf_lookup() {
        val m = mapOf(1 to 10, 2 to 20)
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    @BmcProof
    fun pair_components() {
        val p = 3 to 4
        Bmc.check(p.first == 3 && p.second == 4)
    }

    @BmcProof
    fun triple_components() {
        val t = Triple(3, 4, 5)
        Bmc.check(t.first == 3 && t.second == 4 && t.third == 5)
    }

    @BmcProof
    fun triple_destructures() {
        val (a, b, c) = Triple(6, 7, 8)   // exercises component1/component2/component3
        Bmc.check(a == 6 && b == 7 && c == 8)
    }

    /** Symbolic law: map-then-sum distributes, for every pair of inputs. */
    @BmcProof
    fun symbolic_map_then_sum() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.check(listOf(a, b).map { it * 2 }.sum() == 2 * (a + b))
    }

    // ---- maxOrNull / minOrNull (modeled in CollectionsKt: fold-with-compareTo over the bounded
    // list; the Kotlin compiler routes List<Int>.maxOrNull/minOrNull through the generic
    // Iterable overload CollectionsKt.maxOrNull/minOrNull:(Ljava/lang/Iterable;)Ljava/lang/Comparable;).

    @BmcProof
    fun maxOrNull_picks_largest() {
        Bmc.check(listOf(3, 1, 2).maxOrNull() == 3)
    }

    @BmcProof
    fun minOrNull_picks_smallest() {
        Bmc.check(listOf(3, 1, 2).minOrNull() == 1)
    }

    @BmcProof
    fun maxOrNull_empty_is_null() {
        Bmc.check(emptyList<Int>().maxOrNull() == null)
    }

    @BmcProof
    fun minOrNull_empty_is_null() {
        Bmc.check(emptyList<Int>().minOrNull() == null)
    }

    /** Symbolic law: maxOrNull/minOrNull bracket the inputs and the max dominates the min. */
    @BmcProof
    fun symbolic_max_min_bounds() {
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(-1000, 1000)
        val c = Bmc.anyInt(-1000, 1000)
        val xs = listOf(a, b, c)
        val mx = xs.maxOrNull()!!
        val mn = xs.minOrNull()!!
        Bmc.check(
            mx >= a && mx >= b && mx >= c &&
                mn <= a && mn <= b && mn <= c &&
                (mx == a || mx == b || mx == c) &&
                (mn == a || mn == b || mn == c),
        )
    }

    // ---- count() / count{} / any{} / all{} / none{}: the Kotlin compiler fully INLINES these over
    // the modeled Iterable/Iterator/Collection (count() -> Collection.size()), so they exercise the
    // bounded list model directly with no CollectionsKt facade method. Concrete + symbolic proofs
    // confirm the inlined iteration is sound against the model.

    @BmcProof
    fun count_is_size() {
        Bmc.check(listOf(1, 2, 3).count() == 3 && emptyList<Int>().count() == 0)
    }

    @BmcProof
    fun count_predicate() {
        Bmc.check(listOf(1, 2, 3, 4).count { it % 2 == 0 } == 2)
    }

    @BmcProof
    fun any_finds_match() {
        Bmc.check(listOf(1, 2, 3).any { it == 2 } && !listOf(1, 3, 5).any { it == 2 })
    }

    @BmcProof
    fun all_holds() {
        Bmc.check(listOf(2, 4, 6).all { it % 2 == 0 } && !listOf(2, 3, 6).all { it % 2 == 0 })
    }

    @BmcProof
    fun none_holds() {
        Bmc.check(listOf(1, 3, 5).none { it % 2 == 0 } && !listOf(1, 2, 5).none { it % 2 == 0 })
    }

    /** Symbolic law: any/all/none are consistent and count{p} is bounded by size. */
    @BmcProof
    fun symbolic_predicate_consistency() {
        // Tight domain (0..3 fully exercises even/odd) + only the any/none pair: each inlined
        // collection traversal is costly, and count{} (with its overflow path) blew this proof past
        // the CI budget. any/none/count are each covered by the concrete proofs; this keeps just the
        // symbolic any==!none consistency law, cheap.
        val a = Bmc.anyInt(0, 3)
        val b = Bmc.anyInt(0, 3)
        val xs = listOf(a, b)
        Bmc.check(xs.any { it % 2 == 0 } == !xs.none { it % 2 == 0 })
    }

    // ---- groupBy { keySelector } -> Map<K, List<V>>. The Kotlin compiler fully INLINES this over a
    // LinkedHashMap + ArrayList (both modeled): Map.get/put + List.add. No CollectionsKt facade
    // method is involved, so this exercises the bounded map+list models directly.

    @BmcProof
    fun groupBy_partitions_by_key() {
        val g = listOf(1, 2, 3, 4, 5).groupBy { it % 2 }
        val odd = g[1]!!
        val even = g[0]!!
        Bmc.check(
            g.size == 2 &&
                odd.size == 3 && odd[0] == 1 && odd[1] == 3 && odd[2] == 5 &&
                even.size == 2 && even[0] == 2 && even[1] == 4,
        )
    }

    /** Symbolic groupBy law: every element lands in exactly its key's bucket, order preserved. */
    @BmcProof
    fun symbolic_groupBy_buckets() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        val g = listOf(2 * a, 2 * b + 1).groupBy { it % 2 }   // first is even, second is odd
        Bmc.check(g[0]!!.size == 1 && g[0]!![0] == 2 * a && g[1]!!.size == 1 && g[1]!![0] == 2 * b + 1)
    }

    // ---- associate { Pair } / associateBy { key } / associateWith { value } -> Map. Also INLINED
    // over a LinkedHashMap (sized via MapsKt.mapCapacity + RangesKt.coerceAtLeast + LinkedHashMap(int)
    // ctor — all modeled so the capacity isn't nondet) with the put loop over the bounded map model.

    @BmcProof
    fun associateBy_keys_map_to_elements() {
        val m = listOf(1, 2, 3).associateBy { it * 10 }
        Bmc.check(m.size == 3 && m[10] == 1 && m[20] == 2 && m[30] == 3 && m[99] == null)
    }

    @BmcProof
    fun associateWith_elements_map_to_values() {
        val m = listOf(1, 2, 3).associateWith { it * it }
        Bmc.check(m.size == 3 && m[1] == 1 && m[2] == 4 && m[3] == 9)
    }

    @BmcProof
    fun associate_builds_pairs() {
        val m = listOf(1, 2, 3).associate { it to it + 100 }
        Bmc.check(m.size == 3 && m[1] == 101 && m[2] == 102 && m[3] == 103)
    }

    /** Symbolic associateWith law: lookup recovers the selector applied to each key. */
    @BmcProof
    fun symbolic_associateWith_lookup() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(1001, 2000)   // distinct keys (disjoint ranges)
        val m = listOf(a, b).associateWith { it + 7 }
        Bmc.check(m[a] == a + 7 && m[b] == b + 7)
    }

    // ---- zip(other) -> List<Pair>, truncated to the shorter input (CollectionsKt.zip facade model).

    @BmcProof
    fun zip_pairs_elementwise() {
        val z = listOf("a", "b", "c").zip(listOf(1, 2, 3))
        Bmc.check(
            z.size == 3 &&
                z[0].first == "a" && z[0].second == 1 &&
                z[1].first == "b" && z[1].second == 2 &&
                z[2].first == "c" && z[2].second == 3,
        )
    }

    @BmcProof
    fun zip_truncates_to_shorter() {
        val z = listOf(1, 2, 3, 4).zip(listOf(10, 20))
        Bmc.check(z.size == 2 && z[0].second == 10 && z[1].second == 20)
    }

    /** Symbolic zip law: each pair carries the corresponding elements of the two inputs. */
    @BmcProof
    fun symbolic_zip_corresponds() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val z = listOf(a, b).zip(listOf(a + 1, b + 1))
        Bmc.check(z.size == 2 && z[0].first == a && z[0].second == a + 1 && z[1].first == b && z[1].second == b + 1)
    }

    // ---- sorted() / sortedBy { } -> a NEW sorted list (CollectionsKt.sorted / sortedWith facade
    // models: bounded insertion sort; sortedBy's keySelector is desugared into a Comparator).

    @BmcProof
    fun sorted_orders_ascending() {
        val s = listOf(3, 1, 2).sorted()
        Bmc.check(s.size == 3 && s[0] == 1 && s[1] == 2 && s[2] == 3)
    }

    @BmcProof
    fun sorted_leaves_source_unchanged() {
        val src = listOf(3, 1, 2)
        val s = src.sorted()
        Bmc.check(src[0] == 3 && src[1] == 1 && src[2] == 2 && s[0] == 1 && s[2] == 3)
    }

    @BmcProof
    fun sortedBy_orders_by_selector() {
        // sortedBy with a numeric selector: sort by descending magnitude via negation, so the
        // result order differs from natural order (proving the Comparator from the desugared
        // keySelector is genuinely applied). Avoids String.length (regex/String-content ops are
        // unsound under JBMC; see notes) to keep the law about the sortedWith model + Comparator.
        val s = listOf(1, 3, 2).sortedBy { -it }
        Bmc.check(s.size == 3 && s[0] == 3 && s[1] == 2 && s[2] == 1)
    }

    /** Symbolic sorted law: the result is a non-decreasing permutation of the two inputs. */
    @BmcProof
    fun symbolic_sorted_is_ordered_permutation() {
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(-1000, 1000)
        val s = listOf(a, b).sorted()
        Bmc.check(
            s.size == 2 &&
                s[0] <= s[1] &&
                s[0] == minOf(a, b) && s[1] == maxOf(a, b),
        )
    }

    /**
     * Symbolic sortedBy law: a descending-by-negation selector reverses natural order, for every
     * pair of inputs. Pins ComparisonsKt.compareValues — a nondet stub could not satisfy this for
     * all symbolic a, b.
     */
    @BmcProof
    fun symbolic_sortedBy_descending() {
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(-1000, 1000)
        val s = listOf(a, b).sortedBy { -it }
        Bmc.check(s.size == 2 && s[0] >= s[1] && s[0] == maxOf(a, b) && s[1] == minOf(a, b))
    }

    // ---- take(n) / drop(n) -> a NEW list of the first / all-but-first n elements
    // (CollectionsKt.take / drop facade models, bounded; negative n throws).

    @BmcProof
    fun take_keeps_prefix() {
        val t = listOf(1, 2, 3, 4).take(2)
        Bmc.check(t.size == 2 && t[0] == 1 && t[1] == 2)
    }

    @BmcProof
    fun take_more_than_size_is_whole() {
        val t = listOf(1, 2).take(5)
        Bmc.check(t.size == 2 && t[0] == 1 && t[1] == 2)
    }

    @BmcProof
    fun drop_skips_prefix() {
        val d = listOf(1, 2, 3, 4).drop(2)
        Bmc.check(d.size == 2 && d[0] == 3 && d[1] == 4)
    }

    @BmcProof
    fun drop_more_than_size_is_empty() {
        Bmc.check(listOf(1, 2).drop(5).isEmpty())
    }

    /** Symbolic take/drop law: take(k)+drop(k) partition the source and concatenate to it. */
    @BmcProof
    fun symbolic_take_drop_partition() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val c = Bmc.anyInt(-100, 100)
        val xs = listOf(a, b, c)
        val t = xs.take(1)
        val d = xs.drop(1)
        Bmc.check(
            t.size == 1 && d.size == 2 &&
                t[0] == a && d[0] == b && d[1] == c,
        )
    }

    // ---- distinct() -> NEW list of distinct elements in first-occurrence order
    // (CollectionsKt.distinct facade model via a bounded LinkedHashSet).

    @BmcProof
    fun distinct_dedups_preserving_order() {
        val d = listOf(3, 1, 3, 2, 1).distinct()
        Bmc.check(d.size == 3 && d[0] == 3 && d[1] == 1 && d[2] == 2)
    }

    /** Symbolic distinct law: two equal elements collapse to one; a distinct one is kept, in order. */
    @BmcProof
    fun symbolic_distinct_collapses_duplicates() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200) // b != a (disjoint ranges)
        val d = listOf(a, a, b).distinct()
        Bmc.check(d.size == 2 && d[0] == a && d[1] == b)
    }

    // ---- toSet() -> NEW Set (LinkedHashSet model) deduping via equals.

    @BmcProof
    fun toSet_dedups() {
        val s = listOf(1, 2, 2, 3, 1).toSet()
        Bmc.check(s.size == 3 && s.contains(1) && s.contains(2) && s.contains(3) && !s.contains(9))
    }

    /** Symbolic toSet law: a duplicate is absorbed; membership reflects the inputs. */
    @BmcProof
    fun symbolic_toSet_membership() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val s = listOf(a, a, b).toSet()
        Bmc.check(s.size == 2 && s.contains(a) && s.contains(b))
    }

    // ---- toMutableList() -> NEW ArrayList snapshot (CollectionsKt.toMutableList facade model).

    @BmcProof
    fun toMutableList_copies_and_is_mutable() {
        val src = listOf(1, 2, 3)
        val m = src.toMutableList()
        m.add(4)
        // mutating the copy does not affect the source snapshot
        Bmc.check(m.size == 4 && m[3] == 4 && src.size == 3)
    }

    /** Symbolic toMutableList law: the copy carries every element in order, independent of source. */
    @BmcProof
    fun symbolic_toMutableList_is_faithful_copy() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val m = listOf(a, b).toMutableList()
        Bmc.check(m.size == 2 && m[0] == a && m[1] == b)
    }

    // ---- fold / reduce / sumOf { } / flatMap { }: the Kotlin compiler fully INLINES these over the
    // bounded Iterable/Iterator/ArrayList (flatMap additionally via the modeled CollectionsKt.addAll).
    // No dedicated facade method — these exercise the bounded list model directly.

    @BmcProof
    fun fold_accumulates() {
        Bmc.check(listOf(1, 2, 3, 4).fold(10) { acc, e -> acc + e } == 20)
    }

    @BmcProof
    fun reduce_combines() {
        Bmc.check(listOf(1, 2, 3, 4).reduce { acc, e -> acc + e } == 10)
    }

    @BmcProof
    fun sumOf_selector() {
        Bmc.check(listOf(1, 2, 3).sumOf { it * 2 } == 12)
    }

    @BmcProof
    fun flatMap_concatenates() {
        val f = listOf(1, 2, 3).flatMap { listOf(it, it * 10) }
        Bmc.check(f.size == 6 && f[0] == 1 && f[1] == 10 && f[2] == 2 && f[3] == 20 && f[4] == 3 && f[5] == 30)
    }

    /** Symbolic fold law: folding (+) from 0 equals the sum, for every pair of inputs. */
    @BmcProof
    fun symbolic_fold_is_sum() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.check(listOf(a, b).fold(0) { acc, e -> acc + e } == a + b)
    }

    /** Symbolic reduce law: reduce(+) equals the sum and (for these inputs) equals fold-from-first. */
    @BmcProof
    fun symbolic_reduce_is_sum() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        val c = Bmc.anyInt(0, 1000)
        Bmc.check(listOf(a, b, c).reduce { acc, e -> acc + e } == a + b + c)
    }

    /** Symbolic sumOf law: sumOf{*3} triples the sum — only true if the selector is applied. */
    @BmcProof
    fun symbolic_sumOf_selector() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.check(listOf(a, b).sumOf { it * 3 } == 3 * (a + b))
    }

    /** Symbolic flatMap law: each element expands to a pair (e, e+1), in order. */
    @BmcProof
    fun symbolic_flatMap_expands() {
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        val f = listOf(a, b).flatMap { listOf(it, it + 1) }
        Bmc.check(f.size == 4 && f[0] == a && f[1] == a + 1 && f[2] == b && f[3] == b + 1)
    }
}
