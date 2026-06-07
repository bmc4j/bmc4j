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
@BmcModelTail(reason = "array-snapshot/parallel-stream views (toArray/toArray(IntFunction)/parallelStream/spliterator) and bounded drainTo(Collection,int) — out of scope for the bounded FIFO model; all loud under JBMC")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public int size() {
        return size;
    }

    @Override
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public int remainingCapacity() {
        return capacity - size;
    }

    /** Enqueue if room; returns false when full. NPE on null per the JDK. */
    @Override
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean add(E e) {
        if (!offer(e)) {
            throw new IllegalStateException("Queue full");
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public E remove() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return poll();
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public E peek() {
        return size == 0 ? null : (E) elements[0];
    }

    @Override
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public E take() throws InterruptedException {
        CProver.assume(size > 0);
        return poll();
    }

    @Override
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public void clear() {
        size = 0;
    }

    @Override
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public Iterator<E> iterator() {
        return new Itr();
    }

    /** A sequential stream over the queued elements in FIFO order — a thin ListStream adapter. */
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public java.util.stream.Stream<E> stream() {
        java.util.ArrayList<E> snapshot = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            snapshot.add((E) elements[i]);
        }
        return new java.util.stream.ListStream<>(snapshot);
    }

    /** Drain all elements into {@code c} (in FIFO order) and return the count moved. */
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public int drainTo(Collection<? super E> c) {
        int n = size;
        while (size > 0) {
            c.add(poll());
        }
        return n;
    }

    // --- functional / bulk ops over the bounded FIFO backing array ------------------------------
    // forEach reads in FIFO order; removeIf/removeAll/retainAll compact in place (preserving FIFO);
    // addAll enqueues via add() so it honors the logical capacity exactly (IllegalStateException when
    // full, like the JDK). Functional arguments are plain SAM calls (bmc4j desugars the lambda so JBMC
    // devirtualizes test/accept).

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public void forEach(java.util.function.Consumer<? super E> action) {
        for (int i = 0; i < size; i++) {
            action.accept((E) elements[i]);
        }
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        int w = 0;
        boolean changed = false;
        for (int r = 0; r < size; r++) {
            E e = (E) elements[r];
            if (filter.test(e)) {
                changed = true;
            } else {
                elements[w++] = e;
            }
        }
        size = w;
        return changed;
    }

    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            add(e);             // honors logical capacity (IllegalStateException when full), like the JDK
            changed = true;
        }
        return changed;
    }

    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean removeAll(Collection<?> c) {
        return removeWhere(c, true);
    }

    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean retainAll(Collection<?> c) {
        return removeWhere(c, false);
    }

    /** Compact in place (preserving FIFO), dropping elements whose membership in {@code c} equals {@code removeMatched}. */
    private boolean removeWhere(Collection<?> c, boolean removeMatched) {
        int w = 0;
        boolean changed = false;
        for (int r = 0; r < size; r++) {
            Object e = elements[r];
            if (c.contains(e) == removeMatched) {
                changed = true;     // dropped
            } else {
                elements[w++] = e;  // kept
            }
        }
        size = w;
        return changed;
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ----------------

    @BmcNotNeeded(reason = "bulk membership — compose contains() explicitly")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean containsAll(Collection<?> c) {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.containsAll(java.util.Collection) — bulk membership — compose contains() explicitly");
    }

    @BmcNotNeeded(reason = "timed offer — timeout is a scheduling concern; use offer()/put() assume-prune")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        throw fail("bmc4j: unmodelled member java.util.concurrent.ArrayBlockingQueue.offer(java.lang.Object,long,java.util.concurrent.TimeUnit) — timed offer — timeout is a scheduling concern; use offer()/put() assume-prune");
    }

    @BmcNotNeeded(reason = "timed poll — timeout is a scheduling concern; use poll()/take() assume-prune")
    @BmcModelConforms("differential (non-blocking surface) + @BmcProof (put/take assume-prune)")
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
