package org.bmc4j.constraints.jakarta

import jakarta.validation.constraints.AssertFalse
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Negative
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.bmc4j.constraints.Constraint
import org.bmc4j.constraints.ConstraintExtractor
import org.bmc4j.constraints.Constraints
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * Maps the subset of `jakarta.validation.constraints.*` that translates
 * cleanly to JBMC-analyzable expressions.
 *
 * Supported: `@NotNull`, `@Min`, `@Max`, `@Positive`, `@PositiveOrZero`, `@Negative`,
 * `@NegativeOrZero`, `@Size` (String / array / collection length), `@NotEmpty`,
 * `@Null`, `@AssertTrue`, `@AssertFalse`.
 *
 * **Null semantics (jakarta):** every constraint except `@NotNull` PASSES on
 * `null` — so on a BOXED/reference field the numeric and boolean translations are
 * null-guarded. An unguarded compare would either NPE inside the generated assume or silently
 * EXCLUDE valid-null objects from the proof domain (a proof "for every valid object" that never
 * explored the valid-null ones — the false green this guard closes).
 *
 * Deferred (poor/no JBMC modeling): `@Pattern`, `@Email` (regex), `@NotBlank` (trim),
 * `@Digits`/`@DecimalMin`/`@DecimalMax` (BigDecimal), `@Past`/`@Future` (dates).
 */
class JakartaConstraintExtractor : ConstraintExtractor {

    override fun extract(element: Element): List<Constraint> {
        val result = mutableListOf<Constraint>()
        val primitive = element.asType().kind.isPrimitive

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

        val size = sizeAccessorFor(element.asType())
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
        return result
    }

    private companion object {

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
