package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the MapsKt and SetsKt facade residue that PR #129 probed and REFUTED
 * through the real kotlin-stdlib bytecode: MapsKt plus/minus/getValue/toList and SetsKt plus/minus.
 * Each now has a real bmc4j model building the bounded java HashMap/HashSet models directly; these
 * proofs pin the observable with concrete + symbolic checks so a wrong (or nondet-stubbed) facade is
 * caught. The map copies iterate entrySet explicitly (the HashMap model's putAll is a loud stub).
 */
class KotlinMapSetResidueLaws {

    // ---- MapsKt.getValue ----

    @BmcProof
    fun getValue_returns_present() {
        val m = mapOf(1 to 10, 2 to 20)
        Bmc.check(m.getValue(1) == 10 && m.getValue(2) == 20)
    }

    /** Symbolic getValue law: getValue recovers the value stored under each distinct key. */
    @BmcProof
    fun symbolic_getValue() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val m = mapOf(a to a + 1, b to b + 2)
        Bmc.check(m.getValue(a) == a + 1 && m.getValue(b) == b + 2)
    }

    // ---- MapsKt.plus (pair / map) ----

    @BmcProof
    fun plus_pair_adds_entry() {
        val m = mapOf(1 to 10) + (2 to 20)
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    @BmcProof
    fun plus_pair_overwrites_on_collision() {
        val m = mapOf(1 to 10) + (1 to 99)
        Bmc.check(m.size == 1 && m[1] == 99)
    }

    @BmcProof
    fun plus_map_merges() {
        val m = mapOf(1 to 10) + mapOf(2 to 20, 3 to 30)
        Bmc.check(m.size == 3 && m[1] == 10 && m[2] == 20 && m[3] == 30)
    }

    @BmcProof
    fun plus_leaves_source_unchanged() {
        val src = mapOf(1 to 10)
        val m = src + (2 to 20)
        Bmc.check(src.size == 1 && m.size == 2)
    }

    /** Symbolic plus law: adding (b,vb) to {a:va} (distinct keys) yields both, source untouched. */
    @BmcProof
    fun symbolic_plus_pair() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val m = mapOf(a to a + 1) + (b to b + 1)
        Bmc.check(m.size == 2 && m[a] == a + 1 && m[b] == b + 1)
    }

    // ---- MapsKt.minus (key) ----

    @BmcProof
    fun minus_key_removes_entry() {
        val m = mapOf(1 to 10, 2 to 20) - 1
        Bmc.check(m.size == 1 && m[2] == 20 && m[1] == null)
    }

    @BmcProof
    fun minus_keys_removes_all() {
        val m = mapOf(1 to 10, 2 to 20, 3 to 30) - listOf(1, 3)
        Bmc.check(m.size == 1 && m[2] == 20)
    }

    // NOTE: a symbolic minus-key law (mapOf(a to .., b to ..) - b) is intentionally omitted — the
    // symbolic two-key HashMap build + remove blew past the proof budget (heavy symbolic hashing
    // circuit). The concrete minus_key_removes_entry / minus_keys_removes_all + symbolic_plus_pair
    // (symbolic two-key build) cover the family; chasing the symbolic remove is the SAT-pathological
    // case the protocol says to skip rather than fight.

    // ---- MapsKt.toList ----

    @BmcProof
    fun toList_yields_pairs() {
        val pairs = mapOf(1 to 10).toList()
        Bmc.check(pairs.size == 1 && pairs[0].first == 1 && pairs[0].second == 10)
    }

    // ---- SetsKt.plus / minus ----

    @BmcProof
    fun set_plus_element() {
        val s = setOf(1, 2) + 3
        Bmc.check(s.size == 3 && s.contains(3) && s.contains(1))
    }

    @BmcProof
    fun set_plus_dedups() {
        val s = setOf(1, 2) + 2
        Bmc.check(s.size == 2 && s.contains(1) && s.contains(2))
    }

    @BmcProof
    fun set_plus_collection() {
        val s = setOf(1, 2) + listOf(2, 3, 4)
        Bmc.check(s.size == 4 && s.contains(4) && !s.contains(9))
    }

    @BmcProof
    fun set_minus_element() {
        val s = setOf(1, 2, 3) - 2
        Bmc.check(s.size == 2 && s.contains(1) && s.contains(3) && !s.contains(2))
    }

    @BmcProof
    fun set_minus_collection() {
        val s = setOf(1, 2, 3, 4) - listOf(2, 4)
        Bmc.check(s.size == 2 && s.contains(1) && s.contains(3))
    }

    /**
     * Symbolic set plus law: for distinct a,b — `{a} + b` contains both, size 2. (A single op keeps the
     * symbolic circuit small; the plus-then-minus round-trip blew the proof budget — the SAT-
     * pathological case the protocol says to skip. The concrete set_minus_* proofs cover removal.)
     */
    @BmcProof
    fun symbolic_set_plus() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val added = setOf(a) + b
        Bmc.check(added.size == 2 && added.contains(a) && added.contains(b))
    }

    // ---- MapsKt.toMap / toMutableMap / toSortedMap (models/kotlin-collections-2 pass) ----

    @BmcProof
    fun toMap_copies_entries() {
        val m = mapOf(1 to 10, 2 to 20).toMap()
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    @BmcProof
    fun toMutableMap_copies_and_is_mutable() {
        val m = mapOf(1 to 10).toMutableMap()
        m[2] = 20
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    @BmcProof
    fun toMap_from_pair_list() {
        val m = listOf(1 to 10, 2 to 20).toMap()
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    // NOTE: no toSortedMap proof — that op stays in the @BmcModelTail residue (returns a TreeMap whose
    // sorted navigation is a documented JBMC contract-level divergence / dynamic-cast artifact; no sound
    // bounded proof of the sorted observable). See MapsKt.java.

    // ---- SetsKt.setOfNotNull / hashSetOf / linkedSetOf (models/kotlin-collections-2 pass) ----

    @BmcProof
    fun setOfNotNull_filters_nulls() {
        val s = setOfNotNull(1, null, 2, null, 2)
        Bmc.check(s.size == 2 && s.contains(1) && s.contains(2))
    }

    @BmcProof
    fun setOfNotNull_single_null_is_empty() {
        val s = setOfNotNull<Int>(null)
        Bmc.check(s.isEmpty())
    }

    @BmcProof
    fun hashSetOf_dedups() {
        val s = hashSetOf(1, 2, 2, 3)
        Bmc.check(s.size == 3 && s.contains(2) && !s.contains(9))
    }

    @BmcProof
    fun linkedSetOf_dedups() {
        val s = linkedSetOf(1, 2, 2, 3)
        Bmc.check(s.size == 3 && s.contains(1) && s.contains(3))
    }

    /** Symbolic setOfNotNull law: for distinct a,b — setOfNotNull(a, null, b) has exactly {a,b}. */
    @BmcProof
    fun symbolic_setOfNotNull() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val s = setOfNotNull(a, null, b)
        Bmc.check(s.size == 2 && s.contains(a) && s.contains(b))
    }
}
