package java.util.concurrent;

import java.util.Queue;

/**
 * BMC model of {@link java.util.concurrent.BlockingQueue}. bmc4j proves logic, not interleavings
 * (Lincheck's job), so only the FIFO data semantics are modeled, not the blocking handoff between
 * threads. The <b>non-blocking surface</b> ({@code offer}/{@code poll}/{@code peek} + the throwing
 * {@code add}/{@code remove}/{@code element} from {@link Queue}) is sound over the bounded array model.
 *
 * <p><b>Blocking idealization (assume-prune):</b> {@code put}/{@code take} are idealized the standard
 * BMC way — the thread "waits here until it can proceed". {@code take} <em>assumes</em> the queue is
 * non-empty then dequeues; {@code put} <em>assumes</em> there is room (within capacity) then
 * enqueues. The would-block path (empty / full) is pruned from the analysis, so producer/consumer
 * <em>logic</em> through the queue stays testable and sound (a path that could only block is pruned,
 * not silently passed). The impls drive this with the JBMC {@code CProver.assume} primitive, so
 * {@code put}/{@code take} live on the proof axis only; for non-blocking probing use {@code offer}/
 * {@code poll}, which are pure and differential-tested.
 */
public interface BlockingQueue<E> extends Queue<E> {

    /** Enqueue, blocking if full; idealized as assume-room-then-enqueue (would-block path pruned) — see javadoc. */
    void put(E e) throws InterruptedException;

    /** Dequeue, blocking if empty; idealized as assume-non-empty-then-dequeue (would-block path pruned) — see javadoc. */
    E take() throws InterruptedException;

    /** Spare capacity before {@code offer} would fail. */
    int remainingCapacity();
}
