package java.lang;

/** Minimal BMC model of {@link java.lang.Iterable} — the {@code iterator()} that for-each and the
 *  Kotlin collection extensions iterate through. */
public interface Iterable<T> {
    java.util.Iterator<T> iterator();
}
