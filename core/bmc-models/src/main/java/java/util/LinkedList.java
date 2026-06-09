package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

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
// Queue offer/poll/peek/remove/element) is implemented here. The List view ops (listIterator/subList/
// reversed/parallelStream/removeRange), the spliterator + toArray(IntFunction) walls, and the
// clone/sort/toArray(T[]) stubs are all inherited from the ArrayList model; descendingIterator is the
// only Deque-specific surface modeled here.
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

    // --- Deque: occurrence removal -------------------------------------------
    // removeFirstOccurrence removes the FIRST element equal to o (head→tail), removeLastOccurrence the
    // LAST (tail→head); each returns whether a removal happened, like the JDK Deque. The first/last
    // split is exactly indexOf vs lastIndexOf over the inherited bounded backing array.

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public boolean removeLastOccurrence(Object o) {
        int i = lastIndexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    // --- Deque: descending iteration -----------------------------------------
    // An iterator over the elements from tail to head (the reverse of the inherited iterator()), by
    // index over the inherited bounded backing array — never a virtual dispatch, so it resolves soundly.

    @BmcModelConforms("inherits the ArrayList model surface (incl. the SequencedCollection head/tail ops) + an implemented Deque/Queue surface")
    public Iterator<E> descendingIterator() {
        return new DescItr();
    }

    private final class DescItr implements Iterator<E> {
        private int cursor = size() - 1;

        @Override
        public boolean hasNext() {
            return cursor >= 0;
        }

        @Override
        public E next() {
            if (cursor < 0) {
                throw new NoSuchElementException();
            }
            return get(cursor--);
        }
    }

    // --- explicitly UNMODELLED members (loud stubs) -----------------------------------------------
    // The per-member auditing gate accounts a subclass's own real surface against its OWN method-level
    // stubs, so the loud stubs the ArrayList model declares for these members are re-declared here for
    // the (covariantly-typed) LinkedList surface. Same decisions + reasons as the ArrayList model.

    @BmcModelConforms("@BmcProof (proofs.sort SortWitnessLaws)")
    public void sort(Comparator<? super E> c) {
        // Nondet sorted-permutation witness (java.util.BmcSortWitness), in place over the inherited
        // ArrayList backing via set(int, E): a bijective permutation of the current elements that is
        // non-decreasing under the comparator. Sound for ordering proofs; equal elements not stable.
        ArrayList<E> ordered = BmcSortWitness.sorted(this, c);
        for (int i = 0; i < ordered.size(); i++) {
            set(i, ordered.get(i));
        }
    }

    @BmcUnmodelable(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.LinkedList.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "array snapshot via a reflective IntFunction generator (creates a T[] of a reflective component type) — iterate the model instead")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        throw fail("bmc4j: unmodelled member java.util.LinkedList.toArray(java.util.function.IntFunction) — array snapshot via a reflective generator — iterate the model instead");
    }

    @BmcUnmodelable(reason = "parallel-decomposition Spliterator (a tryAdvance/trySplit traversal view a sequential bounded model can't represent) — iterate the model or use stream() instead")
    public Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.LinkedList.spliterator() — parallel-decomposition Spliterator view — iterate the model or use stream() instead");
    }

    @BmcUnmodelable(reason = "shallow copy of a bounded model — construct a fresh list from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.LinkedList.clone() — shallow copy of a bounded model — construct a fresh list from the elements instead");
    }
}
