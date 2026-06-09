package proofs.arraydeque

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.ArrayDeque

/**
 * Model proofs (axis 2): algebraic laws the ArrayDeque model must satisfy under JBMC's own semantics,
 * over symbolic inputs (so they hold for every value at once). All must pass.
 */
class ArrayDequeLaws {

    @BmcProof
    fun new_deque_is_empty() {
        val d = ArrayDeque<Int>()
        Bmc.check(d.size == 0 && d.isEmpty())
    }

    // FIFO: offer at the tail, poll from the head — first in, first out.
    @BmcProof
    fun fifo_offer_then_poll_preserves_order() {
        val d = ArrayDeque<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        d.offer(a)
        d.offer(b)
        Bmc.check(d.poll() == a && d.poll() == b && d.isEmpty())
    }

    // LIFO: push at the head, pop from the head — last in, first out.
    @BmcProof
    fun lifo_push_then_pop_reverses_order() {
        val d = ArrayDeque<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        d.push(a)
        d.push(b)
        Bmc.check(d.pop() == b && d.pop() == a && d.isEmpty())
    }

    @BmcProof
    fun addFirst_puts_at_head_addLast_at_tail() {
        val d = ArrayDeque<Int>()
        val mid = Bmc.anyInt()
        val head = Bmc.anyInt()
        val tail = Bmc.anyInt()
        d.addLast(mid)
        d.addFirst(head)
        d.addLast(tail)
        Bmc.check(d.getFirst() == head && d.getLast() == tail && d.size == 3)
    }

    @BmcProof
    fun peekFirst_does_not_remove() {
        val d = ArrayDeque<Int>()
        val x = Bmc.anyInt()
        d.addFirst(x)
        Bmc.check(d.peekFirst() == x && d.size == 1)
    }

    @BmcProof
    fun add_then_contains_and_size() {
        val d = ArrayDeque<Int>()
        val x = Bmc.anyInt()
        d.add(x)
        Bmc.check(d.contains(x) && d.size == 1)
    }

    @BmcProof
    fun removeFirstOccurrence_deletes_one() {
        val d = ArrayDeque<Int>()
        val x = Bmc.anyInt()
        d.addLast(x)
        d.addLast(x)            // two copies
        val removed = d.removeFirstOccurrence(x)
        Bmc.check(removed && d.size == 1 && d.contains(x))
    }

    @BmcProof
    fun pollFirst_on_empty_is_null() {
        val d = ArrayDeque<Int>()
        Bmc.check(d.pollFirst() == null && d.peekFirst() == null)
    }

    @BmcProof
    fun forEach_visits_every_element_via_lambda() {
        val d = ArrayDeque<Int>()
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        d.addLast(a)
        d.addLast(b)
        val sum = intArrayOf(0)
        d.forEach { sum[0] += it }
        Bmc.check(sum[0] == a + b)
    }

    @BmcProof
    fun removeIf_drops_matching_via_lambda() {
        val d = ArrayDeque<Int>()
        val p = Bmc.anyInt(0, 100)
        val n = Bmc.anyInt(-100, -1)
        d.addLast(p)
        d.addLast(n)
        val changed = d.removeIf { it < 0 }
        Bmc.check(changed && d.size == 1 && d.contains(p))
    }
}
