package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.LinkedList} as an array-backed list — behaviourally equivalent for
 * proofs (the linked structure doesn't affect functional results, only performance). The full
 * {@link List} surface is inherited from {@link ArrayList}; this class adds the Deque/Queue surface
 * (addFirst/addLast/getFirst/getLast/removeFirst/removeLast/peek/poll/offer/push/pop) on top of the
 * same bounded backing array, so idiomatic queue/deque use resolves to a sound body rather than a
 * silent nondet stub.
 *
 * <p>Semantics match the JDK exactly: head is index 0, tail is index {@code size-1};
 * {@code getFirst}/{@code getLast}/{@code removeFirst}/{@code removeLast}/{@code element}/
 * {@code remove}/{@code pop} throw {@link NoSuchElementException} on an empty list, while
 * {@code peek}/{@code peekFirst}/{@code peekLast}/{@code poll}/{@code pollFirst}/{@code pollLast}
 * return {@code null}. {@code offer}/{@code offerLast} enqueue at the tail (and always return true
 * within capacity, like the JDK's unbounded deque), {@code offerFirst}/{@code push}/{@code addFirst}
 * insert at the head. The Deque ops compose with the inherited List ops: {@code addFirst(x)} then
 * {@code get(0)} returns {@code x}; {@code addLast(x)} then {@code get(size-1)} returns {@code x}.
 * Capacity is the inherited {@value ArrayList#CAPACITY}; inserting past it is out of bounds (loud
 * backing-array write), never a silent drop.
 */
// The List/Collection surface is inherited from the ArrayList model; the Deque/Queue surface
// (addFirst/addLast/getFirst/getLast/removeFirst/removeLast/offer*/poll*/peek*/push/pop, plus the
// Queue offer/poll/peek/remove/element) is implemented here. Blanket-conforms covers both; the tail
// is the remaining Deque/List surface still unmodeled.
@BmcModelTail(reason = "the remaining Deque/List surface not implemented (descendingIterator/descendingDeque ops, listIterator/subList/spliterator, reversed, clone) is out of scope for this array-backed model; all loud under JBMC")
public class LinkedList<E> extends ArrayList<E> implements Queue<E> {

    public LinkedList() {
        super();
    }

    public LinkedList(Collection<? extends E> c) {
        super(c);
    }

    // --- Deque: head/tail insertion ------------------------------------------
    // addFirst/addLast/getFirst/getLast/removeFirst/removeLast are inherited from the ArrayList model
    // (its SequencedCollection ops have identical array-backed semantics) — head is index 0, tail is
    // size-1, the throwing-on-empty split matches. Only the Deque-specific surface lives here.

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /** Deque/Stack push: insert at the head (equivalent to {@link #addFirst}). */
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public void push(E e) {
        addFirst(e);
    }

    // --- Deque: head/tail peek (null on empty) -------------------------------

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E peekFirst() {
        return isEmpty() ? null : get(0);
    }

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E peekLast() {
        return isEmpty() ? null : get(size() - 1);
    }

    /** Deque/Stack pop: remove and return the head; throws when empty (like the JDK). */
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E pop() {
        return removeFirst();
    }

    // --- Deque: head/tail removal (null on empty) ----------------------------

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E pollFirst() {
        return isEmpty() ? null : remove(0);
    }

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E pollLast() {
        return isEmpty() ? null : remove(size() - 1);
    }

    // --- Queue surface (FIFO: enqueue at tail, dequeue at head) --------------

    @Override
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public boolean offer(E e) {
        return offerLast(e);
    }

    @Override
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E poll() {
        return pollFirst();
    }

    @Override
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E peek() {
        return peekFirst();
    }

    @Override
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E remove() {
        return removeFirst();
    }

    @Override
    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public E element() {
        return getFirst();
    }
}
