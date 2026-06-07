package java.util.concurrent;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.cprover.CProver;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * Sequential BMC model of {@link java.util.concurrent.ArrayBlockingQueue} — a bounded FIFO over a
 * fixed-size backing array (head at index 0, tail at {@code size}; {@code poll}/{@code take} shift
 * left, like the bmc4j ArrayList model). bmc4j proves logic, not interleavings (Lincheck's job).
 *
 * <p>The <b>non-blocking surface</b> is sound: {@code offer} returns false when full, {@code poll}/
 * {@code peek} return null when empty, {@code add}/{@code remove}/{@code element} throw per the
 * {@link java.util.Queue} contract. The <b>blocking</b> {@code put}/{@code take} are idealized as
 * assume-prune (see {@link BlockingQueue}): {@code put} assumes room (within capacity) then enqueues,
 * {@code take} assumes non-empty then dequeues — the would-block path (full / empty) is pruned from
 * the analysis, so producer/consumer <em>logic</em> through the queue stays testable and sound.
 *
 * <p><b>Axis note:</b> {@code put}/{@code take} use the {@link CProver#assume} prune primitive
 * (JBMC-only), so they are exercised on the {@code @BmcProof} axis only, never on the JVM-runnable
 * differential axis (the non-blocking surface is what is differential-tested vs the JDK).
 *
 * <p><b>Logical capacity vs storage bound.</b> {@code capacity} is the constructor argument,
 * honored exactly as the JDK contract ({@code offer} returns false / {@code add} throws at
 * {@code size == capacity}; {@code remainingCapacity() == capacity - size}) — it is NOT silently
 * capped, because a smaller-than-real capacity would admit rejection behaviors the real queue
 * cannot produce (a false-green vector). Independently, the model can only <em>store</em>
 * {@value #MAX_CAPACITY} elements: a proof that actually holds more than that trips a loud
 * out-of-bounds at the store — the documented model-bound signal, same as the other array-backed
 * models — never a silent wrong answer.
 */
@BmcModelConforms("bounded array FIFO — differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
@BmcModelTail(reason = "array-snapshot/stream views (toArray/toArray(IntFunction)/stream/parallelStream/spliterator) and bounded drainTo(Collection,int) — out of scope for the bounded FIFO model; all loud under JBMC")
public class ArrayBlockingQueue<E> implements BlockingQueue<E> {

    static final int MAX_CAPACITY = 64;

    private final Object[] elements;
    private final int capacity;
    private int size;

    public ArrayBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        this.elements = new Object[MAX_CAPACITY];
    }

    public ArrayBlockingQueue(int capacity, boolean fair) {
        this(capacity);
    }

    /** Internal ctor used by LinkedBlockingQueue's default (effectively unbounded) capacity. */
    ArrayBlockingQueue(int capacity, boolean fair, boolean internal) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        this.elements = new Object[MAX_CAPACITY];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int remainingCapacity() {
        return capacity - size;
    }

    /** Enqueue if room; returns false when full. NPE on null per the JDK. */
    @Override
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (size >= capacity) {
            return false;
        }
        elements[size] = e;
        size++;
        return true;
    }

    /** Enqueue; throws IllegalStateException("Queue full") when full, per the JDK. */
    @Override
    public boolean add(E e) {
        if (!offer(e)) {
            throw new IllegalStateException("Queue full");
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0) {
            return null;
        }
        E head = (E) elements[0];
        for (int i = 0; i < size - 1; i++) {
            elements[i] = elements[i + 1];
        }
        size--;
        return head;
    }

    @Override
    public E remove() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return poll();
    }

    @Override
    @SuppressWarnings("unchecked")
    public E peek() {
        return size == 0 ? null : (E) elements[0];
    }

    @Override
    public E element() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return peek();
    }

    /**
     * Blocking put, idealized as assume-prune (see class javadoc): assume there is room (within
     * capacity), then enqueue. The would-block "full" path is pruned. Use {@link #offer(Object)} to
     * probe without blocking.
     */
    @Override
    public void put(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        CProver.assume(size < capacity);
        elements[size] = e;
        size++;
    }

    /**
     * Blocking take, idealized as assume-prune (see class javadoc): assume the queue is non-empty,
     * then dequeue. The would-block "empty" path is pruned. Use {@link #poll()} to probe without
     * blocking.
     */
    @Override
    @SuppressWarnings("unchecked")
    public E take() throws InterruptedException {
        CProver.assume(size > 0);
        return poll();
    }

    @Override
    public boolean contains(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a single instance of {@code o} (the first, by {@code equals}) if present, shifting the
     * tail down; returns whether one was removed. The {@code Collection.remove(Object)} overload —
     * distinct from the no-arg head-removing {@link #remove()}.
     */
    @Override
    public boolean remove(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == null ? elements[i] == null : o.equals(elements[i])) {
                for (int j = i; j < size - 1; j++) {
                    elements[j] = elements[j + 1];
                }
                size--;
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    /** Drain all elements into {@code c} (in FIFO order) and return the count moved. */
    public int drainTo(Collection<? super E> c) {
        int n = size;
        while (size > 0) {
            c.add(poll());
        }
        return n;
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ----------------

    @BmcNotModelled(reason = "functional-arg iteration — iterate explicitly")
    public void forEach(java.util.function.Consumer<? super E> action) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.forEach(java.util.function.Consumer) — functional-arg iteration — iterate explicitly");
    }

    @BmcNotModelled(reason = "functional-arg filter — JBMC stubs the predicate dispatch")
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.removeIf(java.util.function.Predicate) — functional-arg filter — JBMC stubs the predicate dispatch");
    }

    @BmcNotNeeded(reason = "bulk add — add elements explicitly over the bounded model")
    public boolean addAll(Collection<? extends E> c) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.addAll(java.util.Collection) — bulk add — add elements explicitly over the bounded model");
    }

    @BmcNotNeeded(reason = "bulk membership — compose contains() explicitly")
    public boolean containsAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.containsAll(java.util.Collection) — bulk membership — compose contains() explicitly");
    }

    @BmcNotNeeded(reason = "bulk remove — compose remove() explicitly")
    public boolean removeAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.removeAll(java.util.Collection) — bulk remove — compose remove() explicitly");
    }

    @BmcNotNeeded(reason = "bulk retain — exotic over a bounded model")
    public boolean retainAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.retainAll(java.util.Collection) — bulk retain — exotic over a bounded model");
    }

    @BmcNotNeeded(reason = "timed offer — timeout is a scheduling concern; use offer()/put() assume-prune")
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.offer(java.lang.Object,long,java.util.concurrent.TimeUnit) — timed offer — timeout is a scheduling concern; use offer()/put() assume-prune");
    }

    @BmcNotNeeded(reason = "timed poll — timeout is a scheduling concern; use poll()/take() assume-prune")
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.poll(long,java.util.concurrent.TimeUnit) — timed poll — timeout is a scheduling concern; use poll()/take() assume-prune");
    }

    private final class Itr implements Iterator<E> {
        private int cursor;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor >= size) {
                throw new NoSuchElementException();
            }
            return (E) elements[cursor++];
        }
    }
}
