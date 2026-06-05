package org.bmc4j.constraints.jakarta;

import org.bmc4j.constraints.Constraint;
import org.bmc4j.constraints.ConstraintCodeGenerator;
import org.bmc4j.constraints.ConstraintExtractor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a {@code <Type>Constraints.assumeValid(<Type>)} helper for every model
 * class with Jakarta validation annotations, so proofs can constrain symbolic
 * inputs to "valid" without hand-writing assumptions.
 */
@SupportedAnnotationTypes("jakarta.validation.constraints.*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class BmcConstraintsProcessor extends AbstractProcessor {

    private final ConstraintExtractor extractor = new JakartaConstraintExtractor();
    private final Set<String> generated = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        Set<TypeElement> classes = new LinkedHashSet<>();
        for (TypeElement annotation : annotations) {
            for (Element annotated : round.getElementsAnnotatedWith(annotation)) {
                if (annotated.getKind() == ElementKind.FIELD
                        && annotated.getEnclosingElement() instanceof TypeElement) {
                    classes.add((TypeElement) annotated.getEnclosingElement());
                }
            }
        }
        for (TypeElement type : classes) {
            generateFor(type);
        }
        return false; // let other processors see these annotations too
    }

    private void generateFor(TypeElement type) {
        String targetFqn = type.getQualifiedName().toString();
        if (!generated.add(targetFqn)) {
            return;
        }

        List<ConstraintCodeGenerator.Field> fields = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.FIELD) {
                continue;
            }
            List<Constraint> constraints = extractor.extract(member);
            if (constraints.isEmpty()) {
                continue;
            }
            String accessor = accessorFor(type, (VariableElement) member);
            if (accessor == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "bmc-constraints: no public field or getter for '" + member.getSimpleName()
                                + "'; skipping its constraints", member);
                continue;
            }
            fields.add(new ConstraintCodeGenerator.Field(accessor, constraints));
        }
        if (fields.isEmpty()) {
            return;
        }

        String packageName = processingEnv.getElementUtils()
                .getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName() + "Constraints";
        String generatedFqn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String source = ConstraintCodeGenerator.generate(packageName, simpleName, targetFqn, "obj", fields);

        try (Writer writer = processingEnv.getFiler().createSourceFile(generatedFqn, type).openWriter()) {
            writer.write(source);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "bmc-constraints: failed to write " + generatedFqn + ": " + e.getMessage(), type);
        }
    }

    /** Read via a public field, else a public no-arg getter; null if neither exists. */
    private String accessorFor(TypeElement type, VariableElement field) {
        String name = field.getSimpleName().toString();
        Set<Modifier> mods = field.getModifiers();
        if (mods.contains(Modifier.PUBLIC) && !mods.contains(Modifier.STATIC)) {
            return "obj." + name;
        }
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        boolean isBoolean = field.asType().getKind() == TypeKind.BOOLEAN;
        List<String> getters = isBoolean
                ? List.of("is" + capitalized, "get" + capitalized)
                : List.of("get" + capitalized);
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            if (method.getModifiers().contains(Modifier.PUBLIC)
                    && method.getParameters().isEmpty()
                    && getters.contains(method.getSimpleName().toString())) {
                return "obj." + method.getSimpleName() + "()";
            }
        }
        return null;
    }
}
