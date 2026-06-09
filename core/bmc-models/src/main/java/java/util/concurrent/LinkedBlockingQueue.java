package java.util.concurrent;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.Collection;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

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
 *
 * <p>The FIFO-snapshot {@code toArray}/{@code toArray(T[])}/{@code toArray(IntFunction)} and the bulk
 * {@code containsAll} are inherited, modeled by the {@link ArrayBlockingQueue} backing. The
 * parallel-decomposition surface ({@code spliterator}/{@code parallelStream}) is the concurrency wall —
 * re-declared here as loud waivers because a class-level decision on the superclass does not propagate
 * to the subclass surface.
 */
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

    /**
     * Bulk membership — the inherited {@link ArrayBlockingQueue} model (a bounded loop of
     * {@link #contains(Object)}). Overridden only to make the inherited-model decision explicit on this
     * subclass surface (its semantics are exactly {@code super.containsAll(c)}).
     */
    @Override
    @BmcModelConforms("differential (non-blocking surface): bulk membership via the inherited ArrayBlockingQueue model")
    public boolean containsAll(Collection<?> c) {
        return super.containsAll(c);
    }

    /** Parallel-decomposition primitive — true-parallel split is the concurrency wall; use iterator()/stream(). */
    @BmcUnmodelable(reason = "spliterator's parallel split / true-parallel decomposition is the concurrency wall — use the sequential iterator()/stream() instead")
    public java.util.Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.LinkedBlockingQueue.spliterator() — spliterator's parallel split / true-parallel decomposition is the concurrency wall — use the sequential iterator()/stream() instead");
    }

    /** Parallel stream — true-parallel execution is the concurrency wall; use the sequential stream(). */
    @BmcUnmodelable(reason = "parallelStream's true-parallel execution is the concurrency wall — use the sequential stream() instead")
    public java.util.stream.Stream<E> parallelStream() {
        throw fail("bmc4j: unmodelled member java.util.concurrent.LinkedBlockingQueue.parallelStream() — parallelStream's true-parallel execution is the concurrency wall — use the sequential stream() instead");
    }
}
