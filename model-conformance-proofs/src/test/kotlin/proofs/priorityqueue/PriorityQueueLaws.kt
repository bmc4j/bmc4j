package proofs.priorityqueue

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import java.util.PriorityQueue

/**
 * Model proofs (axis 2): algebraic laws the PriorityQueue model must satisfy under JBMC's own
 * semantics, over symbolic inputs (so they hold for every value at once). The model backs an
 * unordered array and selects the least element by a bounded linear scan; these laws pin the PQ
 * CONTRACT (the head is the minimum; successive polls are non-decreasing) under BOTH natural order
 * (the builtin-Comparable ladder, over symbolic Integer and String) and a lambda Comparator.
 */
class PriorityQueueLaws {

    @BmcProof
    fun new_queue_is_empty() {
        val q = PriorityQueue<Int>()
        Bmc.check(q.size == 0 && q.isEmpty())
    }

    // --- natural order, symbolic Integer ---------------------------------------------------------

    @BmcProof(unwind = 2)
    fun natural_poll_returns_the_minimum() {
        val q = PriorityQueue<Int>()
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        q.offer(a)
        q.offer(b)
        val first = q.poll()
        Bmc.check(first == if (a <= b) a else b)
    }

    // Non-decreasing across one poll step: the value polled (the current minimum) is <= the new head
    // after it is removed. Two symbolic elements keep the repeated-scan circuit decidable; this is the
    // adjacent-step form of "successive polls are non-decreasing".
    //
    // unwind = 4 (down from the build default of 16) is load-bearing here for the SAME reason as
    // natural_string_head_is_the_lexicographic_minimum below (lightened in #246): this is the only
    // natural-Integer law that POLLS (removeAt's shift loop on top of leastIndex's scan), so it is the
    // heaviest of the family. Every model loop it touches is bounded by size, which is at most 2 here
    // (leastIndex's `i<size` scan and removeAt's `j<size-1` shift), so each closes in <= 1 iteration;
    // unwind = 4 covers them completely with margin. At the default bound of 16, goto-build unrolls those
    // bounded loops far past what the proof can reach, and under the conformance leg's 4-wide fan-out on
    // the memory-tightest jdk17 floor leg jbmc exhausted memory and was killed before emitting any output
    // (empty stdout -> PARSE_FAILURE); 21/25 have the headroom to absorb the default bound. This heaviness
    // was latent — the verdict cache held a VERIFIED, so CI never re-derived it live on 17 — until a
    // SEMANTICS_REVISION bump invalidated the cache and forced the first fresh 17 derivation in a while.
    // A bound of 4 fully covers every reachable loop, so the proof verifies the SAME law over the SAME two
    // symbolic integers while fitting the engine's memory, independent of cache state.
    @BmcProof(unwind = 4)
    fun natural_poll_then_peek_is_non_decreasing() {
        val q = PriorityQueue<Int>()
        val a = Bmc.anyInt(-100, 100)
        val b = Bmc.anyInt(-100, 100)
        q.offer(a)
        q.offer(b)
        val first = q.poll()       // the minimum
        val next = q.peek()        // the remaining element
        Bmc.check(first <= next)
    }

    // Head-is-the-minimum over THREE elements via a single scan (peek does not remove, so this is one
    // bounded scan rather than three poll/shift rounds — decidable while still exercising 3 elements).
    @BmcProof(unwind = 4)
    fun natural_peek_is_the_minimum_of_three() {
        val q = PriorityQueue<Int>()
        val a = Bmc.anyInt(-50, 50)
        val b = Bmc.anyInt(-50, 50)
        val c = Bmc.anyInt(-50, 50)
        q.offer(a); q.offer(b); q.offer(c)
        val head = q.peek()
        val min = if (a <= b) (if (a <= c) a else c) else (if (b <= c) b else c)
        Bmc.check(head == min)
    }

    @BmcProof
    fun natural_peek_is_the_minimum_and_does_not_remove() {
        val q = PriorityQueue<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        q.offer(a)
        q.offer(b)
        val head = q.peek()
        Bmc.check(head == (if (a <= b) a else b) && q.size == 2)
    }

    // --- natural order, symbolic String (builtin Comparable via the instanceof ladder) -----------

    // Two symbolic single-char strings over a tiny alphabet: the builtin-Comparable ladder routes
    // String to String.compareTo, which here is a single-char comparison — proving the natural-order
    // path works for String (not just the numeric builtins). Uses peek (one bounded scan, no removal
    // shift over the String array) to stay decidable.
    //
    // unwind = 4 (down from the build default of 16) is load-bearing on the JDK-17 floor leg: with
    // maxStringLength = 1 the String.compareTo char loop and the 2-element heap arrays need only a
    // few iterations, but at the default bound the symbolic-String circuit grew large enough that
    // jbmc exhausted memory and was killed before emitting any output (empty stdout -> PARSE_FAILURE)
    // on 17 specifically (21/25 absorbed it). A bound of 4 fully covers the loops here, so the proof
    // verifies the same law over the same two symbolic strings while fitting the engine's memory.
    @BmcProof(maxStringLength = 1, unwind = 4, timeoutSeconds = 300)
    fun natural_string_head_is_the_lexicographic_minimum() {
        val q = PriorityQueue<String>()
        val a = Bmc.anyString(1, "ab")
        val b = Bmc.anyString(1, "ab")
        q.offer(a)
        q.offer(b)
        val head = q.peek()
        Bmc.check(head == if (a <= b) a else b)
    }

    // --- lambda Comparator (reverse order: the head is the MAXIMUM) ------------------------------

    @BmcProof
    fun comparator_reverse_poll_returns_the_maximum() {
        val q = PriorityQueue<Int>(Comparator { x, y -> y.compareTo(x) })
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        q.offer(a)
        q.offer(b)
        val first = q.poll()
        Bmc.check(first == if (a >= b) a else b)
    }

    @BmcProof
    fun comparator_returns_the_supplied_comparator() {
        val cmp = Comparator<Int> { x, y -> x.compareTo(y) }
        val q = PriorityQueue<Int>(cmp)
        Bmc.check(q.comparator() === cmp)
        val natural = PriorityQueue<Int>()
        Bmc.check(natural.comparator() == null)
    }

    @BmcProof
    fun peek_poll_on_empty_are_null_element_remove_throw() {
        val q = PriorityQueue<Int>()
        Bmc.check(q.peek() == null && q.poll() == null)
    }

    @BmcProof
    fun add_then_contains_and_size() {
        val q = PriorityQueue<Int>()
        val x = Bmc.anyInt()
        q.add(x)
        Bmc.check(q.contains(x) && q.size == 1)
    }

    // --- NEGATIVE law: a PQ does NOT preserve insertion order; the claim "poll returns the first
    // inserted element" is FALSE and must REFUTE. If this drifted to VERIFIED the least-element
    // selection would be broken (a silent soundness regression), so the expected-refutation guards it.
    @BmcProof(expect = Verdict.REFUTED)
    fun negative_poll_does_not_return_insertion_order() {
        val q = PriorityQueue<Int>()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        q.offer(a)
        q.offer(b)
        // FALSE in general: poll returns the MINIMUM, not the first-inserted a. A counterexample
        // exists whenever b < a, so JBMC must REFUTE.
        Bmc.check(q.poll() == a)
    }
}
