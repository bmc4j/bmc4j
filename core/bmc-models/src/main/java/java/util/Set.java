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

    /** Add every distinct element of {@code c} (dedup via the set); true if anything was added. */
    boolean addAll(Collection<? extends E> c);

    /** Remove every element also contained in {@code c}; true if this set changed. */
    boolean removeAll(Collection<?> c);

    /** Retain only the elements contained in {@code c}; true if this set changed. */
    boolean retainAll(Collection<?> c);

    /** Remove every element matching {@code filter}; true if any were removed. */
    boolean removeIf(java.util.function.Predicate<? super E> filter);

    /** Apply {@code action} to each element, in insertion order. */
    void forEach(java.util.function.Consumer<? super E> action);

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
