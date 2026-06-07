package java.util;

/**
 * Minimal BMC model of {@link java.util.Collection}, sitting between {@link java.lang.Iterable} and
 * {@link List}/{@link Set}. Needed because desugared code (e.g. Kotlin's inlined {@code map}) casts
 * its destination to {@code Collection} and calls {@code add}. Members our {@link ArrayList}/
 * {@link HashSet} models already implement; omitted ones stay JBMC stubs.
 *
 * <p>{@code remove(Object)} lives here (as in the JDK), so it resolves for both {@link List} and
 * {@link Set} consumers; {@link List#remove(int)} is the separate by-index overload.
 */
public interface Collection<E> extends java.lang.Iterable<E> {
    int size();

    boolean isEmpty();

    boolean contains(Object o);

    boolean add(E e);

    boolean remove(Object o);

    void clear();

    Iterator<E> iterator();
}
