package java.util;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * BMC model of {@link java.util.LinkedHashSet} — same array-backed behaviour as {@link HashSet}.
 * The backing array preserves insertion order, so the {@link java.util.SequencedSet} surface is
 * modeled soundly here: {@code getFirst}/{@code getLast} read the ends of the insertion order,
 * {@code addFirst}/{@code addLast} (re)position an element at an end exactly like the JDK (a present
 * element is moved), and {@code removeFirst}/{@code removeLast} read-and-remove the ends (throwing
 * {@link NoSuchElementException} when empty). {@code reversed()} and the {@code newLinkedHashSet}
 * presizing factory are modeled; the parallel-decomposition {@code spliterator()} and the typed-array
 * snapshots ({@code toArray(T[])}/{@code toArray(IntFunction)}) inherit {@link HashSet}'s per-member
 * loud {@link org.bmc4j.models.audit.BmcUnmodelable} stubs (resolved up the modeled superclass chain).
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

    /**
     * Presizing factory ({@code LinkedHashSet.newLinkedHashSet(numElements)}, Java 19+) — capacity is a
     * hint only, so the model returns a fresh empty set. Negative throws IllegalArgumentException, like
     * the JDK. (The inherited {@code newHashSet} is covered by the HashSet model.)
     */
    @BmcModelConforms("differential (SetConformanceTest): newLinkedHashSet(int) presizing factory -> empty set")
    public static <T> LinkedHashSet<T> newLinkedHashSet(int numElements) {
        if (numElements < 0) {
            throw new IllegalArgumentException("Negative number of elements: " + numElements);
        }
        return new LinkedHashSet<>();
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

    /**
     * A bounded snapshot of the elements in reverse insertion order (SequencedSet, Java 21+). The JDK
     * returns a live view; the model returns an independent {@code LinkedHashSet} populated by reading
     * the backing in reverse (index {@code size-1} → 0). Sound for read-only / build-then-read proofs;
     * built by index over the concrete backing, no iterator() virtual dispatch.
     */
    @BmcModelConforms("differential (SetConformanceTest): reversed() bounded snapshot in reverse insertion order")
    public Set<E> reversed() {
        LinkedHashSet<E> out = new LinkedHashSet<>();
        for (int i = size() - 1; i >= 0; i--) {
            out.add(elementAt(i));
        }
        return out;
    }
}
