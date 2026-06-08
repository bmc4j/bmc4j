package proofs.linkedhashmap

import java.util.LinkedHashMap
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the LinkedHashMap model's SequencedMap surface must satisfy
 * under JBMC's own semantics. The backing arrays preserve insertion order, so these pin the ends of
 * that order (first/last), the read-and-remove poll ops, and the (re)positioning putFirst/putLast
 * (a present key is moved to the addressed end). Concrete keys where the ordering relationship is the
 * point; the empty-map null split is symbolic-agnostic.
 *
 * The sequenced VIEW surface (sequencedKeySet/sequencedValues/sequencedEntrySet) is deliberately NOT
 * proven here: those are java.util.SequencedMap default methods, and JBMC binds the real JDK default
 * over the model override and havocs the returned view (a devirtualization artifact, same class as the
 * TreeMap sorted-navigation tail). They are pinned on the differential axis (MapConformanceTest)
 * instead, which runs the model on a real JVM where the override is honored.
 */
class LinkedHashMapLaws {

    @BmcProof
    fun firstEntry_and_lastEntry_are_the_insertion_ends() {
        val m = LinkedHashMap<Int, Int>()
        m[3] = 30
        m[1] = 10
        m[2] = 20
        val first = m.firstEntry()
        val last = m.lastEntry()
        Bmc.check(first.key == 3 && first.value == 30)   // first inserted
        Bmc.check(last.key == 2 && last.value == 20)      // last inserted
    }

    @BmcProof
    fun firstEntry_and_lastEntry_on_empty_map_are_null() {
        val m = LinkedHashMap<Int, Int>()
        Bmc.check(m.firstEntry() == null && m.lastEntry() == null)
    }

    @BmcProof
    fun pollFirst_and_pollLast_remove_the_insertion_ends() {
        val m = LinkedHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        m[3] = 30
        val first = m.pollFirstEntry()
        val last = m.pollLastEntry()
        Bmc.check(first.key == 1 && first.value == 10)
        Bmc.check(last.key == 3 && last.value == 30)
        Bmc.check(m.size == 1 && m[2] == 20)
    }

    @BmcProof
    fun poll_on_empty_map_is_null() {
        val m = LinkedHashMap<Int, Int>()
        Bmc.check(m.pollFirstEntry() == null && m.pollLastEntry() == null)
        Bmc.check(m.isEmpty())
    }

    @BmcProof
    fun putFirst_moves_a_present_key_to_the_front() {
        val m = LinkedHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        m[3] = 30
        m.putFirst(2, 22)                  // present key 2 -> front, value updated
        Bmc.check(m.firstEntry().key == 2 && m.firstEntry().value == 22)
        Bmc.check(m.size == 3 && m[2] == 22)
        m.putFirst(0, 0)                   // absent key -> installed at the front
        Bmc.check(m.firstEntry().key == 0 && m.size == 4)
    }

    @BmcProof
    fun putLast_moves_a_present_key_to_the_back() {
        val m = LinkedHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        m[3] = 30
        m.putLast(1, 11)                   // present key 1 -> back, value updated
        Bmc.check(m.lastEntry().key == 1 && m.lastEntry().value == 11)
        Bmc.check(m.size == 3 && m[1] == 11)
    }

}
