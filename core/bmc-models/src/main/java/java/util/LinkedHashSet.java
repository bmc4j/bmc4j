package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.LinkedHashSet} — same array-backed behaviour as {@link HashSet}.
 * The backing array preserves insertion order, so the {@link java.util.SequencedSet} surface is
 * modeled soundly here: {@code getFirst}/{@code getLast} read the ends of the insertion order,
 * {@code addFirst}/{@code addLast} (re)position an element at an end exactly like the JDK (a present
 * element is moved), and {@code removeFirst}/{@code removeLast} read-and-remove the ends (throwing
 * {@link NoSuchElementException} when empty). The {@code reversed()} live view and the spliterator
 * parallel-decomposition view stay loud (tail).
 */
@BmcModelTail(reason = "the reversed() live view, the spliterator parallel-decomposition view, the newHashSet/newLinkedHashSet presizing factories, and toArray(IntFunction) — out of scope for this insertion-ordered array-backed model; all loud under JBMC")
public class LinkedHashSet<E> extends HashSet<E> {

    public LinkedHashSet() {
        super();
    }

    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity);
    }

    public LinkedHashSet(Collection<? extends E> c) {
        super(c);
    }

    // --- SequencedSet: ends of the insertion order -------------------------------------------------

    /** Add {@code e} at the front; a present element is moved there (JDK SequencedSet semantics). */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public void addFirst(E e) {
        addAtFront(e);
    }

    /** Add {@code e} at the back; a present element is moved there (JDK SequencedSet semantics). */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public void addLast(E e) {
        addAtBack(e);
    }

    /** The first-inserted element; throws {@link NoSuchElementException} when empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public E getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return elementAt(0);
    }

    /** The last-inserted element; throws {@link NoSuchElementException} when empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public E getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return elementAt(size() - 1);
    }

    /** Remove and return the first-inserted element; throws {@link NoSuchElementException} when empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public E removeFirst() {
        return removeAtFront();
    }

    /** Remove and return the last-inserted element; throws {@link NoSuchElementException} when empty. */
    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public E removeLast() {
        return removeAtBack();
    }
}
