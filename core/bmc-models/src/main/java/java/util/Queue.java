package java.util;

/**
 * Minimal BMC model of {@link java.util.Queue}, sitting between {@link Collection} and the concurrent
 * queue models. Declares the non-blocking FIFO surface ({@code offer}/{@code poll}/{@code peek} and
 * the throwing {@code add}/{@code remove}/{@code element}) so code typed against {@code Queue}
 * devirtualizes onto a concrete model. Concurrency/blocking is not modeled here (see
 * {@link java.util.concurrent.BlockingQueue}).
 */
public interface Queue<E> extends Collection<E> {

    /** Insert if possible without violating capacity; returns false if full. */
    boolean offer(E e);

    /** Retrieve and remove the head, or return null if empty. */
    E poll();

    /** Retrieve but do not remove the head, or return null if empty. */
    E peek();

    /** Retrieve and remove the head; throws NoSuchElementException if empty. */
    E remove();

    /** Retrieve but do not remove the head; throws NoSuchElementException if empty. */
    E element();
}
