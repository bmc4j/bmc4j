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

    /** value {@code >= n} — for PRIMITIVE numerics only; boxed fields take {@link #minNullable}. */
    public static Constraint min(long n) {
        return accessor -> accessor + " >= " + n;
    }

    /** value {@code <= n} — for PRIMITIVE numerics only; boxed fields take {@link #maxNullable}. */
    public static Constraint max(long n) {
        return accessor -> accessor + " <= " + n;
    }

    /**
     * value {@code >= n} with jakarta null semantics for BOXED/reference numerics: {@code null}
     * PASSES (only {@code @NotNull} rejects null). The guard is load-bearing for soundness — an
     * unguarded compare on a boxed field either NPEs inside the generated assume or silently
     * EXCLUDES valid-null objects from the proof domain, so a proof claiming "holds for every
     * valid object" never explored the valid-null ones.
     */
    public static Constraint minNullable(long n) {
        return accessor -> "(" + accessor + " == null || " + accessor + " >= " + n + ")";
    }

    /** value {@code <= n} with null-pass semantics for boxed numerics; see {@link #minNullable}. */
    public static Constraint maxNullable(long n) {
        return accessor -> "(" + accessor + " == null || " + accessor + " <= " + n + ")";
    }

    /** value == null (jakarta {@code @Null}). */
    public static Constraint isNull() {
        return accessor -> accessor + " == null";
    }

    /** primitive {@code boolean} is true (jakarta {@code @AssertTrue}). */
    public static Constraint isTrue() {
        return accessor -> accessor;
    }

    /** primitive {@code boolean} is false (jakarta {@code @AssertFalse}). */
    public static Constraint isFalse() {
        return accessor -> "!" + accessor;
    }

    /** boxed {@code Boolean} is true, null passes (jakarta {@code @AssertTrue} semantics). */
    public static Constraint isTrueNullable() {
        return accessor -> "(" + accessor + " == null || " + accessor + ")";
    }

    /** boxed {@code Boolean} is false, null passes (jakarta {@code @AssertFalse} semantics). */
    public static Constraint isFalseNullable() {
        return accessor -> "(" + accessor + " == null || !" + accessor + ")";
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
