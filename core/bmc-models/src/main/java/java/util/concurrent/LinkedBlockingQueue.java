package java.util.concurrent;

/**
 * Sequential BMC model of {@link java.util.concurrent.LinkedBlockingQueue} — functionally the same
 * bounded-FIFO model as {@link ArrayBlockingQueue} (bmc4j proves logic, not interleavings). The only
 * difference from the JDK is that the real LinkedBlockingQueue defaults to {@code Integer.MAX_VALUE}
 * capacity; here the default is the model bound {@value ArrayBlockingQueue#MAX_CAPACITY}. The
 * non-blocking surface ({@code offer}/{@code poll}/{@code peek}/{@code add}/{@code remove}/
 * {@code element}/{@code size}) is sound; {@code put}/{@code take} carry the same assume-prune
 * blocking idealization as {@link ArrayBlockingQueue} — see its javadoc.
 */
public class LinkedBlockingQueue<E> extends ArrayBlockingQueue<E> {

    /** Unbounded in the JDK; bounded by the model capacity here. */
    public LinkedBlockingQueue() {
        super(MAX_CAPACITY, false, true);
    }

    public LinkedBlockingQueue(int capacity) {
        super(capacity, false, true);
    }
}
