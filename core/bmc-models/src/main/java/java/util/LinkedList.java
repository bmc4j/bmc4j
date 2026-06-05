package java.util;

/**
 * BMC model of {@link java.util.LinkedList} as an array-backed list — behaviourally equivalent for
 * proofs (the linked structure doesn't affect functional results, only performance).
 */
public class LinkedList<E> extends ArrayList<E> {

    public LinkedList() {
        super();
    }

    public LinkedList(Collection<? extends E> c) {
        super(c);
    }
}
