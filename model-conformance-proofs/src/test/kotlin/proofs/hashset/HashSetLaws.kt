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

    @BmcProof(unwind = 4)
    fun copy_constructor_dedups_the_source() {
        val src = ArrayList<Int>()
        val x = Bmc.anyInt()
        src.add(x)
        src.add(x)   // duplicate in the source
        val s = HashSet<Int>(src)
        Bmc.check(s.size == 1 && s.contains(x))
    }

    // --- stream() adapter (thin ListStream over the deduped elements) ------------------------------

    @BmcProof(unwind = 4)
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

    @BmcProof(unwind = 4)
    fun stream_filter_then_count_via_lambda() {
        // A real predicate through the set's stream must devirtualize (bmc4j desugars the lambda).
        val s = HashSet<Int>()
        val p = Bmc.anyInt(0, 100)    // positive
        val n = Bmc.anyInt(-100, -1)  // negative
        s.add(p)
        s.add(n)
        Bmc.check(s.stream().filter { it >= 0 }.count() == 1L)
    }

    // --- functional / bulk ops (lambdas through the set; bulk over a source collection) ------------

    @BmcProof(unwind = 4)
    fun removeIf_drops_matching_via_lambda() {
        // A real predicate through removeIf must devirtualize (bmc4j desugars the lambda).
        val s = HashSet<Int>()
        val p = Bmc.anyInt(0, 100)
        val n = Bmc.anyInt(-100, -1)
        s.add(p)
        s.add(n)
        val changed = s.removeIf { it < 0 }
        Bmc.check(changed && s.size == 1 && s.contains(p))
    }

    @BmcProof(unwind = 4)
    fun forEach_visits_every_element_via_lambda() {
        val s = HashSet<Int>()
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.assume(a != b)
        s.add(a)
        s.add(b)
        val sum = intArrayOf(0)
        s.forEach { sum[0] += it }
        Bmc.check(sum[0] == a + b)
    }

    @BmcProof(unwind = 4)
    fun addAll_dedups_distinct_elements() {
        val src = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        Bmc.assume(a != b)
        src.add(a); src.add(b); src.add(a)   // duplicate of a
        val s = HashSet<Int>()
        s.add(a)                              // a already present
        val changed = s.addAll(src)           // only b is new
        Bmc.check(changed && s.size == 2 && s.contains(a) && s.contains(b))
    }

    @BmcProof(unwind = 4)
    fun containsAll_true_iff_every_element_present() {
        val s = HashSet<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        Bmc.assume(a != b)
        s.add(a); s.add(b)
        val sub = ArrayList<Int>()
        sub.add(a)
        val other = ArrayList<Int>()
        other.add(a); other.add(b + 1)
        Bmc.assume(b + 1 != a)
        Bmc.check(s.containsAll(sub) && !s.containsAll(other))
    }

    @BmcProof(unwind = 4)
    fun retainAll_keeps_only_the_intersection() {
        val s = HashSet<Int>()
        val keep = Bmc.anyInt()
        val drop = Bmc.anyInt()
        Bmc.assume(keep != drop)
        s.add(keep); s.add(drop)
        val keepSet = ArrayList<Int>()
        keepSet.add(keep)
        val changed = s.retainAll(keepSet)
        Bmc.check(changed && s.size == 1 && s.contains(keep) && !s.contains(drop))
    }

    // The presizing factory java.util.HashSet.newHashSet(int) is Java 19+, so its proof lives in the
    // jvm21+ source set (src/test21, proofs.hashset.HashSetNewFactoryLaws) — it cannot resolve on the
    // Java-17 conformance floor.
}
