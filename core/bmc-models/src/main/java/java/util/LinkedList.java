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
@BmcModelConforms("inherits the ArrayList model surface + an implemented Deque/Queue surface; differential (LinkedList) + @BmcProof")
@BmcModelTail(reason = "the remaining Deque/List surface not implemented (descendingIterator/descendingDuque ops, listIterator/subList/spliterator, reversed/SequencedCollection, clone) is out of scope for this array-backed model; all loud under JBMC")
public class LinkedList<E> extends ArrayList<E> implements Queue<E> {

    public LinkedList() {
        super();
    }

    public LinkedList(Collection<? extends E> c) {
        super(c);
    }

    // --- Deque: head/tail insertion ------------------------------------------

    public void addFirst(E e) {
        insertAt(0, e);
    }

    public void addLast(E e) {
        add(e);
    }

    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /** Deque/Stack push: insert at the head (equivalent to {@link #addFirst}). */
    public void push(E e) {
        addFirst(e);
    }

    // --- Deque: head/tail peek (throwing) ------------------------------------

    public E getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return get(0);
    }

    public E getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return get(size() - 1);
    }

    // --- Deque: head/tail peek (null on empty) -------------------------------

    public E peekFirst() {
        return isEmpty() ? null : get(0);
    }

    public E peekLast() {
        return isEmpty() ? null : get(size() - 1);
    }

    // --- Deque: head/tail removal (throwing) ---------------------------------

    public E removeFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return remove(0);
    }

    public E removeLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return remove(size() - 1);
    }

    /** Deque/Stack pop: remove and return the head; throws when empty (like the JDK). */
    public E pop() {
        return removeFirst();
    }

    // --- Deque: head/tail removal (null on empty) ----------------------------

    public E pollFirst() {
        return isEmpty() ? null : remove(0);
    }

    public E pollLast() {
        return isEmpty() ? null : remove(size() - 1);
    }

    // --- Queue surface (FIFO: enqueue at tail, dequeue at head) --------------

    @Override
    public boolean offer(E e) {
        return offerLast(e);
    }

    @Override
    public E poll() {
        return pollFirst();
    }

    @Override
    public E peek() {
        return peekFirst();
    }

    @Override
    public E remove() {
        return removeFirst();
    }

    @Override
    public E element() {
        return getFirst();
    }

    // --- positional insert used by addFirst (shift right within capacity) ----

    /**
     * Insert {@code element} at {@code index}, shifting later elements one slot toward the tail
     * (like {@code ArrayList.add(int, E)}). Writing the new tail slot past capacity is out of bounds
     * — the documented loud model-bound signal, never a silent drop. Package-visible because
     * {@code ArrayList}'s backing array is private; this reuses the public List surface to stay sound.
     */
    private void insertAt(int index, E element) {
        if (index < 0 || index > size()) {
            throw new IndexOutOfBoundsException();
        }
        // Append a placeholder to grow size by one (loud if past capacity), then shift up.
        E last = size() == 0 ? null : get(size() - 1);
        add(last);
        for (int i = size() - 1; i > index; i--) {
            set(i, get(i - 1));
        }
        set(index, element);
    }
}
