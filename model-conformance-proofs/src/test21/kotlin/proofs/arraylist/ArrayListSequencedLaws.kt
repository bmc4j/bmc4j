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

    // --- reversed() is a live reverse view (Java 21+) -----------------------------------------------
    // The read view verifies under JBMC (the model's reversed() body is reached). The write-through to
    // the returned view (mutating rev, observing the parent) is a JBMC devirtualization artifact — JBMC
    // can bind the real SequencedCollection/List.reversed() default over this override and havoc the
    // returned view's identity, exactly like the LinkedHashMap sequenced-view artifact — so it is pinned
    // on the differential axis (ArrayListConformanceTest), not as a @BmcProof.

    @BmcProof
    fun reversed_reads_the_backing_in_reverse_order() {
        val l = ArrayList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val c = Bmc.anyInt()
        l.add(a); l.add(b); l.add(c)              // [a, b, c]
        val rev = l.reversed()                    // view [c, b, a]
        Bmc.check(rev.size == 3 && rev[0] == c && rev[1] == b && rev[2] == a)
    }
}
