package proofs.arraylist

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the ArrayList model must satisfy under
 * JBMC's own semantics — the interpreter real proofs rely on. These verify the model itself, over
 * symbolic inputs, so they hold for every value at once. All must pass.
 */
class ArrayListLaws {

    @BmcProof
    fun new_list_is_empty() {
        val l = ArrayList<Int>()
        Bmc.check(l.size == 0 && l.isEmpty())
    }

    @BmcProof
    fun add_appends_and_increments_size() {
        val l = ArrayList<Int>()
        val x = Bmc.anyInt()
        l.add(x)
        Bmc.check(l.size == 1 && l[0] == x)   // get(size-1) returns what we put
    }

    @BmcProof
    fun set_replaces_in_place_and_returns_old() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.add(a)
        val old = l.set(0, b)
        Bmc.check(old == a && l[0] == b && l.size == 1)
    }

    @BmcProof
    fun contains_and_indexOf_find_an_added_element() {
        val l = ArrayList<Int>()
        val x = Bmc.anyInt()
        l.add(x)
        Bmc.check(l.contains(x) && l.indexOf(x) == 0)
    }

    @BmcProof
    fun removeAt_shifts_down_and_shrinks() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.add(a)
        l.add(b)
        val removed = l.removeAt(0)
        Bmc.check(removed == a && l.size == 1 && l[0] == b)
    }

    @BmcProof
    fun removeObject_deletes_first_equal_and_reports() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.add(a)
        l.add(b)
        val present = l.remove(a)               // Collection.remove(Object) -> boolean
        Bmc.check(present && l.size == 1 && l[0] == b)
    }

    @BmcProof
    fun removeObject_absent_returns_false_and_keeps_size() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        l.add(a)
        val absent = l.remove(a + 1)
        Bmc.check(!absent && l.size == 1 && l[0] == a)
    }

    @BmcProof
    fun clear_empties() {
        val l = ArrayList<Int>()
        l.add(Bmc.anyInt())
        l.add(Bmc.anyInt())
        l.clear()
        Bmc.check(l.size == 0 && l.isEmpty())
    }

    @BmcProof
    fun copy_constructor_preserves_elements_in_order() {
        val src = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        src.add(a)
        src.add(b)
        val copy = ArrayList<Int>(src)
        Bmc.check(copy.size == 2 && copy[0] == a && copy[1] == b)
    }

    // --- bulk ops: addAll / removeIf / forEach (lambdas through the model) --------------------------

    @BmcProof
    fun addAll_appends_in_order() {
        val src = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        src.add(a); src.add(b)
        val dst = ArrayList<Int>()
        val z = Bmc.anyInt()
        dst.add(z)
        val changed = dst.addAll(src)               // [z, a, b]
        Bmc.check(changed && dst.size == 3 && dst[0] == z && dst[1] == a && dst[2] == b)
    }

    @BmcProof
    fun removeIf_drops_matching_via_lambda() {
        // A real lambda passed through removeIf must devirtualize (bmc4j desugars it). Pin both that
        // the negatives are dropped and that the survivor is the one we kept.
        val l = ArrayList<Int>()
        val p = Bmc.anyInt(0, 100)                  // positive
        val n = Bmc.anyInt(-100, -1)                // negative
        l.add(p); l.add(n)
        val changed = l.removeIf { it < 0 }
        Bmc.check(changed && l.size == 1 && l[0] == p)
    }

    @BmcProof
    fun forEach_visits_every_element_via_lambda() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        l.add(a); l.add(b)
        val sum = intArrayOf(0)
        l.forEach { sum[0] += it }                  // lambda accumulates through the model's forEach
        Bmc.check(sum[0] == a + b)
    }

    @BmcProof
    fun removeIf_no_match_leaves_list_unchanged() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt(0, 100)
        l.add(a)
        val changed = l.removeIf { it < 0 }         // a >= 0, nothing removed
        Bmc.check(!changed && l.size == 1 && l[0] == a)
    }

    // SequencedCollection head/tail ops (addFirst/addLast/getFirst/getLast/removeFirst/removeLast)
    // only resolve against java.util.List on the Java 21+ floor, so those proofs live in the
    // jvm21+ source set (see build.gradle.kts). lastIndexOf is on List in every supported floor.

    // --- positional add / addAll(int) / containsAll / replaceAll ------------------------------------

    @BmcProof
    fun add_at_index_shifts_tail_right() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val x = Bmc.anyInt()
        l.add(a); l.add(b)
        l.add(1, x)                                 // [a, x, b]
        Bmc.check(l.size == 3 && l[0] == a && l[1] == x && l[2] == b)
    }

    @BmcProof
    fun addAll_at_index_inserts_in_order() {
        val src = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        src.add(a); src.add(b)
        val dst = ArrayList<Int>()
        val z = Bmc.anyInt()
        dst.add(z); dst.add(z + 1)
        val changed = dst.addAll(1, src)            // [z, a, b, z+1]
        Bmc.check(changed && dst.size == 4 && dst[0] == z && dst[1] == a && dst[2] == b && dst[3] == z + 1)
    }

    @BmcProof
    fun containsAll_true_iff_every_element_present() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.add(a); l.add(b)
        // b+1 must be genuinely absent from l={a,b}: b+1 != b always, but b+1 != a only if a != b+1
        // (else other={a,b+1}={a,a} is fully contained and the law's own assertion is false).
        Bmc.assume(a != b + 1)
        val sub = ArrayList<Int>()
        sub.add(a)
        val other = ArrayList<Int>()
        other.add(a); other.add(b + 1)              // b+1 now absent (b+1 != a and b+1 != b)
        Bmc.check(l.containsAll(sub) && !l.containsAll(other))
    }

    @BmcProof
    fun replaceAll_maps_every_element_via_lambda() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        l.add(a); l.add(b)
        l.replaceAll { it + 1 }
        Bmc.check(l.size == 2 && l[0] == a + 1 && l[1] == b + 1)
    }

    @BmcProof
    fun lastIndexOf_finds_the_last_equal_element() {
        val l = ArrayList<Int>()
        val x = Bmc.anyInt()
        l.add(x)            // index 0
        l.add(x + 1)        // index 1
        l.add(x)            // index 2 (duplicate of index 0)
        Bmc.check(l.indexOf(x) == 0 && l.lastIndexOf(x) == 2)
    }
}
