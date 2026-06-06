package org.bmc4j.constraints.jakarta;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.bmc4j.constraints.Constraint;
import org.bmc4j.constraints.ConstraintExtractor;
import org.bmc4j.constraints.Constraints;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the subset of {@code jakarta.validation.constraints.*} that translates
 * cleanly to JBMC-analyzable expressions.
 *
 * <p>Supported: {@code @NotNull}, {@code @Min}, {@code @Max}, {@code @Positive},
 * {@code @PositiveOrZero}, {@code @Negative}, {@code @NegativeOrZero},
 * {@code @Size} (String / array / collection length), {@code @NotEmpty},
 * {@code @Null}, {@code @AssertTrue}, {@code @AssertFalse}.
 *
 * <p><b>Null semantics (jakarta):</b> every constraint except {@code @NotNull} PASSES on
 * {@code null} — so on a BOXED/reference field the numeric and boolean translations are
 * null-guarded. An unguarded compare would either NPE inside the generated assume or silently
 * EXCLUDE valid-null objects from the proof domain (a proof "for every valid object" that never
 * explored the valid-null ones — the false green this guard closes).
 *
 * <p>Deferred (poor/no JBMC modeling): {@code @Pattern}, {@code @Email} (regex),
 * {@code @NotBlank} (trim), {@code @Digits}/{@code @DecimalMin}/{@code @DecimalMax}
 * (BigDecimal), {@code @Past}/{@code @Future} (dates).
 */
public final class JakartaConstraintExtractor implements ConstraintExtractor {

    @Override
    public List<Constraint> extract(Element element) {
        List<Constraint> result = new ArrayList<>();
        boolean primitive = element.asType().getKind().isPrimitive();

        if (element.getAnnotation(NotNull.class) != null) {
            result.add(Constraints.notNull());
        }
        if (element.getAnnotation(Null.class) != null) {
            result.add(Constraints.isNull());
        }
        Min min = element.getAnnotation(Min.class);
        if (min != null) {
            result.add(primitive ? Constraints.min(min.value()) : Constraints.minNullable(min.value()));
        }
        Max max = element.getAnnotation(Max.class);
        if (max != null) {
            result.add(primitive ? Constraints.max(max.value()) : Constraints.maxNullable(max.value()));
        }
        if (element.getAnnotation(Positive.class) != null) {
            result.add(primitive ? Constraints.min(1) : Constraints.minNullable(1));
        }
        if (element.getAnnotation(PositiveOrZero.class) != null) {
            result.add(primitive ? Constraints.min(0) : Constraints.minNullable(0));
        }
        if (element.getAnnotation(Negative.class) != null) {
            result.add(primitive ? Constraints.max(-1) : Constraints.maxNullable(-1));
        }
        if (element.getAnnotation(NegativeOrZero.class) != null) {
            result.add(primitive ? Constraints.max(0) : Constraints.maxNullable(0));
        }
        if (element.getAnnotation(AssertTrue.class) != null) {
            result.add(primitive ? Constraints.isTrue() : Constraints.isTrueNullable());
        }
        if (element.getAnnotation(AssertFalse.class) != null) {
            result.add(primitive ? Constraints.isFalse() : Constraints.isFalseNullable());
        }

        String size = sizeAccessorFor(element.asType());
        Size sizeAnn = element.getAnnotation(Size.class);
        if (sizeAnn != null && size != null) {
            if (sizeAnn.min() > 0) {
                result.add(Constraints.sizeAtLeast(size, sizeAnn.min()));
            }
            if (sizeAnn.max() != Integer.MAX_VALUE) {
                result.add(Constraints.sizeAtMost(size, sizeAnn.max()));
            }
        }
        if (element.getAnnotation(NotEmpty.class) != null) {
            result.add(Constraints.notNull());
            if (size != null) {
                result.add(Constraints.sizeAtLeast(size, 1));
            }
        }
        return result;
    }

    /** The expression suffix that reads a length/size for this type, or null if unsupported. */
    private static String sizeAccessorFor(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return ".length";
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        // Resolve via the type's element so type-use annotations (Jakarta 3.0
        // constraints are @Target TYPE_USE) don't pollute the name.
        Element element = ((DeclaredType) type).asElement();
        if (!(element instanceof TypeElement)) {
            return null;
        }
        String name = ((TypeElement) element).getQualifiedName().toString();
        if (name.equals("java.lang.String") || name.equals("java.lang.CharSequence")) {
            return ".length()";
        }
        if (name.equals("java.util.List") || name.equals("java.util.Set")
                || name.equals("java.util.Collection") || name.equals("java.util.Map")) {
            return ".size()";
        }
        return null;
    }
}
