package java.util;

/**
 * Minimal BMC model of {@link java.util.Deque}, the double-ended queue interface between
 * {@link Queue} and the concrete {@link ArrayDeque}/{@link LinkedList} models. Declares the
 * head/tail surface ({@code addFirst}/{@code addLast}/{@code offerFirst}/{@code offerLast}/
 * {@code removeFirst}/{@code removeLast}/{@code pollFirst}/{@code pollLast}/{@code peekFirst}/
 * {@code peekLast}/{@code getFirst}/{@code getLast}), the Queue aliases inherited from {@link Queue},
 * and the Stack aliases ({@code push}/{@code pop}) plus {@code descendingIterator} and the
 * occurrence-removal ops, so code typed against {@code Deque} devirtualizes onto a concrete model.
 *
 * <p>The real {@code java.util.Deque} extends {@code SequencedCollection} only on Java 21+; to keep
 * the Java-17 floor build resolving cleanly this models it as {@code extends Queue} (the head/tail
 * surface is declared directly here rather than inherited from a SequencedCollection model).
 */
public interface Deque<E> extends Queue<E> {

    // --- head/tail insertion ---------------------------------------------------------------------

    /** Insert at the head; throws on capacity overflow (loud, never a silent drop). */
    void addFirst(E e);

    /** Insert at the tail; throws on capacity overflow (loud, never a silent drop). */
    void addLast(E e);

    /** Insert at the head if capacity permits; returns false if it can't (true within capacity). */
    boolean offerFirst(E e);

    /** Insert at the tail if capacity permits; returns false if it can't (true within capacity). */
    boolean offerLast(E e);

    // --- head/tail removal -----------------------------------------------------------------------

    /** Remove and return the head; throws {@link NoSuchElementException} when empty. */
    E removeFirst();

    /** Remove and return the tail; throws {@link NoSuchElementException} when empty. */
    E removeLast();

    /** Remove and return the head, or {@code null} when empty. */
    E pollFirst();

    /** Remove and return the tail, or {@code null} when empty. */
    E pollLast();

    // --- head/tail peek --------------------------------------------------------------------------

    /** Retrieve the head without removing; throws {@link NoSuchElementException} when empty. */
    E getFirst();

    /** Retrieve the tail without removing; throws {@link NoSuchElementException} when empty. */
    E getLast();

    /** Retrieve the head without removing, or {@code null} when empty. */
    E peekFirst();

    /** Retrieve the tail without removing, or {@code null} when empty. */
    E peekLast();

    // --- Stack aliases (LIFO over the head) ------------------------------------------------------

    /** Stack push: insert at the head (equivalent to {@link #addFirst}). */
    void push(E e);

    /** Stack pop: remove and return the head; throws {@link NoSuchElementException} when empty. */
    E pop();

    // --- occurrence removal ----------------------------------------------------------------------

    /** Remove the first element equal to {@code o} (head→tail); true if one was removed. */
    boolean removeFirstOccurrence(Object o);

    /** Remove the last element equal to {@code o} (tail→head); true if one was removed. */
    boolean removeLastOccurrence(Object o);

    // --- descending iteration --------------------------------------------------------------------

    /** An iterator over the elements from tail to head (the reverse of {@link #iterator}). */
    Iterator<E> descendingIterator();
}
