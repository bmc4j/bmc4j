package java.util;

/**
 * Minimal BMC model of {@link java.util.List} — a flattened interface (no Collection/Iterable
 * hierarchy) declaring the members our {@link ArrayList} model supports, so {@code invokeinterface
 * List.get/add/iterator/...} in analysed code resolves to a sound body. Members the real interface
 * has but this omits fall back to JBMC's nondet stub (the usual partial-model caveat).
 */
public interface List<E> extends Collection<E> {
    int size();

    boolean isEmpty();

    boolean add(E e);

    E get(int index);

    E set(int index, E element);

    boolean contains(Object o);

    int indexOf(Object o);

    int lastIndexOf(Object o);

    E remove(int index);

    // SequencedCollection (Java 21+) head/tail ops — modeled over the backing array.

    E getFirst();

    E getLast();

    void addFirst(E e);

    void addLast(E e);

    E removeFirst();

    E removeLast();

    void clear();

    Iterator<E> iterator();

    java.util.stream.Stream<E> stream();

    /** Append every element of {@code c} (in iteration order); true if anything was added. */
    boolean addAll(Collection<? extends E> c);

    /** Remove every element also contained in {@code c}; true if this list changed. */
    boolean removeAll(Collection<?> c);

    /** Retain only the elements contained in {@code c}; true if this list changed. */
    boolean retainAll(Collection<?> c);

    /** Remove every element matching {@code filter}; true if any were removed. */
    boolean removeIf(java.util.function.Predicate<? super E> filter);

    /** Apply {@code action} to each element, in index order. */
    void forEach(java.util.function.Consumer<? super E> action);

    /** A new array holding every element, in index order. */
    Object[] toArray();

    /** A bounded snapshot of the elements in {@code [fromIndex, toIndex)} (see the ArrayList model). */
    List<E> subList(int fromIndex, int toIndex);

    /** A bounded snapshot of the elements in reverse order (SequencedCollection, Java 21+). */
    List<E> reversed();

    // Immutable factories (java.util.List.of) — javac emits the fixed-arity overload for small
    // counts and the varargs one beyond. We return a (mutable) ArrayList; proofs only read it.

    static <E> List<E> of() {
        return new ArrayList<>();
    }

    static <E> List<E> of(E e1) {
        ArrayList<E> l = new ArrayList<>();
        l.add(e1);
        return l;
    }

    static <E> List<E> of(E e1, E e2) {
        ArrayList<E> l = new ArrayList<>();
        l.add(e1);
        l.add(e2);
        return l;
    }

    static <E> List<E> of(E e1, E e2, E e3) {
        ArrayList<E> l = new ArrayList<>();
        l.add(e1);
        l.add(e2);
        l.add(e3);
        return l;
    }

    static <E> List<E> of(E e1, E e2, E e3, E e4) {
        ArrayList<E> l = new ArrayList<>();
        l.add(e1);
        l.add(e2);
        l.add(e3);
        l.add(e4);
        return l;
    }

    @SafeVarargs
    static <E> List<E> of(E... elements) {
        ArrayList<E> l = new ArrayList<>();
        for (E e : elements) {
            l.add(e);
        }
        return l;
    }
}
