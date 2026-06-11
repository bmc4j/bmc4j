package proofs.linkedhashset

import java.util.LinkedHashSet
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the LinkedHashSet model's SequencedSet surface must satisfy
 * under JBMC's own semantics. The backing array preserves insertion order, so these pin getFirst/
 * getLast (the ends of that order), the (re)positioning addFirst/addLast (a present element is moved
 * to the addressed end), and removeFirst/removeLast (read-and-remove the ends). Concrete elements
 * where the ordering relationship is the point.
 */
class LinkedHashSetLaws {

    @BmcProof(unwind = 4)
    fun getFirst_and_getLast_are_the_insertion_ends() {
        val s = LinkedHashSet<Int>()
        s.add(3)
        s.add(1)
        s.add(2)
        Bmc.check(s.getFirst() == 3 && s.getLast() == 2)
    }

    @BmcProof(unwind = 4)
    fun addFirst_moves_a_present_element_to_the_front() {
        val s = LinkedHashSet<Int>()
        s.add(1)
        s.add(2)
        s.add(3)
        s.addFirst(2)                            // present -> moved to front, no size change
        Bmc.check(s.getFirst() == 2 && s.size == 3)
        s.addFirst(0)                            // absent -> inserted at front
        Bmc.check(s.getFirst() == 0 && s.size == 4)
    }

    @BmcProof(unwind = 4)
    fun addLast_moves_a_present_element_to_the_back() {
        val s = LinkedHashSet<Int>()
        s.add(1)
        s.add(2)
        s.add(3)
        s.addLast(1)                             // present -> moved to back, no size change
        Bmc.check(s.getLast() == 1 && s.size == 3)
    }

    @BmcProof(unwind = 4)
    fun removeFirst_and_removeLast_pop_the_insertion_ends() {
        val s = LinkedHashSet<Int>()
        s.add(1)
        s.add(2)
        s.add(3)
        Bmc.check(s.removeFirst() == 1 && s.removeLast() == 3)
        Bmc.check(s.size == 1 && s.contains(2))
    }
}
