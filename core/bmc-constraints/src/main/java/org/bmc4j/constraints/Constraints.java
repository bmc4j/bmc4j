package org.bmc4j.constraints;

/**
 * Factories for the {@link Constraint}s that map cleanly onto JBMC-analyzable
 * expressions. Library-specific extractors (e.g. the Jakarta one) compose these.
 */
public final class Constraints {

    private Constraints() {
    }

    /** value != null */
    public static Constraint notNull() {
        return accessor -> accessor + " != null";
    }

    /** value {@code >= n} */
    public static Constraint min(long n) {
        return accessor -> accessor + " >= " + n;
    }

    /** value {@code <= n} */
    public static Constraint max(long n) {
        return accessor -> accessor + " <= " + n;
    }

    /**
     * Lower bound on a size/length. {@code sizeAccessor} is the suffix that reads the
     * size, e.g. {@code ".length()"} (String), {@code ".length"} (array),
     * {@code ".size()"} (collection). Null is permitted (Jakarta {@code @Size} allows null).
     */
    public static Constraint sizeAtLeast(String sizeAccessor, int n) {
        return accessor -> "(" + accessor + " == null || " + accessor + sizeAccessor + " >= " + n + ")";
    }

    /** Upper bound on a size/length; see {@link #sizeAtLeast}. */
    public static Constraint sizeAtMost(String sizeAccessor, int n) {
        return accessor -> "(" + accessor + " == null || " + accessor + sizeAccessor + " <= " + n + ")";
    }
}
