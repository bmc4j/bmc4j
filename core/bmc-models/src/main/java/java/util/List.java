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

    E remove(int index);

    void clear();

    Iterator<E> iterator();

    java.util.stream.Stream<E> stream();

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
