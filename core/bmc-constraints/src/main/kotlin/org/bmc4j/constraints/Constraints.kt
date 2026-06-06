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

    // ---- temporal (@Past / @Future family) ---------------------------------
    //
    // Validation-time semantics are "field compares against the clock at the moment of
    // validation". The encoding pins a single symbolic reference moment ([nowVar]) shared by
    // ALL temporal fields of one object — `birthDate` and `signupDate` were both in the past
    // OF THE SAME MOMENT. Distinct nows per field would admit objects no validator accepted.
    // Comparison lowers to the modeled time types' `isBefore`/`isAfter` (epoch-primitive backed,
    // conformance-proven), the SAT-friendly shape. Null PASSES (only `@NotNull` rejects null).

    /** value strictly before [nowVar] (jakarta `@Past`); null passes. */
    @JvmStatic
    fun past(nowVar: String): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor.isBefore($nowVar))" }

    /** value not after [nowVar] (jakarta `@PastOrPresent`); null passes. */
    @JvmStatic
    fun pastOrPresent(nowVar: String): Constraint =
            Constraint { accessor -> "($accessor == null || !$accessor.isAfter($nowVar))" }

    /** value strictly after [nowVar] (jakarta `@Future`); null passes. */
    @JvmStatic
    fun future(nowVar: String): Constraint =
            Constraint { accessor -> "($accessor == null || $accessor.isAfter($nowVar))" }

    /** value not before [nowVar] (jakarta `@FutureOrPresent`); null passes. */
    @JvmStatic
    fun futureOrPresent(nowVar: String): Constraint =
            Constraint { accessor -> "($accessor == null || !$accessor.isBefore($nowVar))" }

    // ---- decimal (@DecimalMin / @DecimalMax / @Digits) ---------------------
    //
    // Lowered against the BigDecimal/BigInteger models (conformance-proven). `compareTo` is a
    // cheap comparison shape and generated assumes PRUNE the search space — not the heavy
    // `setScale` arithmetic. Null PASSES (only `@NotNull` rejects null).

    /**
     * value `>= bound` ([inclusive]) or `> bound` (strict), where [bound] is the decimal literal
     * from `@DecimalMin`; null passes. Lowers to `compareTo(new BigDecimal("<bound>"))`.
     */
    @JvmStatic
    fun decimalMin(bound: String, inclusive: Boolean): Constraint = Constraint { accessor ->
        val op = if (inclusive) ">=" else ">"
        "($accessor == null || $accessor.compareTo(new java.math.BigDecimal(\"$bound\")) $op 0)"
    }

    /** value `<= bound` ([inclusive]) or `< bound` (strict) from `@DecimalMax`; null passes. */
    @JvmStatic
    fun decimalMax(bound: String, inclusive: Boolean): Constraint = Constraint { accessor ->
        val op = if (inclusive) "<=" else "<"
        "($accessor == null || $accessor.compareTo(new java.math.BigDecimal(\"$bound\")) $op 0)"
    }

    /**
     * `@Digits(integer, fraction)`: the integer part has at most [integer] digits AND the fraction
     * part at most [fraction] digits; null passes. The fraction bound is exactly `scale() <= fraction`.
     * The integer bound is the truncated integer part's magnitude staying below `10^integer`
     * (`toBigInteger().abs().compareTo(BigInteger.valueOf(10^integer)) < 0`) — all modeled surface.
     * [integer] is capped at 18 so `10^integer` fits a `long` literal; a larger value is left unbounded.
     */
    @JvmStatic
    fun digits(integer: Int, fraction: Int): Constraint = Constraint { accessor ->
        val parts = mutableListOf<String>()
        if (fraction >= 0) {
            parts.add("$accessor.scale() <= $fraction")
        }
        if (integer in 1..18) {
            val pow = generateSequence(1L) { it * 10 }.elementAt(integer) // 10^integer, integer<=18
            parts.add("$accessor.toBigInteger().abs()" +
                    ".compareTo(java.math.BigInteger.valueOf(${pow}L)) < 0")
        }
        if (parts.isEmpty()) "($accessor == null || true)"
        else "($accessor == null || (${parts.joinToString(" && ")}))"
    }

    // ---- @NotBlank ---------------------------------------------------------
    //
    // Unlike the numeric constraints, `@NotBlank` REJECTS null (jakarta semantics). It requires a
    // non-null CharSequence with at least one non-whitespace char. Two formulations, see issue
    // probe: [notBlankTrim] when `trim()` verifies soundly on the modeled string layer, else
    // [notBlankCharAtLoop] over the natively-sound `length()`/`charAt` primitives.

    /** `@NotBlank` via `trim().isEmpty()`. Null FAILS (no guard — the jakarta asymmetry). */
    @JvmStatic
    fun notBlankTrim(): Constraint =
            Constraint { accessor -> "($accessor != null && !$accessor.trim().isEmpty())" }

    /**
     * `@NotBlank` via the proven-sound `length()`/`charAt` primitives: non-null AND at least one
     * index holds a non-whitespace char (`String.trim` semantics treat a char `> ' '` as non-blank).
     * The existential is a bounded OR over `[0, maxLen)`; [maxLen] mirrors `maxStringLength`'s role
     * as the bound discipline. Null FAILS (no null-guard — that asymmetry is the jakarta semantic).
     */
    @JvmStatic
    fun notBlankCharAtLoop(maxLen: Int): Constraint = Constraint { accessor ->
        val terms = (0 until maxLen).joinToString(" || ") {
            "($it < $accessor.length() && $accessor.charAt($it) > ' ')"
        }
        "($accessor != null && ($terms))"
    }

    // ---- @Valid cascade (statement-emitting) -------------------------------

    /**
     * Cascading `@Valid` on a bean field: null-guarded recursive `assumeValid` on the nested bean's
     * generated constraints class. Null PASSES. Cyclic bean graphs are explored to the proof's
     * unwind depth (JBMC bounds the recursion) — the same depth discipline as everything else.
     *
     * @param nestedConstraintsFqn the nested type's generated helper, e.g. `com.acme.AddressConstraints`
     */
    @JvmStatic
    fun validCascade(nestedConstraintsFqn: String): StatementConstraint = StatementConstraint { accessor ->
        "if ($accessor != null) { $nestedConstraintsFqn.assumeValid($accessor); }"
    }

    /**
     * Container-element numeric/notNull constraints (`List<@Min(1) Integer> scores`): a bounded loop
     * over `[0, bound)` that, for each in-range element, assumes the element constraints. The list and
     * any element may be null (jakarta semantics): the loop is fully null-guarded and the per-element
     * [elementConstraints] are the same null-passing factories used for fields. [bound] is the loop cap
     * (from `@Size(max)` when present, else the default cap). [sizeAccessor] reads the size (`.size()`).
     */
    @JvmStatic
    fun containerElements(sizeAccessor: String, getAccessor: String, bound: Int,
                          elementConstraints: List<Constraint>): StatementConstraint =
            StatementConstraint { accessor ->
                val idx = "__i"
                val elem = "__e"
                val body = StringBuilder()
                body.append("if ($accessor != null) {\n")
                body.append("    for (int $idx = 0; $idx < $bound && $idx < $accessor$sizeAccessor; $idx++) {\n")
                body.append("        var $elem = $accessor$getAccessor($idx);\n")
                for (c in elementConstraints) {
                    body.append("        org.bmc4j.Bmc.assume(").append(c.toExpression(elem)).append(");\n")
                }
                body.append("    }\n")
                body.append("}")
                body.toString()
            }

    /**
     * Container-element `@Valid` cascade (`List<@Valid OrderLine> lines`): a bounded loop that, for each
     * in-range non-null element, recurses into the element type's generated helper. Composes the field
     * cascade ([validCascade]) with the element loop. Null list and null elements pass. Cycles
     * (`List<@Valid Node>` over a self-referential `Node`) are bounded by JBMC's unwind, exactly as the
     * field cascade. See [containerElements] for the [bound] / [sizeAccessor] / [getAccessor] roles.
     */
    @JvmStatic
    fun containerValidCascade(sizeAccessor: String, getAccessor: String, bound: Int,
                              elementConstraintsFqn: String): StatementConstraint =
            StatementConstraint { accessor ->
                val idx = "__i"
                val elem = "__e"
                val body = StringBuilder()
                body.append("if ($accessor != null) {\n")
                body.append("    for (int $idx = 0; $idx < $bound && $idx < $accessor$sizeAccessor; $idx++) {\n")
                body.append("        var $elem = $accessor$getAccessor($idx);\n")
                body.append("        if ($elem != null) { $elementConstraintsFqn.assumeValid($elem); }\n")
                body.append("    }\n")
                body.append("}")
                body.toString()
            }
}
