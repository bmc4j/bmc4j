package java.util;

/** Minimal BMC model of {@link java.util.Iterator} — just the two methods for-each desugars to. */
public interface Iterator<E> {
    boolean hasNext();

    E next();
}
