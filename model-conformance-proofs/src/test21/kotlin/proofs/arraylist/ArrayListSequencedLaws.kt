package proofs.arraylist

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs for ArrayList's SequencedCollection head/tail surface (addFirst/addLast/
 * getFirst/getLast/removeFirst/removeLast). These methods only exist on java.util.List from
 * Java 21 onward, so this class compiles solely on the 21+ floor — it lives in the jvm21+
 * source set that the build wires into the test compilation only when bmcJvmTarget >= 21.
 */
class ArrayListSequencedLaws {

    @BmcProof
    fun addFirst_inserts_at_head_addLast_at_tail() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val c = Bmc.anyInt()
        l.addLast(a)        // [a]
        l.addFirst(b)       // [b, a]
        l.addLast(c)        // [b, a, c]
        Bmc.check(l.size == 3 && l[0] == b && l[1] == a && l[2] == c)
        Bmc.check(l.getFirst() == b && l.getLast() == c)
    }

    @BmcProof
    fun removeFirst_and_removeLast_take_the_ends() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val c = Bmc.anyInt()
        l.addLast(a); l.addLast(b); l.addLast(c)  // [a, b, c]
        val first = l.removeFirst()               // a, leaves [b, c]
        val last = l.removeLast()                 // c, leaves [b]
        Bmc.check(first == a && last == c && l.size == 1 && l[0] == b)
    }
}
