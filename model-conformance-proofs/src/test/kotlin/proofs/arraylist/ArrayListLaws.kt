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
}
