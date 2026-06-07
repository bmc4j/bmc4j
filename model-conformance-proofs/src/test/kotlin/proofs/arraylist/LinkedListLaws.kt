package proofs.arraylist

import java.util.LinkedList
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the LinkedList model's Deque/Queue surface must satisfy
 * under JBMC — head/tail insertion and removal over the bounded backing array, and the interplay
 * with the inherited List surface. Symbolic over the values, so they hold for every value at once.
 */
class LinkedListLaws {

    @BmcProof
    fun addFirst_then_get0_and_peekFirst() {
        val l = LinkedList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.addLast(a)
        l.addFirst(b)                         // [b, a]
        Bmc.check(l[0] == b && l[1] == a && l.size == 2 && l.peekFirst() == b)
    }

    @BmcProof
    fun addLast_appends_at_tail() {
        val l = LinkedList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.addLast(a)
        l.addLast(b)                          // [a, b]
        Bmc.check(l.getFirst() == a && l.getLast() == b && l[1] == b)
    }

    @BmcProof
    fun offer_poll_is_fifo() {
        val l = LinkedList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.offer(a)
        l.offer(b)
        Bmc.check(l.poll() == a && l.poll() == b && l.poll() == null)   // FIFO, then empty -> null
    }

    @BmcProof
    fun push_pop_is_lifo() {
        val l = LinkedList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        l.push(a)
        l.push(b)                             // stack top is b
        Bmc.check(l.pop() == b && l.pop() == a && l.size == 0)
    }

    @BmcProof
    fun removeFirst_removeLast_round_trip() {
        val l = LinkedList<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val c = Bmc.anyInt()
        l.addLast(a); l.addLast(b); l.addLast(c)   // [a, b, c]
        Bmc.check(l.removeFirst() == a && l.removeLast() == c && l.size == 1 && l[0] == b)
    }

    @BmcProof
    fun peek_and_poll_on_empty_are_null() {
        val l = LinkedList<Int>()
        Bmc.check(l.peek() == null && l.poll() == null && l.peekFirst() == null && l.peekLast() == null)
    }

    // removeFirstOccurrence drops the FIRST element equal to the arg (head->tail); with a duplicate
    // present, the later copy survives and the earlier one is gone — the index shifts down by one.
    @BmcProof
    fun removeFirstOccurrence_removes_the_earliest_match() {
        val l = LinkedList<Int>()
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        Bmc.assume(x != y)
        l.addLast(x); l.addLast(y); l.addLast(x)   // [x, y, x]
        val removed = l.removeFirstOccurrence(x)
        Bmc.check(removed && l.size == 2 && l[0] == y && l[1] == x)   // first x gone
    }

    // removeLastOccurrence drops the LAST element equal to the arg (tail->head); the earlier copy
    // survives in place.
    @BmcProof
    fun removeLastOccurrence_removes_the_latest_match() {
        val l = LinkedList<Int>()
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        Bmc.assume(x != y)
        l.addLast(x); l.addLast(y); l.addLast(x)   // [x, y, x]
        val removed = l.removeLastOccurrence(x)
        Bmc.check(removed && l.size == 2 && l[0] == x && l[1] == y)   // last x gone
    }

    // Absent element: neither occurrence-removal changes the list, and both report false.
    @BmcProof
    fun removeOccurrence_of_absent_is_false_and_noop() {
        val l = LinkedList<Int>()
        val a = Bmc.anyInt()
        val missing = Bmc.anyInt()
        Bmc.assume(a != missing)
        l.addLast(a)
        Bmc.check(!l.removeFirstOccurrence(missing) && !l.removeLastOccurrence(missing))
        Bmc.check(l.size == 1 && l[0] == a)
    }
}
