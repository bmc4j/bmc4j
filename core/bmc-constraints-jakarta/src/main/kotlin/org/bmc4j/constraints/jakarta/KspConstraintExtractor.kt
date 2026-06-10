package org.bmc4j.constraints.jakarta

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import org.bmc4j.constraints.Constraint
import org.bmc4j.constraints.ConstraintCodeGenerator
import org.bmc4j.constraints.Constraints
import org.bmc4j.constraints.ExtractedConstraints
import org.bmc4j.constraints.StatementConstraint

/**
 * The KSP analogue of [JakartaConstraintExtractor]: it maps the same subset of
 * `jakarta.validation.constraints.*` (plus `@Valid`) to the SAME [Constraint] / [StatementConstraint]
 * model the javac path builds, reading the annotations off **Kotlin declarations** (data-class
 * constructor `val` params and properties) instead of `javax.lang.model` elements. The shared
 * [Constraints] factory and [ConstraintCodeGenerator] then render a byte-identical `assumeValid`
 * helper, so a Kotlin DTO works exactly like the Java one with no mirror class.
 *
 * **Annotation-use-site targets:** validation libraries resolve `@field:`-targeted annotations (the
 * backing field is what reflection-based validation reads). On a Kotlin property KSP attaches a
 * `@field:Min` to the property declaration with [AnnotationUseSiteTarget.FIELD], a `@get:Min` with
 * [AnnotationUseSiteTarget.GET], a `@param:Min` to the matching constructor value parameter, and a
 * bare `@Min` (no target) to the property/parameter per Kotlin's defaulting. This extractor reads
 * the union of the property's annotations and its constructor parameter's annotations, so every
 * use-site target a consumer might write is seen — see [annotationsFor].
 *
 * Coverage matches the javac extractor's documented surface (numeric / size / boolean / temporal /
 * decimal / `@NotBlank` / `@Valid` cascade / container-element). The `note` hook surfaces every
 * skipped/defaulted decision as a KSP logger note, exactly like the APT path's processor NOTEs, so no
 * bound is ever silent.
 */
class KspConstraintExtractor(
        /** Surfaces a note for a skipped/defaulted decision so no bound is ever silent. */
        private val note: (String) -> Unit = {}) {

    /**
     * The full constraint set for one Kotlin property: its boolean constraints, statements (cascade /
     * container loops) and shared temporal nowParams. [annotations] is the union of the property's and
     * its constructor parameter's annotations (every use-site target); [type] is the property's type.
     */
    fun extractAll(annotations: List<KSAnnotation>, type: KSType): ExtractedConstraints {
        val result = mutableListOf<Constraint>()
        val statements = mutableListOf<StatementConstraint>()
        val nowParams = mutableListOf<ConstraintCodeGenerator.NowParam>()
        // A Kotlin non-nullable primitive (`Int`, not `Int?`) lowers to a JVM primitive field, so the
        // un-guarded factories apply (no null in the domain). A nullable or reference type is boxed.
        val primitive = isPrimitive(type)

        if (has(annotations, NOT_NULL)) {
            result.add(Constraints.notNull())
        }
        if (has(annotations, NULL)) {
            result.add(Constraints.isNull())
        }
        longArg(annotations, MIN, "value")?.let {
            result.add(if (primitive) Constraints.min(it) else Constraints.minNullable(it))
        }
        longArg(annotations, MAX, "value")?.let {
            result.add(if (primitive) Constraints.max(it) else Constraints.maxNullable(it))
        }
        if (has(annotations, POSITIVE)) {
            result.add(if (primitive) Constraints.min(1) else Constraints.minNullable(1))
        }
        if (has(annotations, POSITIVE_OR_ZERO)) {
            result.add(if (primitive) Constraints.min(0) else Constraints.minNullable(0))
        }
        if (has(annotations, NEGATIVE)) {
            result.add(if (primitive) Constraints.max(-1) else Constraints.maxNullable(-1))
        }
        if (has(annotations, NEGATIVE_OR_ZERO)) {
            result.add(if (primitive) Constraints.max(0) else Constraints.maxNullable(0))
        }
        if (has(annotations, ASSERT_TRUE)) {
            result.add(if (primitive) Constraints.isTrue() else Constraints.isTrueNullable())
        }
        if (has(annotations, ASSERT_FALSE)) {
            result.add(if (primitive) Constraints.isFalse() else Constraints.isFalseNullable())
        }

        val size = sizeAccessorFor(type)
        val sizeAnn = find(annotations, SIZE)
        if (sizeAnn != null && size != null) {
            val min = intArg(sizeAnn, "min") ?: 0
            val max = intArg(sizeAnn, "max") ?: Integer.MAX_VALUE
            if (min > 0) {
                result.add(Constraints.sizeAtLeast(size, min))
            }
            if (max != Integer.MAX_VALUE) {
                result.add(Constraints.sizeAtMost(size, max))
            }
        }
        if (has(annotations, NOT_EMPTY)) {
            result.add(Constraints.notNull())
            if (size != null) {
                result.add(Constraints.sizeAtLeast(size, 1))
            }
        }

        addTemporal(annotations, type, result, nowParams)
        addDecimal(annotations, type, result)

        if (has(annotations, NOT_BLANK)) {
            if (isCharSequence(type)) {
                result.add(notBlankConstraint())
            } else {
                note("bmc-constraints: @NotBlank on a non-CharSequence property is unsupported; skipped")
            }
        }

        if (has(annotations, VALID)) {
            nestedConstraintsFqn(type)?.let { statements.add(Constraints.validCascade(it)) }
        }

        addContainerElements(type, sizeAnn, statements)

        return ExtractedConstraints(result, statements, nowParams)
    }

    private fun addTemporal(annotations: List<KSAnnotation>, type: KSType,
                            result: MutableList<Constraint>,
                            nowParams: MutableList<ConstraintCodeGenerator.NowParam>) {
        val hasTemporal = has(annotations, PAST) || has(annotations, PAST_OR_PRESENT) ||
                has(annotations, FUTURE) || has(annotations, FUTURE_OR_PRESENT)
        if (!hasTemporal) {
            return
        }
        val fqn = declaredFqn(type)
        val now = NOW_FACTORIES[fqn]
        if (now == null) {
            note("bmc-constraints: @Past/@Future on unmodeled temporal type '$fqn' " +
                    "(no zone/DST modeling); skipped")
            return
        }
        val varName = "__now_" + fqn.substringAfterLast('.')
        if (nowParams.none { it.varName == varName }) {
            nowParams.add(ConstraintCodeGenerator.NowParam(varName, fqn, now))
        }
        if (has(annotations, PAST)) result.add(Constraints.past(varName))
        if (has(annotations, PAST_OR_PRESENT)) result.add(Constraints.pastOrPresent(varName))
        if (has(annotations, FUTURE)) result.add(Constraints.future(varName))
        if (has(annotations, FUTURE_OR_PRESENT)) result.add(Constraints.futureOrPresent(varName))
    }

    private fun addDecimal(annotations: List<KSAnnotation>, type: KSType,
                           result: MutableList<Constraint>) {
        val decMin = find(annotations, DECIMAL_MIN)
        val decMax = find(annotations, DECIMAL_MAX)
        val digits = find(annotations, DIGITS)
        if (decMin == null && decMax == null && digits == null) {
            return
        }
        if (isCharSequence(type)) {
            note("bmc-constraints: @DecimalMin/@DecimalMax/@Digits on a CharSequence property " +
                    "(string-to-decimal parsing is unmodeled); skipped")
            return
        }
        if (!isDecimalType(type)) {
            note("bmc-constraints: @DecimalMin/@DecimalMax/@Digits is supported on BigDecimal in v1; " +
                    "type '${declaredFqn(type)}' skipped")
            return
        }
        decMin?.let { result.add(Constraints.decimalMin(stringArg(it, "value") ?: "0",
                boolArg(it, "inclusive") ?: true)) }
        decMax?.let { result.add(Constraints.decimalMax(stringArg(it, "value") ?: "0",
                boolArg(it, "inclusive") ?: true)) }
        digits?.let { result.add(Constraints.digits(intArg(it, "integer") ?: 0,
                intArg(it, "fraction") ?: 0)) }
    }

    private fun addContainerElements(type: KSType, sizeAnn: KSAnnotation?,
                                     statements: MutableList<StatementConstraint>) {
        val erasure = declaredFqn(type)
        val isSupportedContainer = erasure in CONTAINER_TYPES
        val isMap = erasure == "java.util.Map"
        val typeArgs = type.arguments

        val elementArg: KSTypeArgument = when {
            isSupportedContainer && typeArgs.size == 1 -> typeArgs[0]
            isMap && typeArgs.size == 2 -> {
                if (hasAnyElementConstraint(typeArgs[0]) || hasAnyElementConstraint(typeArgs[1])) {
                    note("bmc-constraints: Map key/value element constraints are deferred; skipped")
                }
                return
            }
            else -> return
        }

        val elemConstraints = elementNumericConstraints(elementArg)
        val hasValid = typeArgAnnotation(elementArg, VALID) != null
        if (elemConstraints.isEmpty() && !hasValid) {
            return
        }

        val sizeMax = sizeAnn?.let { intArg(it, "max") }
        val bound: Int
        if (sizeMax != null && sizeMax != Integer.MAX_VALUE) {
            bound = sizeMax
        } else {
            bound = DEFAULT_ELEMENT_CAP
            note("bmc-constraints: container-element constraints with no @Size(max) — element loop " +
                    "bounded at the default cap of $DEFAULT_ELEMENT_CAP; add @Size(max=...) to widen " +
                    "or tighten it")
        }

        if (elemConstraints.isNotEmpty()) {
            statements.add(Constraints.containerElements(".size()", ".get", bound, elemConstraints))
        }
        if (hasValid) {
            val elemType = elementArg.type?.resolve()
            val nested = elemType?.let { nestedConstraintsFqn(it) }
            if (nested != null) {
                statements.add(Constraints.containerValidCascade(".size()", ".get", bound, nested))
            }
        }
    }

    /** Element-level numeric / @NotNull constraints on a container's element type argument. */
    private fun elementNumericConstraints(elem: KSTypeArgument): List<Constraint> {
        val out = mutableListOf<Constraint>()
        // Element type is always a reference type (generics) — use the nullable factories.
        if (typeArgAnnotation(elem, NOT_NULL) != null) {
            out.add(Constraints.notNull())
        }
        typeArgAnnotation(elem, MIN)?.let { longArgOf(it, "value")?.let { v -> out.add(Constraints.minNullable(v)) } }
        typeArgAnnotation(elem, MAX)?.let { longArgOf(it, "value")?.let { v -> out.add(Constraints.maxNullable(v)) } }
        if (typeArgAnnotation(elem, POSITIVE) != null) out.add(Constraints.minNullable(1))
        if (typeArgAnnotation(elem, POSITIVE_OR_ZERO) != null) out.add(Constraints.minNullable(0))
        if (typeArgAnnotation(elem, NEGATIVE) != null) out.add(Constraints.maxNullable(-1))
        if (typeArgAnnotation(elem, NEGATIVE_OR_ZERO) != null) out.add(Constraints.maxNullable(0))
        return out
    }

    private fun hasAnyElementConstraint(elem: KSTypeArgument): Boolean =
            typeArgAnnotation(elem, VALID) != null || elementNumericConstraints(elem).isNotEmpty()

    private fun typeArgAnnotation(elem: KSTypeArgument, fqn: String): KSAnnotation? {
        // A type-use annotation on `List<@Min(1) Int>` is reported on the type argument's `type`
        // reference (Jakarta 3.0 constraints are @Target TYPE_USE). Read from both the type argument
        // and its referenced type to cover how KSP surfaces TYPE_USE annotations.
        elem.annotations.firstOrNull { fqnOf(it) == fqn }?.let { return it }
        return elem.type?.annotations?.firstOrNull { fqnOf(it) == fqn }
    }

    private fun nestedConstraintsFqn(type: KSType): String? {
        val decl = type.declaration as? KSClassDeclaration ?: return null
        val fqn = decl.qualifiedName?.asString() ?: return null
        if (fqn.startsWith("java.") || fqn.startsWith("jakarta.") || fqn.startsWith("kotlin.")) {
            return null
        }
        val pkg = fqn.substringBeforeLast('.', "")
        val simple = decl.simpleName.asString() + "Constraints"
        return if (pkg.isEmpty()) simple else "$pkg.$simple"
    }

    private fun notBlankConstraint(): Constraint =
            if (USE_TRIM) Constraints.notBlankTrim() else Constraints.notBlankCharAtLoop(MAX_STRING_LENGTH)

    // ---- annotation reading helpers -----------------------------------------

    private fun has(annotations: List<KSAnnotation>, fqn: String): Boolean =
            annotations.any { fqnOf(it) == fqn }

    private fun find(annotations: List<KSAnnotation>, fqn: String): KSAnnotation? =
            annotations.firstOrNull { fqnOf(it) == fqn }

    private fun longArg(annotations: List<KSAnnotation>, fqn: String, name: String): Long? =
            find(annotations, fqn)?.let { longArgOf(it, name) }

    private fun longArgOf(ann: KSAnnotation, name: String): Long? =
            (argValue(ann, name) as? Number)?.toLong()

    private fun intArg(ann: KSAnnotation, name: String): Int? = (argValue(ann, name) as? Number)?.toInt()

    private fun boolArg(ann: KSAnnotation, name: String): Boolean? = argValue(ann, name) as? Boolean

    private fun stringArg(ann: KSAnnotation, name: String): String? = argValue(ann, name) as? String

    private fun argValue(ann: KSAnnotation, name: String): Any? =
            ann.arguments.firstOrNull { it.name?.asString() == name }?.value

    private companion object {

        const val PKG = "jakarta.validation.constraints."
        const val NOT_NULL = PKG + "NotNull"
        const val NULL = PKG + "Null"
        const val MIN = PKG + "Min"
        const val MAX = PKG + "Max"
        const val POSITIVE = PKG + "Positive"
        const val POSITIVE_OR_ZERO = PKG + "PositiveOrZero"
        const val NEGATIVE = PKG + "Negative"
        const val NEGATIVE_OR_ZERO = PKG + "NegativeOrZero"
        const val ASSERT_TRUE = PKG + "AssertTrue"
        const val ASSERT_FALSE = PKG + "AssertFalse"
        const val SIZE = PKG + "Size"
        const val NOT_EMPTY = PKG + "NotEmpty"
        const val NOT_BLANK = PKG + "NotBlank"
        const val PAST = PKG + "Past"
        const val PAST_OR_PRESENT = PKG + "PastOrPresent"
        const val FUTURE = PKG + "Future"
        const val FUTURE_OR_PRESENT = PKG + "FutureOrPresent"
        const val DECIMAL_MIN = PKG + "DecimalMin"
        const val DECIMAL_MAX = PKG + "DecimalMax"
        const val DIGITS = PKG + "Digits"
        const val VALID = "jakarta.validation.Valid"

        // Mirror JakartaConstraintExtractor's @NotBlank route and caps (kept identical for parity).
        const val USE_TRIM = true
        const val MAX_STRING_LENGTH = 16
        const val DEFAULT_ELEMENT_CAP = 16

        val CONTAINER_TYPES = setOf("java.util.List", "java.util.Set", "java.util.Collection",
                "kotlin.collections.List", "kotlin.collections.MutableList",
                "kotlin.collections.Set", "kotlin.collections.MutableSet",
                "kotlin.collections.Collection", "kotlin.collections.MutableCollection")

        val NOW_FACTORIES = mapOf(
                "java.time.LocalDate" to
                        "java.time.LocalDate.ofEpochDay(org.bmc4j.Bmc.anyLong())",
                "java.time.LocalTime" to
                        "java.time.LocalTime.ofNanoOfDay(org.bmc4j.Bmc.anyLong(0L, 86399999999999L))",
                "java.time.LocalDateTime" to
                        ("java.time.LocalDateTime.of(java.time.LocalDate.ofEpochDay(org.bmc4j.Bmc.anyLong()), " +
                                "java.time.LocalTime.ofNanoOfDay(org.bmc4j.Bmc.anyLong(0L, 86399999999999L)))"),
                "java.time.Instant" to
                        "java.time.Instant.ofEpochMilli(org.bmc4j.Bmc.anyLong())")

        fun fqnOf(ann: KSAnnotation): String? =
                ann.annotationType.resolve().declaration.qualifiedName?.asString()

        /**
         * The constraint-bearing FQN of a Kotlin property/parameter type, normalized to the Java
         * form the generated helper and the APT path use (`kotlin.Int` -> primitive `int` is handled
         * by [isPrimitive]; the collections map to `java.util.*`). Nullability is stripped.
         */
        fun declaredFqn(type: KSType): String {
            val qn = type.declaration.qualifiedName?.asString() ?: return type.toString()
            return when (qn) {
                "kotlin.String", "kotlin.CharSequence" -> if (qn == "kotlin.String") "java.lang.String"
                        else "java.lang.CharSequence"
                "kotlin.collections.List", "kotlin.collections.MutableList" -> "java.util.List"
                "kotlin.collections.Set", "kotlin.collections.MutableSet" -> "java.util.Set"
                "kotlin.collections.Collection", "kotlin.collections.MutableCollection" ->
                    "java.util.Collection"
                "kotlin.collections.Map", "kotlin.collections.MutableMap" -> "java.util.Map"
                else -> qn
            }
        }

        /**
         * True for a Kotlin non-nullable primitive type (`Int`, `Long`, … `Boolean`) — it lowers to a
         * JVM primitive field, so the un-guarded numeric/boolean factories apply. A nullable (`Int?`)
         * or any reference type is boxed and takes the null-passing factories.
         */
        fun isPrimitive(type: KSType): Boolean {
            if (type.isMarkedNullable) {
                return false
            }
            return type.declaration.qualifiedName?.asString() in KOTLIN_PRIMITIVES
        }

        val KOTLIN_PRIMITIVES = setOf(
                "kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte",
                "kotlin.Char", "kotlin.Boolean", "kotlin.Float", "kotlin.Double")

        fun isCharSequence(type: KSType): Boolean =
                declaredFqn(type) in setOf("java.lang.String", "java.lang.CharSequence")

        fun isDecimalType(type: KSType): Boolean = declaredFqn(type) == "java.math.BigDecimal"

        /** The expression suffix that reads a length/size for this type, or null if unsupported. */
        fun sizeAccessorFor(type: KSType): String? = when (declaredFqn(type)) {
            "java.lang.String", "java.lang.CharSequence" -> ".length()"
            "java.util.List", "java.util.Set", "java.util.Collection", "java.util.Map" -> ".size()"
            else -> if (type.declaration.qualifiedName?.asString()?.let {
                        it in KOTLIN_ARRAYS
                    } == true || isArray(type)) ".length" else null
        }

        val KOTLIN_ARRAYS = setOf(
                "kotlin.IntArray", "kotlin.LongArray", "kotlin.ShortArray", "kotlin.ByteArray",
                "kotlin.CharArray", "kotlin.BooleanArray", "kotlin.FloatArray", "kotlin.DoubleArray",
                "kotlin.Array")

        fun isArray(type: KSType): Boolean =
                type.declaration.qualifiedName?.asString() == "kotlin.Array"
    }
}
