package java.util;

/**
 * Minimal BMC model of {@link java.util.Enumeration} — the legacy two-method iteration protocol
 * ({@code hasMoreElements()}/{@code nextElement()}). Modeled as a plain interface; concrete bounded
 * enumerations (e.g. {@link Collections#enumeration(Collection)}) iterate by index over a snapshot of a
 * modeled collection's backing, the robust concrete-backing pattern (no virtual dispatch through an
 * interface). The {@code asIterator()} default bridge is left to JBMC's stub — code that reaches an
 * Enumeration uses the two-method surface.
 */
public interface Enumeration<E> {
    boolean hasMoreElements();

    E nextElement();
}
