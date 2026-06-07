package java.util;

/** Minimal BMC model of {@link java.util.Set} — flattened interface for our {@link HashSet} model. */
public interface Set<E> extends Collection<E> {
    int size();

    boolean isEmpty();

    boolean add(E e);

    boolean contains(Object o);

    boolean remove(Object o);

    void clear();

    Iterator<E> iterator();

    /** A sequential stream over the set's elements. */
    java.util.stream.Stream<E> stream();

    // Immutable factories (java.util.Set.of).

    static <E> Set<E> of() {
        return new HashSet<>();
    }

    static <E> Set<E> of(E e1) {
        HashSet<E> s = new HashSet<>();
        s.add(e1);
        return s;
    }

    static <E> Set<E> of(E e1, E e2) {
        HashSet<E> s = new HashSet<>();
        s.add(e1);
        s.add(e2);
        return s;
    }

    static <E> Set<E> of(E e1, E e2, E e3) {
        HashSet<E> s = new HashSet<>();
        s.add(e1);
        s.add(e2);
        s.add(e3);
        return s;
    }

    static <E> Set<E> of(E e1, E e2, E e3, E e4) {
        HashSet<E> s = new HashSet<>();
        s.add(e1);
        s.add(e2);
        s.add(e3);
        s.add(e4);
        return s;
    }

    @SafeVarargs
    static <E> Set<E> of(E... elements) {
        HashSet<E> s = new HashSet<>();
        for (E e : elements) {
            s.add(e);
        }
        return s;
    }
}
