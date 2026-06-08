package proofs.treeset

import java.util.ArrayList
import java.util.TreeSet
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the TreeSet model must satisfy under JBMC's own semantics, over
 * symbolic inputs where the range stays small. The set is composed over the TreeMap model, so these pin
 * the add/contains round-trip, the dedup-by-compareTo, and the sorted navigable surface (first/last and
 * the inclusive/exclusive ceiling/floor/higher/lower bounds) the analysis relies on.
 */
class TreeSetLaws {

    @BmcProof
    fun new_set_is_empty() {
        val s = TreeSet<Int>()
        Bmc.check(s.size == 0 && s.isEmpty())
    }

    @BmcProof
    fun add_then_contains() {
        val s = TreeSet<Int>()
        val x = Bmc.anyInt()
        s.add(x)
        Bmc.check(s.contains(x) && s.size == 1)
    }

    @BmcProof
    fun add_is_idempotent_for_equal_elements() {
        val s = TreeSet<Int>()
        val x = Bmc.anyInt()
        s.add(x)
        val addedAgain = s.add(x)   // duplicate (compareTo == 0): rejected, size unchanged
        Bmc.check(!addedAgain && s.size == 1)
    }

    @BmcProof
    fun remove_deletes_membership() {
        val s = TreeSet<Int>()
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
        val s = TreeSet<Int>(src)
        Bmc.check(s.size == 1 && s.contains(x))
    }

    @BmcProof
    fun first_and_last_are_the_min_and_max() {
        val s = TreeSet<Int>()
        s.add(3)
        s.add(1)
        s.add(2)
        Bmc.check(s.first() == 1 && s.last() == 3)
    }

    @BmcProof
    fun ceiling_is_least_element_at_or_above() {
        val s = TreeSet<Int>()
        s.add(2); s.add(4); s.add(6)
        Bmc.check(s.ceiling(4) == 4)    // inclusive: exact element qualifies
        Bmc.check(s.ceiling(3) == 4)    // rounds up to the next element
        Bmc.check(s.ceiling(7) == null) // nothing at or above
    }

    @BmcProof
    fun floor_is_greatest_element_at_or_below() {
        val s = TreeSet<Int>()
        s.add(2); s.add(4); s.add(6)
        Bmc.check(s.floor(4) == 4)      // inclusive
        Bmc.check(s.floor(5) == 4)      // rounds down
        Bmc.check(s.floor(1) == null)   // nothing at or below
    }

    @BmcProof
    fun higher_and_lower_are_strict() {
        val s = TreeSet<Int>()
        s.add(2); s.add(4); s.add(6)
        Bmc.check(s.higher(4) == 6)     // strictly greater
        Bmc.check(s.lower(4) == 2)      // strictly less
        Bmc.check(s.higher(6) == null)  // nothing strictly greater than the max
        Bmc.check(s.lower(2) == null)   // nothing strictly less than the min
    }

    @BmcProof
    fun navigation_on_empty_set_is_null_for_the_bound_family() {
        val s = TreeSet<Int>()
        val k = Bmc.anyInt()
        Bmc.check(s.ceiling(k) == null && s.floor(k) == null)
        Bmc.check(s.higher(k) == null && s.lower(k) == null)
    }

    @BmcProof
    fun comparator_is_null_for_natural_ordering() {
        val s = TreeSet<Int>()
        Bmc.check(s.comparator() == null)
    }

    // pollFirst returns the MIN and removes it; pollLast the MAX. After polling both ends of a 3-element
    // set only the middle remains, and a re-poll sees the new extreme.
    @BmcProof
    fun pollFirst_and_pollLast_remove_the_extremes() {
        val s = TreeSet<Int>()
        s.add(1); s.add(2); s.add(3)
        Bmc.check(s.pollFirst() == 1)
        Bmc.check(s.pollLast() == 3)
        Bmc.check(s.size == 1 && s.first() == 2 && s.last() == 2)
    }

    @BmcProof
    fun poll_on_empty_set_is_null() {
        val s = TreeSet<Int>()
        Bmc.check(s.pollFirst() == null && s.pollLast() == null)
        Bmc.check(s.isEmpty())
    }
}
