package java.util.concurrent;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Sequential BMC model of {@link java.util.concurrent.LinkedBlockingQueue} — functionally the same
 * FIFO model as {@link ArrayBlockingQueue} (bmc4j proves logic, not interleavings). The default
 * constructor matches the JDK's <em>logical</em> contract exactly: capacity
 * {@code Integer.MAX_VALUE}, so {@code offer} never returns false and {@code add} never throws —
 * the model must never admit a rejection the real unbounded queue cannot produce (that admitted
 * branch was a silent-false-green vector). The model can only <em>store</em>
 * {@value ArrayBlockingQueue#MAX_CAPACITY} elements: exceeding that is a loud out-of-bounds at the
 * store (the documented model-bound signal, like every array-backed model) — never a silent false
 * rejection. The non-blocking surface ({@code offer}/{@code poll}/{@code peek}/{@code add}/
 * {@code remove}/{@code element}/{@code size}) is sound; {@code put}/{@code take} carry the same
 * assume-prune blocking idealization as {@link ArrayBlockingQueue} — see its javadoc.
 */
@BmcModelConforms("inherits the ArrayBlockingQueue FIFO model (incl. stream() and forEach/removeIf/addAll/removeAll/retainAll); unbounded-by-default logical capacity")
@BmcModelTail(reason = "array-snapshot/parallel-stream views, timed ops and bounded drainTo not inherited from the ArrayBlockingQueue model — out of scope for the FIFO model; all loud under JBMC")
public class LinkedBlockingQueue<E> extends ArrayBlockingQueue<E> {

    /**
     * Unbounded, exactly like the JDK default: the logical capacity is {@code Integer.MAX_VALUE},
     * so the rejection paths are unreachable. Storage stays bounded by the model bound — see the
     * class javadoc.
     */
    public LinkedBlockingQueue() {
        super(Integer.MAX_VALUE, false, true);
    }

    public LinkedBlockingQueue(int capacity) {
        super(capacity, false, true);
    }
}
