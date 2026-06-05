package java.util;

/** BMC model of {@link java.util.LinkedHashSet} — same array-backed behaviour as {@link HashSet}. */
public class LinkedHashSet<E> extends HashSet<E> {

    public LinkedHashSet() {
        super();
    }

    public LinkedHashSet(Collection<? extends E> c) {
        super(c);
    }
}
