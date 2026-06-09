package org.bmc4j.constraints.jakarta

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertFalse
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Negative
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.bmc4j.constraints.Constraint
import org.bmc4j.constraints.ConstraintCodeGenerator
import org.bmc4j.constraints.ConstraintExtractor
import org.bmc4j.constraints.Constraints
import org.bmc4j.constraints.ExtractedConstraints
import org.bmc4j.constraints.StatementConstraint
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Maps the subset of `jakarta.validation.constraints.*` (plus `@Valid`) that translates cleanly to
 * JBMC-analyzable expressions.
 *
 * Supported: `@NotNull`, `@Min`, `@Max`, `@Positive`, `@PositiveOrZero`, `@Negative`,
 * `@NegativeOrZero`, `@Size` (String / array / collection length), `@NotEmpty`, `@Null`,
 * `@AssertTrue`, `@AssertFalse`; `@Past`/`@PastOrPresent`/`@Future`/`@FutureOrPresent` over the
 * modeled `java.time` types; `@DecimalMin`/`@DecimalMax`/`@Digits` over the modeled `BigDecimal`
 * (v1 decimal surface); `@NotBlank`; `@Valid` field cascade; and container-element constraints
 * (numeric + `@NotNull` + `@Valid`) inside `List`/`Set`/`Collection`.
 *
 * **Null semantics (jakarta):** every constraint except `@NotNull` and `@NotBlank` PASSES on `null` —
 * so on a BOXED/reference field the translations are null-guarded. `@NotBlank` is the deliberate
 * asymmetry: it REJECTS null. An unguarded compare elsewhere would either NPE inside the generated
 * assume or silently EXCLUDE valid-null objects from the proof domain (a proof "for every valid
 * object" that never explored the valid-null ones — the false green these guards close).
 *
 * **Temporal "now":** `@Past`/`@Future` compare against a single symbolic reference moment shared by
 * all temporal fields of one object (see [Constraints.past]). The moment is per modeled time type.
 *
 * Deferred (poor/no JBMC modeling): `@Pattern`, `@Email` (regex — `split()`/`chars()` are unsound,
 * see the strings coverage map). Unmodeled temporal types (`ZonedDateTime` etc. — no zone/DST
 * modeling by design), CharSequence `@DecimalMin`/`@DecimalMax` (string-to-decimal parsing, a
 * different surface), and `@DecimalMin`/`@DecimalMax`/`@Digits` on BigInteger / boxed integrals
 * (their sound lowering differs from BigDecimal's) are skipped with a processor NOTE. `Map`
 * key/value element constraints are deferred until needed (NOTE, not silent skip).
 */
class JakartaConstraintExtractor @JvmOverloads constructor(
        /** Surfaces a processor NOTE for a skipped/defaulted decision so no bound is ever silent. */
        private val note: (String, Element) -> Unit = { _, _ -> },
        /**
         * Fallback mirror for a field's container type argument, read from the attributed source
         * tree ([TypeUseTrees]). javac ≤ 22 drops TYPE_USE annotations from the type arguments of
         * `Element.asType()` (JDK-8225377 family, fixed for 23), so without this the element
         * constraints in `List<@Min(1) Integer>` are invisible on JDK 17/21 — and the generated
         * `assumeValid` would silently skip them.
         */
        private val typeArgMirror: (Element, Int) -> TypeMirror? = { _, _ -> null }
) : ConstraintExtractor {

    /** Boolean-only view for the [ConstraintExtractor] contract; richer shapes via [extractAll]. */
    override fun extract(element: Element): List<Constraint> = extractAll(element).constraints

    override fun extractAll(element: Element): ExtractedConstraints {
        val result = mutableListOf<Constraint>()
        val statements = mutableListOf<StatementConstraint>()
        val nowParams = mutableListOf<ConstraintCodeGenerator.NowParam>()
        val type = element.asType()
        val primitive = type.kind.isPrimitive

        if (element.getAnnotation(NotNull::class.java) != null) {
            result.add(Constraints.notNull())
        }
        if (element.getAnnotation(Null::class.java) != null) {
            result.add(Constraints.isNull())
        }
        element.getAnnotation(Min::class.java)?.let {
            result.add(if (primitive) Constraints.min(it.value) else Constraints.minNullable(it.value))
        }
        element.getAnnotation(Max::class.java)?.let {
            result.add(if (primitive) Constraints.max(it.value) else Constraints.maxNullable(it.value))
        }
        if (element.getAnnotation(Positive::class.java) != null) {
            result.add(if (primitive) Constraints.min(1) else Constraints.minNullable(1))
        }
        if (element.getAnnotation(PositiveOrZero::class.java) != null) {
            result.add(if (primitive) Constraints.min(0) else Constraints.minNullable(0))
        }
        if (element.getAnnotation(Negative::class.java) != null) {
            result.add(if (primitive) Constraints.max(-1) else Constraints.maxNullable(-1))
        }
        if (element.getAnnotation(NegativeOrZero::class.java) != null) {
            result.add(if (primitive) Constraints.max(0) else Constraints.maxNullable(0))
        }
        if (element.getAnnotation(AssertTrue::class.java) != null) {
            result.add(if (primitive) Constraints.isTrue() else Constraints.isTrueNullable())
        }
        if (element.getAnnotation(AssertFalse::class.java) != null) {
            result.add(if (primitive) Constraints.isFalse() else Constraints.isFalseNullable())
        }

        val size = sizeAccessorFor(type)
        val sizeAnn = element.getAnnotation(Size::class.java)
        if (sizeAnn != null && size != null) {
            if (sizeAnn.min > 0) {
                result.add(Constraints.sizeAtLeast(size, sizeAnn.min))
            }
            if (sizeAnn.max != Integer.MAX_VALUE) {
                result.add(Constraints.sizeAtMost(size, sizeAnn.max))
            }
        }
        if (element.getAnnotation(NotEmpty::class.java) != null) {
            result.add(Constraints.notNull())
            if (size != null) {
                result.add(Constraints.sizeAtLeast(size, 1))
            }
        }

        // ---- temporal (@Past / @Future family) -----------------------------
        addTemporal(element, type, result, nowParams)

        // ---- decimal (@DecimalMin / @DecimalMax / @Digits) ------------------
        addDecimal(element, type, result)

        // ---- @NotBlank ------------------------------------------------------
        if (element.getAnnotation(NotBlank::class.java) != null) {
            if (isCharSequence(type)) {
                result.add(notBlankConstraint())
            } else {
                note("bmc-constraints: @NotBlank on a non-CharSequence field is unsupported; skipped",
                        element)
            }
        }

        // ---- @Valid field cascade -------------------------------------------
        if (element.getAnnotation(Valid::class.java) != null) {
            val nested = nestedConstraintsFqn(type)
            if (nested != null) {
                statements.add(Constraints.validCascade(nested))
            }
            // No constraints class for the nested type (no jakarta annotations on it): emit nothing.
        }

        // ---- container-element constraints (List<@Min ...>, List<@Valid ...>) ----
        addContainerElements(element, type, sizeAnn, statements)

        return ExtractedConstraints(result, statements, nowParams)
    }

    private fun addTemporal(element: Element, type: TypeMirror, result: MutableList<Constraint>,
                            nowParams: MutableList<ConstraintCodeGenerator.NowParam>) {
        val hasTemporal = element.getAnnotation(Past::class.java) != null ||
                element.getAnnotation(PastOrPresent::class.java) != null ||
                element.getAnnotation(Future::class.java) != null ||
                element.getAnnotation(FutureOrPresent::class.java) != null
        if (!hasTemporal) {
            return
        }
        val fqn = declaredFqn(type)
        val now = NOW_FACTORIES[fqn]
        if (now == null) {
            note("bmc-constraints: @Past/@Future on unmodeled temporal type '$fqn' " +
                    "(no zone/DST modeling); skipped", element)
            return
        }
        val varName = "__now_" + fqn.substringAfterLast('.')
        if (nowParams.none { it.varName == varName }) {
            nowParams.add(ConstraintCodeGenerator.NowParam(varName, fqn, now))
        }
        element.getAnnotation(Past::class.java)?.let { result.add(Constraints.past(varName)) }
        element.getAnnotation(PastOrPresent::class.java)?.let { result.add(Constraints.pastOrPresent(varName)) }
        element.getAnnotation(Future::class.java)?.let { result.add(Constraints.future(varName)) }
        element.getAnnotation(FutureOrPresent::class.java)?.let { result.add(Constraints.futureOrPresent(varName)) }
    }

    private fun addDecimal(element: Element, type: TypeMirror, result: MutableList<Constraint>) {
        val decMin = element.getAnnotation(DecimalMin::class.java)
        val decMax = element.getAnnotation(DecimalMax::class.java)
        val digits = element.getAnnotation(Digits::class.java)
        if (decMin == null && decMax == null && digits == null) {
            return
        }
        if (isCharSequence(type)) {
            note("bmc-constraints: @DecimalMin/@DecimalMax/@Digits on a CharSequence field " +
                    "(string-to-decimal parsing is unmodeled); skipped", element)
            return
        }
        if (!isDecimalType(type)) {
            // Jakarta also permits these on BigInteger and the boxed integrals, but their sound
            // lowering differs (no scale()/compareTo(BigDecimal) on those models); BigDecimal is the
            // v1 surface (the money shape). Anything else is a NOTE, never a silent half-translation.
            note("bmc-constraints: @DecimalMin/@DecimalMax/@Digits is supported on BigDecimal in v1; " +
                    "type '${declaredFqn(type)}' skipped", element)
            return
        }
        decMin?.let { result.add(Constraints.decimalMin(it.value, it.inclusive)) }
        decMax?.let { result.add(Constraints.decimalMax(it.value, it.inclusive)) }
        digits?.let { result.add(Constraints.digits(it.integer, it.fraction)) }
    }

    private fun addContainerElements(element: Element, type: TypeMirror, sizeAnn: Size?,
                                     statements: MutableList<StatementConstraint>) {
        if (type.kind != TypeKind.DECLARED) {
            return
        }
        val declared = type as DeclaredType
        val erasure = (declared.asElement() as? TypeElement)?.qualifiedName?.toString() ?: return
        val isSupportedContainer = erasure in CONTAINER_TYPES
        val isMap = erasure == "java.util.Map"
        val typeArgs = declared.typeArguments

        // The element type-use carries the constraint annotations (Jakarta 3.0 are @Target TYPE_USE).
        val elementArg: TypeMirror = when {
            isSupportedContainer && typeArgs.size == 1 -> withTreeFallback(element, typeArgs[0], 0)
            isMap && typeArgs.size == 2 -> {
                if (hasAnyElementConstraint(withTreeFallback(element, typeArgs[0], 0)) ||
                        hasAnyElementConstraint(withTreeFallback(element, typeArgs[1], 1))) {
                    note("bmc-constraints: Map key/value element constraints are deferred; skipped",
                            element)
                }
                return
            }
            else -> return
        }
        if (!isSupportedContainer) {
            return
        }

        val elemConstraints = elementNumericConstraints(elementArg)
        val hasValid = typeUseAnnotation(elementArg, "jakarta.validation.Valid") != null
        if (elemConstraints.isEmpty() && !hasValid) {
            return
        }

        // Loop bound: the field's @Size(max) when present (the natural pairing), else a default cap
        // surfaced as a NOTE so the bound is never silent.
        val bound: Int
        if (sizeAnn != null && sizeAnn.max != Integer.MAX_VALUE) {
            bound = sizeAnn.max
        } else {
            bound = DEFAULT_ELEMENT_CAP
            note("bmc-constraints: container-element constraints with no @Size(max) — element loop " +
                    "bounded at the default cap of $DEFAULT_ELEMENT_CAP; add @Size(max=...) to widen " +
                    "or tighten it (the first thing to add when a proof gets slow)", element)
        }

        if (elemConstraints.isNotEmpty()) {
            statements.add(Constraints.containerElements(".size()", ".get", bound, elemConstraints))
        }
        if (hasValid) {
            val nested = nestedConstraintsFqn(elementArg)
            if (nested != null) {
                statements.add(Constraints.containerValidCascade(".size()", ".get", bound, nested))
            }
        }
    }

    /**
     * The element-level numeric / @NotNull constraints on a container's element type-use.
     *
     * Read via [TypeMirror.getAnnotationMirrors], NOT `getAnnotation(Class)`: on the
     * attributed-tree mirrors that [typeArgMirror] returns (the javac ≤ 22 path), javac
     * populates the mirror list but the reflective-proxy lookup still answers null —
     * the mirror API is the only read that works on every supported javac.
     */
    private fun elementNumericConstraints(elem: TypeMirror): List<Constraint> {
        val out = mutableListOf<Constraint>()
        // Element type is always a reference type (generics) — use the nullable factories.
        if (typeUseAnnotation(elem, "jakarta.validation.constraints.NotNull") != null) {
            out.add(Constraints.notNull())
        }
        typeUseAnnotation(elem, "jakarta.validation.constraints.Min")?.let {
            out.add(Constraints.minNullable(longMember(it, "value")))
        }
        typeUseAnnotation(elem, "jakarta.validation.constraints.Max")?.let {
            out.add(Constraints.maxNullable(longMember(it, "value")))
        }
        if (typeUseAnnotation(elem, "jakarta.validation.constraints.Positive") != null) {
            out.add(Constraints.minNullable(1))
        }
        if (typeUseAnnotation(elem, "jakarta.validation.constraints.PositiveOrZero") != null) {
            out.add(Constraints.minNullable(0))
        }
        if (typeUseAnnotation(elem, "jakarta.validation.constraints.Negative") != null) {
            out.add(Constraints.maxNullable(-1))
        }
        if (typeUseAnnotation(elem, "jakarta.validation.constraints.NegativeOrZero") != null) {
            out.add(Constraints.maxNullable(0))
        }
        return out
    }

    private fun hasAnyElementConstraint(elem: TypeMirror): Boolean =
            typeUseAnnotation(elem, "jakarta.validation.Valid") != null ||
                    elementNumericConstraints(elem).isNotEmpty()

    private fun typeUseAnnotation(elem: TypeMirror, fqn: String): AnnotationMirror? =
            elem.annotationMirrors.firstOrNull {
                (it.annotationType.asElement() as? TypeElement)?.qualifiedName?.contentEquals(fqn) == true
            }

    /** A mandatory long member (e.g. `@Min(value)` — no default, so always explicit). */
    private fun longMember(ann: AnnotationMirror, name: String): Long =
            ann.elementValues.entries.first { it.key.simpleName.contentEquals(name) }
                    .value.value as Long

    /**
     * The type-argument mirror to read element constraints from: the plain mirror when it already
     * shows constraints (javac 23+), else the attributed-source-tree mirror (javac ≤ 22 drops
     * TYPE_USE annotations from type arguments — see [typeArgMirror]), else the plain one.
     */
    private fun withTreeFallback(field: Element, mirror: TypeMirror, index: Int): TypeMirror =
            if (hasAnyElementConstraint(mirror)) mirror else typeArgMirror(field, index) ?: mirror

    /** `<Type>Constraints` FQN if [type] is a declared type, else null (caller checks existence). */
    private fun nestedConstraintsFqn(type: TypeMirror): String? {
        if (type.kind != TypeKind.DECLARED) {
            return null
        }
        val element = (type as DeclaredType).asElement() as? TypeElement ?: return null
        // Skip JDK/jakarta types: a `@Valid` on them never has a generated helper.
        val fqn = element.qualifiedName.toString()
        if (fqn.startsWith("java.") || fqn.startsWith("jakarta.")) {
            return null
        }
        val pkg = fqn.substringBeforeLast('.', "")
        val simple = element.simpleName.toString() + "Constraints"
        return if (pkg.isEmpty()) simple else "$pkg.$simple"
    }

    private fun notBlankConstraint(): Constraint =
            if (USE_TRIM) Constraints.notBlankTrim() else Constraints.notBlankCharAtLoop(MAX_STRING_LENGTH)

    private companion object {

        /**
         * `@NotBlank` route. The lowering is generated as Java, so `x.trim().isEmpty()` binds to the
         * NATIVE `java.lang.String.trim()`/`isEmpty()` — JBMC's built-in CProver string library, which
         * StringBytecode does not redirect. That native `trim()` is the fragile op: on the older Kotlin
         * matrix legs (the kotlin-2.3.21 codegen) JBMC mis-models it so that `"".trim().isEmpty()` can
         * read back FALSE, admitting a length-0 "valid" name and FALSE-REFUTING `valid_name_is_non_empty`
         * (the same older-leg model-fragility family as the #237 devirt fixes — sound logic, leg-specific
         * unsound modeling). The charAt-loop ([Constraints.notBlankCharAtLoop]) is built only from the
         * natively-sound `length()`/`charAt` primitives and is itself conformance-pinned, so we route
         * `@NotBlank` through it: a bounded existential over `[0, MAX_STRING_LENGTH)`. The bound matches
         * `maxStringLength`'s nondet-string cap ([BmcProofExtension] DEFAULT_MAX_STRING = 16), so the OR
         * is COMPLETE over every modeled string — no valid name is excluded (sound), and the trim model
         * is never touched.
         */
        const val USE_TRIM = false
        const val MAX_STRING_LENGTH = 16

        /** Default container-element loop cap when no `@Size(max)` pairs the field (mirrors the string cap). */
        const val DEFAULT_ELEMENT_CAP = 16

        val CONTAINER_TYPES = setOf("java.util.List", "java.util.Set", "java.util.Collection")

        /** Modeled temporal type -> a symbolic-instance factory expression (valid by construction). */
        val NOW_FACTORIES = mapOf(
                "java.time.LocalDate" to
                        "java.time.LocalDate.ofEpochDay(org.bmc4j.Bmc.anyLong())",
                "java.time.LocalTime" to
                        "java.time.LocalTime.ofNanoOfDay(org.bmc4j.Bmc.anyLong(0L, 86399999999999L))",
                "java.time.LocalDateTime" to
                        ("java.time.LocalDateTime.of(java.time.LocalDate.ofEpochDay(org.bmc4j.Bmc.anyLong()), " +
                                "java.time.LocalTime.ofNanoOfDay(org.bmc4j.Bmc.anyLong(0L, 86399999999999L)))"),
                // ofEpochMilli (a bare wrap), NOT ofEpochSecond (whose checked seconds->millis
                // multiply throws on an unbounded symbolic input — an uncaught exception in the assume).
                "java.time.Instant" to
                        "java.time.Instant.ofEpochMilli(org.bmc4j.Bmc.anyLong())")

        fun declaredFqn(type: TypeMirror): String {
            if (type.kind != TypeKind.DECLARED) {
                return type.toString()
            }
            val element = (type as DeclaredType).asElement() as? TypeElement ?: return type.toString()
            return element.qualifiedName.toString()
        }

        fun isCharSequence(type: TypeMirror): Boolean =
                declaredFqn(type) in setOf("java.lang.String", "java.lang.CharSequence")

        /** v1 decimal surface: BigDecimal only (compareTo/scale/toBigInteger are modeled on it). */
        fun isDecimalType(type: TypeMirror): Boolean =
                declaredFqn(type) == "java.math.BigDecimal"

        /** The expression suffix that reads a length/size for this type, or null if unsupported. */
        fun sizeAccessorFor(type: TypeMirror): String? {
            if (type.kind == TypeKind.ARRAY) {
                return ".length"
            }
            if (type.kind != TypeKind.DECLARED) {
                return null
            }
            // Resolve via the type's element so type-use annotations (Jakarta 3.0
            // constraints are @Target TYPE_USE) don't pollute the name.
            val element = (type as DeclaredType).asElement() as? TypeElement ?: return null
            return when (element.qualifiedName.toString()) {
                "java.lang.String", "java.lang.CharSequence" -> ".length()"
                "java.util.List", "java.util.Set", "java.util.Collection", "java.util.Map" -> ".size()"
                else -> null
            }
        }
    }
}
