package proofs.hashset

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the HashSet model must satisfy under JBMC's own
 * semantics, over symbolic inputs (so they hold for every value at once). All must pass.
 */
class HashSetLaws {

    @BmcProof
    fun new_set_is_empty() {
        val s = HashSet<Int>()
        Bmc.check(s.size == 0 && s.isEmpty())
    }

    @BmcProof
    fun add_then_contains() {
        val s = HashSet<Int>()
        val x = Bmc.anyInt()
        s.add(x)
        Bmc.check(s.contains(x) && s.size == 1)
    }

    @BmcProof
    fun add_is_idempotent_for_equal_elements() {
        val s = HashSet<Int>()
        val x = Bmc.anyInt()
        s.add(x)
        val addedAgain = s.add(x)   // duplicate: rejected, size unchanged
        Bmc.check(!addedAgain && s.size == 1)
    }

    @BmcProof
    fun remove_deletes_membership() {
        val s = HashSet<Int>()
        val x = Bmc.anyInt()
        s.add(x)
        s.remove(x)
        Bmc.check(!s.contains(x) && s.size == 0)
    }

    @BmcProof
    fun copy_constructor_dedups_the_source() {
        val src = ArrayList<Int>()
        val x = Bmc.anyInt()
        src.add(x)
        src.add(x)   // duplicate in the source
        val s = HashSet<Int>(src)
        Bmc.check(s.size == 1 && s.contains(x))
    }

    // --- stream() adapter (thin ListStream over the deduped elements) ------------------------------

    @BmcProof
    fun stream_count_equals_set_size() {
        val s = HashSet<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        Bmc.assume(a != b)
        s.add(a)
        s.add(b)
        s.add(a)   // duplicate, dropped
        Bmc.check(s.stream().count() == 2L)
    }

    @BmcProof
    fun stream_filter_then_count_via_lambda() {
        // A real predicate through the set's stream must devirtualize (bmc4j desugars the lambda).
        val s = HashSet<Int>()
        val p = Bmc.anyInt(0, 100)    // positive
        val n = Bmc.anyInt(-100, -1)  // negative
        s.add(p)
        s.add(n)
        Bmc.check(s.stream().filter { it >= 0 }.count() == 1L)
    }
}
