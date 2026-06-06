package org.bmc4j.constraints

/**
 * Factories for the [Constraint]s that map cleanly onto JBMC-analyzable
 * expressions. Library-specific extractors (e.g. the Jakarta one) compose these.
 * `@JvmStatic` throughout: extractors are a public extension point ([ConstraintExtractor]),
 * so Java implementations keep calling `Constraints.min(0)` unchanged.
 */
object Constraints {

    /** value != null */
    @JvmStatic
    fun notNull(): Constraint = Constraint { accessor -> "$accessor != null" }

    /** value `>= n` — for PRIMITIVE numerics only; boxed fields take [minNullable]. */
    @JvmStatic
    fun min(n: Long): Constraint = Constraint { accessor -> "$accessor >= $n" }

    /** value `<= n` — for PRIMITIVE numerics only; boxed fields take [maxNullable]. */
    @JvmStatic
    fun max(n: Long): Constraint = Constraint { accessor -> "$accessor <= $n" }

    /**
     * value `>= n` with jakarta null semantics for BOXED/reference numerics: `null`
     * PASSES (only `@NotNull` rejects null). The guard is load-bearing for soundness — an
     * unguarded compare on a boxed field either NPEs inside the generated assume or silently
     * EXCLUDES valid-null objects from the proof domain, so a proof claiming "holds for every
     * valid object" never explored the valid-null ones.
     */
    @JvmStatic
    fun minNullable(n: Long): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor >= $n)" }

    /** value `<= n` with null-pass semantics for boxed numerics; see [minNullable]. */
    @JvmStatic
    fun maxNullable(n: Long): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor <= $n)" }

    /** value == null (jakarta `@Null`). */
    @JvmStatic
    fun isNull(): Constraint = Constraint { accessor -> "$accessor == null" }

    /** primitive `boolean` is true (jakarta `@AssertTrue`). */
    @JvmStatic
    fun isTrue(): Constraint = Constraint { accessor -> accessor }

    /** primitive `boolean` is false (jakarta `@AssertFalse`). */
    @JvmStatic
    fun isFalse(): Constraint = Constraint { accessor -> "!$accessor" }

    /** boxed `Boolean` is true, null passes (jakarta `@AssertTrue` semantics). */
    @JvmStatic
    fun isTrueNullable(): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor)" }

    /** boxed `Boolean` is false, null passes (jakarta `@AssertFalse` semantics). */
    @JvmStatic
    fun isFalseNullable(): Constraint =
            Constraint { accessor -> "($accessor == null || !$accessor)" }

    /**
     * Lower bound on a size/length. [sizeAccessor] is the suffix that reads the
     * size, e.g. `".length()"` (String), `".length"` (array),
     * `".size()"` (collection). Null is permitted (Jakarta `@Size` allows null).
     */
    @JvmStatic
    fun sizeAtLeast(sizeAccessor: String, n: Int): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor$sizeAccessor >= $n)" }

    /** Upper bound on a size/length; see [sizeAtLeast]. */
    @JvmStatic
    fun sizeAtMost(sizeAccessor: String, n: Int): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor$sizeAccessor <= $n)" }
}
