package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.LinkedHashSet} — same array-backed behaviour as {@link HashSet}.
 * The backing array preserves insertion order, so the {@link java.util.SequencedSet} surface is
 * modeled soundly here: {@code getFirst}/{@code getLast} read the ends of the insertion order,
 * {@code addFirst}/{@code addLast} (re)position an element at an end exactly like the JDK (a present
 * element is moved), and {@code removeFirst}/{@code removeLast} read-and-remove the ends (throwing
 * {@link NoSuchElementException} when empty). The presizing {@code newLinkedHashSet} factory is a
 * fresh empty set (the capacity hint is observably irrelevant); the {@code reversed()} live
 * SequencedSet view is a loud {@link BmcUnmodelable} (a write-through reordering view this
 * insertion-ordered array can't represent). The spliterator parallel-decomposition view,
 * {@code toArray(IntFunction)}, and the {@code newHashSet} factory are inherited from the
 * {@link HashSet} model.
 */
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

    // --- presizing factory (Java 19+) ----------------------------------------
    // newLinkedHashSet(numElements) returns an EMPTY set sized to hold numElements without resizing.
    // The capacity hint is observably irrelevant to this fixed-capacity bounded model, so it is exactly
    // a fresh empty set.

    @BmcModelConforms("differential (SetConformanceTest) + @BmcProof (proofs.linkedhashset)")
    public static <T> LinkedHashSet<T> newLinkedHashSet(int numElements) {
        if (numElements < 0) {
            throw new IllegalArgumentException("Negative number of elements: " + numElements);
        }
        return new LinkedHashSet<>();
    }

    // --- explicitly UNMODELLED members (loud stubs; decision + reason live here) ------------------

    @BmcUnmodelable(reason = "reversed() is a live SequencedSet view whose write-through reorders the insertion order — this insertion-ordered array model has no reversed structure to honor that; loud under JBMC")
    public java.util.SequencedSet<E> reversed() {
        throw fail("bmc4j: unmodelled member java.util.LinkedHashSet.reversed() — live SequencedSet reordering view over the insertion-ordered array; out of scope");
    }

    // The per-member auditing gate accounts a subclass's own real surface against its OWN method-level
    // stubs, so the loud stubs the HashSet model declares for these members are re-declared here for the
    // LinkedHashSet surface. Same decisions + reasons as the HashSet model.

    @BmcUnmodelable(reason = "typed array snapshot — iterate the model instead")
    public <T> T[] toArray(T[] a) {
        throw fail("bmc4j: unmodelled member java.util.LinkedHashSet.toArray(java.lang.Object[]) — typed array snapshot — iterate the model instead");
    }

    @BmcUnmodelable(reason = "array snapshot via a reflective IntFunction generator (creates a T[] of a reflective component type) — iterate the model instead")
    public <T> T[] toArray(java.util.function.IntFunction<T[]> generator) {
        throw fail("bmc4j: unmodelled member java.util.LinkedHashSet.toArray(java.util.function.IntFunction) — array snapshot via a reflective generator — iterate the model instead");
    }

    @BmcUnmodelable(reason = "parallel-decomposition Spliterator (a tryAdvance/trySplit traversal view a sequential bounded model can't represent) — iterate the model instead")
    public java.util.Spliterator<E> spliterator() {
        throw fail("bmc4j: unmodelled member java.util.LinkedHashSet.spliterator() — parallel-decomposition Spliterator view — iterate the model instead");
    }

    @BmcUnmodelable(reason = "shallow copy of a bounded model — construct a fresh set from the elements instead")
    public Object clone() {
        throw fail("bmc4j: unmodelled member java.util.LinkedHashSet.clone() — shallow copy of a bounded model — construct a fresh set from the elements instead");
    }
}
