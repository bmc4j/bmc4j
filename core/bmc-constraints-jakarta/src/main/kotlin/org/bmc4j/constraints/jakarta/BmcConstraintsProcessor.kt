package org.bmc4j.constraints.jakarta

import org.bmc4j.constraints.ConstraintCodeGenerator
import org.bmc4j.constraints.ConstraintExtractor
import java.io.IOException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.tools.Diagnostic

/**
 * Generates a `<Type>Constraints.assumeValid(<Type>)` helper for every model
 * class with Jakarta validation annotations, so proofs can constrain symbolic
 * inputs to "valid" without hand-writing assumptions.
 */
@SupportedAnnotationTypes("jakarta.validation.constraints.*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class BmcConstraintsProcessor : AbstractProcessor() {

    private val extractor: ConstraintExtractor = JakartaConstraintExtractor()
    private val generated = mutableSetOf<String>()

    override fun process(annotations: Set<TypeElement>, round: RoundEnvironment): Boolean {
        val classes = LinkedHashSet<TypeElement>()
        for (annotation in annotations) {
            for (annotated in round.getElementsAnnotatedWith(annotation)) {
                val enclosing = annotated.enclosingElement
                if (annotated.kind == ElementKind.FIELD && enclosing is TypeElement) {
                    classes.add(enclosing)
                }
            }
        }
        classes.forEach(::generateFor)
        return false // let other processors see these annotations too
    }

    private fun generateFor(type: TypeElement) {
        val targetFqn = type.qualifiedName.toString()
        if (!generated.add(targetFqn)) {
            return
        }

        val fields = mutableListOf<ConstraintCodeGenerator.Field>()
        for (member in type.enclosedElements) {
            if (member.kind != ElementKind.FIELD) {
                continue
            }
            val constraints = extractor.extract(member)
            if (constraints.isEmpty()) {
                continue
            }
            val accessor = accessorFor(type, member as VariableElement)
            if (accessor == null) {
                processingEnv.messager.printMessage(Diagnostic.Kind.WARNING,
                        "bmc-constraints: no public field or getter for '${member.simpleName}'; " +
                                "skipping its constraints", member)
                continue
            }
            fields.add(ConstraintCodeGenerator.Field(accessor, constraints))
        }
        if (fields.isEmpty()) {
            return
        }

        val packageName = processingEnv.elementUtils.getPackageOf(type).qualifiedName.toString()
        val simpleName = "${type.simpleName}Constraints"
        val generatedFqn = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
        val source = ConstraintCodeGenerator.generate(packageName, simpleName, targetFqn, "obj", fields)

        try {
            processingEnv.filer.createSourceFile(generatedFqn, type).openWriter().use { it.write(source) }
        } catch (e: IOException) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                    "bmc-constraints: failed to write $generatedFqn: ${e.message}", type)
        }
    }

    /** Read via a public field, else a public no-arg getter; null if neither exists. */
    private fun accessorFor(type: TypeElement, field: VariableElement): String? {
        val name = field.simpleName.toString()
        val mods = field.modifiers
        if (Modifier.PUBLIC in mods && Modifier.STATIC !in mods) {
            return "obj.$name"
        }
        val capitalized = name.replaceFirstChar { it.uppercaseChar() }
        val getters = if (field.asType().kind == TypeKind.BOOLEAN) {
            listOf("is$capitalized", "get$capitalized")
        } else {
            listOf("get$capitalized")
        }
        return type.enclosedElements.asSequence()
                .filter { it.kind == ElementKind.METHOD }
                .map { it as ExecutableElement }
                .firstOrNull {
                    Modifier.PUBLIC in it.modifiers && it.parameters.isEmpty()
                            && it.simpleName.toString() in getters
                }
                ?.let { "obj.${it.simpleName}()" }
    }
}
