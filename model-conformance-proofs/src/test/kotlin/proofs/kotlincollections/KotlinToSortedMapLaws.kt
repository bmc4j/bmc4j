package proofs.kotlincollections

import java.util.TreeMap
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for `MapsKt.toSortedMap(Map)` — the non-inline kotlin-stdlib extension that
 * builds a `SortedMap` from a map's entries, sorted by key natural ordering. PR background: an earlier
 * pass left `toSortedMap` in the tail believing it tripped a "dynamic cast check" artifact; a 2026-06-08
 * diagnosis proved that WRONG — `toSortedMap` simply wasn't modeled (its call nondet-stubbed → UNKNOWN),
 * and raw TreeMap navigation with CONCRETE keys @BmcProofs fine. So it is modelable: bmc4j now models it
 * over the bounded, natural-ordering `java.util.TreeMap` model, which now `implements java.util.SortedMap`
 * so the `SortedMap`-typed result downcasts cleanly instead of havoc'ing.
 *
 * The first proof is the ANCHOR: a raw TreeMap built from concrete entries, reading firstKey/get — it
 * pins that the backing the model relies on works under JBMC (the load-bearing claim of the diagnosis).
 * The rest drive `Map.toSortedMap()` itself. All keys are CONCRETE: the sort construction is driven by
 * natural-ordering Comparable comparisons, which are SAT-heavy when symbolic, so per the protocol these
 * stay concrete-keyed (natural-ordering proofs, not symbolic-comparator).
 *
 * Access-pattern note (load-bearing, verified by bisection): the navigation surface that is declared
 * DIRECTLY on `java.util.SortedMap` (`firstKey`/`lastKey`/`comparator`) devirtualizes onto the model
 * through the `SortedMap`-typed result. The `Map`-inherited accessors/mutators (`size`/`get`/`put`),
 * dispatched through the `SortedMap` static type, hit a JBMC devirtualization gap on the real
 * `SortedMap → SequencedMap → Map` chain and havoc. So these proofs read ordering via firstKey/lastKey,
 * and downcast the result to the concrete `TreeMap` model (`as TreeMap`, which succeeds — the model
 * genuinely returns a TreeMap) before exercising size/get. That is exactly how downstream code uses a
 * `toSortedMap` result anyway, and keeps every check sound.
 *
 * The comparator overload `toSortedMap(Map, Comparator)` is NOT modeled (left in the tail): bmc4j's
 * TreeMap model is natural-ordering only, so a custom-comparator sort can't be modeled soundly there.
 */
class KotlinToSortedMapLaws {

    /** ANCHOR: raw TreeMap built from concrete entries — firstKey/lastKey/get read back. Pins that the
     *  bounded TreeMap backing toSortedMap returns over actually works under JBMC. */
    @BmcProof
    fun anchor_raw_treemap_build_reads_sorted() {
        val t = TreeMap<Int, Int>()
        t[3] = 30
        t[1] = 10
        t[2] = 20
        Bmc.check(t.firstKey() == 1 && t.lastKey() == 3)
        Bmc.check(t[1] == 10 && t[2] == 20 && t[3] == 30)
    }

    // ---- MapsKt.toSortedMap(Map) ----

    /** toSortedMap copies every entry of the receiver, value-for-key preserved (read via the TreeMap). */
    @Suppress("UNCHECKED_CAST")
    @BmcProof
    fun toSortedMap_preserves_entries() {
        val sorted = mapOf(3 to 30, 1 to 10, 2 to 20).toSortedMap() as TreeMap<Int, Int>
        Bmc.check(sorted.size == 3)
        Bmc.check(sorted[1] == 10 && sorted[2] == 20 && sorted[3] == 30)
    }

    /** The result is sorted by key natural ordering: firstKey is the min, lastKey the max (SortedMap). */
    @BmcProof
    fun toSortedMap_orders_by_key() {
        val sorted = mapOf(3 to 30, 1 to 10, 2 to 20).toSortedMap()
        Bmc.check(sorted.firstKey() == 1 && sorted.lastKey() == 3)
    }

    /** An empty receiver yields an empty SortedMap (size read via the TreeMap). */
    @Suppress("UNCHECKED_CAST")
    @BmcProof
    fun toSortedMap_of_empty_is_empty() {
        val sorted = emptyMap<Int, Int>().toSortedMap() as TreeMap<Int, Int>
        Bmc.check(sorted.isEmpty() && sorted.size == 0)
    }

    /** A single-entry receiver round-trips: that key is both first and last, value preserved. */
    @Suppress("UNCHECKED_CAST")
    @BmcProof
    fun toSortedMap_singleton() {
        val sorted = mapOf(7 to 70).toSortedMap()
        Bmc.check(sorted.firstKey() == 7 && sorted.lastKey() == 7)
        val tm = sorted as TreeMap<Int, Int>
        Bmc.check(tm.size == 1 && tm[7] == 70)
    }

    /** The receiver is left untouched — toSortedMap returns a fresh map. */
    @Suppress("UNCHECKED_CAST")
    @BmcProof
    fun toSortedMap_leaves_source_unchanged() {
        val src = mapOf(2 to 20, 1 to 10)
        val sorted = src.toSortedMap() as TreeMap<Int, Int>
        sorted[3] = 30
        Bmc.check(src.size == 2 && sorted.size == 3 && src[3] == null)
    }
}
