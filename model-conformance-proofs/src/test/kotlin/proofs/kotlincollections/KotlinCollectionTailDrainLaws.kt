package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the NON-INLINE CollectionsKt / MapsKt / SetsKt facade members modeled in
 * the #184 tail-drain pass: the Sequence-arg plus/minus/putAll/toMap, the in-place mutators
 * addAll/removeAll/retainAll (collection + predicate forms), the destination-map toMap overloads, the
 * map/set factory + snapshot families (hashMapOf/linkedMapOf/sortedMapOf/toSingletonMap/asSequence),
 * the no-predicate any/none, the natural-order binarySearch, and withIndex(Iterator).
 *
 * Every law drives the member through a REAL Kotlin call site (so the kotlinc-emitted facade call
 * resolves against the bmc4j model) and pins the observable with a concrete + (where the symbolic
 * circuit stays in budget) a symbolic check, so a wrong or nondet-stubbed facade is caught — a nondet
 * stub could not satisfy the symbolic relations. The Sequence-arg laws drive the SAME devirt-robust
 * concrete-backing path the SequencesKt facade uses (seqIter over the ListSequence backing), never a
 * hand-rolled Iterable {}. Ranges are tight and collections ≤4 per the bounded-proof convention.
 */
class KotlinCollectionTailDrainLaws {

    // ---- CollectionsKt.plus / minus (Sequence) ----

    @BmcProof
    fun list_plus_sequence_concatenates() {
        val xs = listOf(1, 2) + sequenceOf(3, 4)
        Bmc.check(xs.size == 4 && xs[0] == 1 && xs[2] == 3 && xs[3] == 4)
    }

    // NOTE: the CollectionsKt minus(Iterable,Sequence) model is exercised by the set_minus_sequence /
    // map_minus_sequence_removes_keys laws below (same seqIter concrete-backing path, drained into a
    // bounded set then filtered). A list-minus-Sequence proof adds a second bounded collection (the
    // remove-set) on top of the source iteration — the SAT-pathological combination the protocol says to
    // skip; the family is already pinned green. (One pin per family — per the triage in KotlinTailBytecodeLaws.)
    //
    // NOTE: no symbolic list-plus-Sequence law — the concrete list_plus_sequence_concatenates above pins
    // the full observable (size + every position). A symbolic variant keeps BOTH the source List.iterator()
    // and the seqIter dispatch live over symbolic operands and blew the proof budget (the SAT-pathological
    // case the protocol says to skip, mirroring the symbolic-omission NOTEs in KotlinMapSetResidueLaws).

    // ---- CollectionsKt.addAll(collection, array) (mutating) ----

    @BmcProof
    fun addAll_array_appends() {
        val m = mutableListOf(1, 2)
        val changed = m.addAll(arrayOf(3, 4))
        Bmc.check(changed && m.size == 4 && m[2] == 3 && m[3] == 4)
    }

    @BmcProof
    fun addAll_empty_array_no_change() {
        val m = mutableListOf(1)
        val changed = m.addAll(arrayOf<Int>())
        Bmc.check(!changed && m.size == 1)
    }

    // ---- CollectionsKt.removeAll / retainAll (collection arg) (mutating) ----

    @BmcProof
    fun removeAll_iterable_drops_contained() {
        val m = mutableListOf(1, 2, 3, 4)
        val changed = m.removeAll(listOf(2, 4))
        Bmc.check(changed && m.size == 2 && m[0] == 1 && m[1] == 3)
    }

    @BmcProof
    fun retainAll_iterable_keeps_contained() {
        val m = mutableListOf(1, 2, 3, 4)
        val changed = m.retainAll(listOf(2, 4))
        Bmc.check(changed && m.size == 2 && m[0] == 2 && m[1] == 4)
    }

    @BmcProof
    fun retainAll_array_keeps_contained() {
        val m = mutableListOf(1, 2, 3)
        m.retainAll(arrayOf(1, 3))
        Bmc.check(m.size == 2 && m[0] == 1 && m[1] == 3)
    }

    // ---- CollectionsKt.removeAll / retainAll (predicate) (mutating, non-inline) ----

    @BmcProof
    fun removeAll_predicate_drops_matching() {
        val m = mutableListOf(1, 2, 3, 4)
        val changed = m.removeAll { it % 2 == 0 }
        Bmc.check(changed && m.size == 2 && m[0] == 1 && m[1] == 3)
    }

    @BmcProof
    fun retainAll_predicate_keeps_matching() {
        val m = mutableListOf(1, 2, 3, 4)
        val changed = m.retainAll { it > 2 }
        Bmc.check(changed && m.size == 2 && m[0] == 3 && m[1] == 4)
    }

    /** Symbolic predicate removeAll: removing the >0 elements leaves exactly the <=0 ones. */
    @BmcProof
    fun symbolic_removeAll_predicate() {
        val a = Bmc.anyInt(1, 100)    // > 0 -> removed
        val b = Bmc.anyInt(-100, 0)   // <= 0 -> kept
        val m = mutableListOf(a, b)
        m.removeAll { it > 0 }
        Bmc.check(m.size == 1 && m[0] == b)
    }

    // ---- CollectionsKt.binarySearch (natural-order) ----

    @BmcProof
    fun binarySearch_finds_present() {
        val xs = listOf(1, 3, 5, 7)
        Bmc.check(xs.binarySearch(5, 0, 4) == 2 && xs.binarySearch(1, 0, 4) == 0)
    }

    @BmcProof
    fun binarySearch_absent_returns_inverted_insertion_point() {
        val xs = listOf(1, 3, 5, 7)
        // 4 would insert at index 2 -> -(2)-1 == -3
        Bmc.check(xs.binarySearch(4, 0, 4) == -3)
    }

    /** Symbolic binarySearch: in a strictly increasing pair, the larger value is found at index 1. */
    @BmcProof
    fun symbolic_binarySearch_locates() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200) // strictly greater
        val xs = listOf(a, b)
        Bmc.check(xs.binarySearch(b, 0, 2) == 1 && xs.binarySearch(a, 0, 2) == 0)
    }

    // ---- CollectionsKt.withIndex(Iterator) ----

    @BmcProof
    fun withIndex_iterator_pairs_index_value() {
        val src = listOf(10, 20, 30).iterator()
        val acc = ArrayList<Int>()
        for (iv in src.withIndex()) acc.add(iv.index * 100 + iv.value)
        Bmc.check(acc.size == 3 && acc[0] == 10 && acc[1] == 120 && acc[2] == 230)
    }

    // ---- MapsKt.hashMapOf / linkedMapOf / sortedMapOf ----

    @BmcProof
    fun hashMapOf_holds_pairs() {
        val m = hashMapOf(1 to 10, 2 to 20)
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    @BmcProof
    fun linkedMapOf_holds_pairs() {
        val m = linkedMapOf(1 to 10, 2 to 20)
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    // NOTE: no sortedMapOf law. sortedMapOf(Pair[]) is MODELED (real body building the bounded
    // natural-ordering TreeMap), but its observable is NOT bounded-provable: a TreeMap read (even get()
    // /size after a build, not just sorted navigation) is a documented JBMC TreeMap contract-level
    // divergence (a refuted-on-a-correct-model artifact). This mirrors toSortedMap(Map), which is likewise
    // modeled WITHOUT a proof law for the same reason (see the toSortedMap NOTE in MapsKt.java + the
    // KotlinMapSetResidueLaws toSortedMap NOTE). The model is exercised through the build path; the read
    // observable is out of bounded-proof scope.

    // ---- MapsKt.putAll (array / iterable / sequence) (mutating) ----

    @BmcProof
    fun putAll_array_inserts() {
        val m = mutableMapOf(1 to 10)
        m.putAll(arrayOf(2 to 20, 3 to 30))
        Bmc.check(m.size == 3 && m[2] == 20 && m[3] == 30)
    }

    @BmcProof
    fun putAll_iterable_inserts() {
        val m = mutableMapOf(1 to 10)
        m.putAll(listOf(2 to 20))
        Bmc.check(m.size == 2 && m[2] == 20)
    }

    // NOTE: putAll(Map,Sequence) shares the seqIter concrete-backing drain proven by the map/set
    // plus/minus-Sequence + toMap_sequence_builds_map laws; putAll_array/putAll_iterable pin the putAll
    // mutation itself. A putAll-Sequence proof (Pair-typed sequence into a mutated map) is the heavier
    // SAT-pathological combination the protocol says to skip — the family is already green.

    // ---- MapsKt.toMap (destination overloads + Sequence) ----

    @BmcProof
    fun toMap_pairs_into_destination() {
        val dest = HashMap<Int, Int>()
        val r = listOf(1 to 10, 2 to 20).toMap(dest)
        Bmc.check(r.size == 2 && r[1] == 10 && r[2] == 20 && dest.size == 2)
    }

    @BmcProof
    fun toMap_array_into_destination() {
        val dest = HashMap<Int, Int>()
        arrayOf(1 to 10).toMap(dest)
        Bmc.check(dest.size == 1 && dest[1] == 10)
    }

    @BmcProof
    fun toMap_map_into_destination() {
        val dest = HashMap<Int, Int>()
        mapOf(1 to 10, 2 to 20).toMap(dest)
        Bmc.check(dest.size == 2 && dest[1] == 10 && dest[2] == 20)
    }

    @BmcProof
    fun toMap_sequence_builds_map() {
        val m = sequenceOf(1 to 10, 2 to 20).toMap()
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    // NOTE: toMap(Sequence,destination) combines the seqIter drain (proven by toMap_sequence_builds_map)
    // with the destination-map fill (proven by toMap_pairs/array/map_into_destination) — the cross-product
    // is the heavier SAT-pathological combination the protocol says to skip; both halves are already green.

    // ---- MapsKt.any / none (no predicate) ----

    @BmcProof
    fun map_any_none_track_emptiness() {
        Bmc.check(mapOf(1 to 1).any() && !emptyMap<Int, Int>().any() &&
            emptyMap<Int, Int>().none() && !mapOf(1 to 1).none())
    }

    // ---- MapsKt.asSequence(map) ----

    @BmcProof
    fun map_asSequence_iterates_entries() {
        val sum = mapOf(1 to 10, 2 to 20).asSequence().map { it.value }.sum()
        Bmc.check(sum == 30)
    }

    // ---- MapsKt.plus / minus (Sequence) ----

    @BmcProof
    fun map_plus_sequence_adds_entries() {
        val m = mapOf(1 to 10) + sequenceOf(2 to 20, 3 to 30)
        Bmc.check(m.size == 3 && m[2] == 20 && m[3] == 30)
    }

    @BmcProof
    fun map_minus_sequence_removes_keys() {
        val m = mapOf(1 to 10, 2 to 20, 3 to 30) - sequenceOf(1, 3)
        Bmc.check(m.size == 1 && m[2] == 20)
    }

    // ---- SetsKt.plus / minus (Sequence) ----

    @BmcProof
    fun set_plus_sequence_unions() {
        val s = setOf(1, 2) + sequenceOf(2, 3, 4)
        Bmc.check(s.size == 4 && s.contains(4) && !s.contains(9))
    }

    @BmcProof
    fun set_minus_sequence_removes() {
        val s = setOf(1, 2, 3, 4) - sequenceOf(2, 4)
        Bmc.check(s.size == 2 && s.contains(1) && s.contains(3))
    }

    // NOTE: no symbolic set-plus-Sequence law — the concrete set_plus_sequence_unions / set_minus_sequence
    // above pin the SetsKt Sequence-arg ops, and symbolic_setOfNotNull / symbolic_set_plus in
    // KotlinMapSetResidueLaws already cover set membership over symbolic operands. Symbolic set-union with a
    // seqIter-drained operand (set add over symbolic equals/hash) is the SAT-pathological case the protocol
    // says to skip — the family is already green.
}
