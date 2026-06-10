package org.bmc4j.constraints.jakarta

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Origin
import org.bmc4j.constraints.ConstraintCodeGenerator
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * The KSP analogue of [BmcConstraintsProcessor]: for a **Kotlin** DTO it generates the same
 * `<Type>Constraints.assumeValid(<Type>)` helper the javac annotation processor generates for a Java
 * DTO, from the same `jakarta.validation.constraints.*` annotations and via the same shared
 * [ConstraintCodeGenerator]. A Kotlin `data class Req(@field:Min(1) val qty: Int)` therefore becomes a
 * proof source with no hand-written Java mirror class.
 *
 * The javac [BmcConstraintsProcessor] stays the processor for pure-Java consumers
 * (`testAnnotationProcessor`); this is the Kotlin path (`kspTest`), wired by the `org.bmc4j` Gradle
 * plugin. The two processors compose on `kspTest` alongside the contracts SymbolProcessor.
 *
 * **Annotation-use-site targets:** a validated Kotlin property's constraints are typically written
 * `@field:Min(1)` (the backing field is what reflection-based validators read). KSP attaches such an
 * annotation to the property declaration; a `@param:`-targeted one lands on the matching constructor
 * value parameter; a bare `@Min` follows Kotlin's defaulting. This processor reads the **union** of a
 * property's annotations and its primary-constructor parameter's annotations (see
 * [annotationsForProperty]), so every use-site target a consumer might write is honored.
 */
class BmcConstraintsSymbolProcessor(
        private val codeGenerator: CodeGenerator,
        private val logger: KSPLogger) : SymbolProcessor {

    private val generated = LinkedHashSet<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Every class that carries at least one jakarta constraint on a property: collect the owning
        // classes from the symbols annotated with each constraint annotation, then generate once each.
        val classes = LinkedHashSet<KSClassDeclaration>()
        for (annFqn in TRIGGER_ANNOTATIONS) {
            for (symbol in resolver.getSymbolsWithAnnotation(annFqn)) {
                owningClass(symbol)?.let(classes::add)
            }
        }
        classes.forEach(::generateFor)
        return emptyList()
    }

    /** The class declaration that a constraint-annotated symbol (property or constructor param) belongs to. */
    private fun owningClass(symbol: KSAnnotated): KSClassDeclaration? = when (symbol) {
        is KSPropertyDeclaration -> symbol.parentDeclaration as? KSClassDeclaration
        is KSValueParameter -> {
            // A constructor value parameter: its parent is the constructor function; the class is one up.
            val fn = symbol.parent as? com.google.devtools.ksp.symbol.KSFunctionDeclaration
            fn?.parentDeclaration as? KSClassDeclaration
        }
        else -> null
    }

    private fun generateFor(type: KSClassDeclaration) {
        // SOUNDNESS / no-collision: KSP also surfaces Java declarations, but the javac
        // BmcConstraintsProcessor (on `annotationProcessor`/`testAnnotationProcessor`) owns Java DTOs.
        // Generating here for a Java type would re-create the same `<Type>Constraints.java` the javac
        // path emits — a hard "attempt to recreate a file" error. This KSP path is the KOTLIN path:
        // only generate for declarations whose source is Kotlin. Java DTOs flow through the javac path.
        if (type.origin != Origin.KOTLIN) {
            return
        }
        val targetFqn = type.qualifiedName?.asString() ?: return
        if (!generated.add(targetFqn)) {
            return
        }
        val extractor = KspConstraintExtractor { msg -> logger.info("$msg [$targetFqn]") }

        val ctorParams: Map<String, KSValueParameter> =
                type.primaryConstructor?.parameters
                        ?.mapNotNull { p -> p.name?.asString()?.let { it to p } }
                        ?.toMap()
                        ?: emptyMap()

        val fields = mutableListOf<ConstraintCodeGenerator.Field>()
        val nowParams = LinkedHashMap<String, ConstraintCodeGenerator.NowParam>()
        for (property in type.getAllProperties()) {
            val name = property.simpleName.asString()
            val annotations = annotationsForProperty(property, ctorParams[name])
            if (annotations.isEmpty()) {
                continue
            }
            val propType = property.type.resolve()
            val extracted = extractor.extractAll(annotations, propType)
            if (extracted.isEmpty()) {
                continue
            }
            val accessor = accessorFor(property, name)
            for (np in extracted.nowParams) {
                nowParams.putIfAbsent(np.varName, np)
            }
            fields.add(ConstraintCodeGenerator.Field(accessor, extracted.constraints, extracted.statements))
        }
        if (fields.isEmpty()) {
            return
        }

        val packageName = type.packageName.asString()
        val simpleName = "${type.simpleName.asString()}Constraints"
        val source = ConstraintCodeGenerator.generate(
                packageName, simpleName, targetFqn, "obj", fields, nowParams.values.toList())

        write(packageName, simpleName, source, type)
    }

    /**
     * The union of constraint annotations visible for one property: those on the property declaration
     * itself (covers `@field:`, `@get:`, `@property:`, and bare on a property) plus those on the
     * matching primary-constructor value parameter (`@param:`). Validation libs resolve `@field:`, so
     * the field-targeted ones — KSP surfaces these on the property declaration — are the common case.
     */
    private fun annotationsForProperty(property: KSPropertyDeclaration,
                                       ctorParam: KSValueParameter?): List<KSAnnotation> {
        val out = ArrayList<KSAnnotation>()
        property.annotations.forEach(out::add)
        ctorParam?.annotations?.forEach(out::add)
        return out
    }

    /**
     * The Java accessor expression for a Kotlin property, in terms of the generated method's `obj`
     * parameter. A `@JvmField val x` is a public field (`obj.x`); a plain `val x` / `var x` exposes a
     * getter — `getX()`, or `isX()` for a `Boolean` property whose name already starts with `is`
     * (Kotlin keeps the `is`-prefixed accessor name as-is).
     */
    private fun accessorFor(property: KSPropertyDeclaration, name: String): String {
        if (property.annotations.any { fqnOf(it) == JVM_FIELD }) {
            return "obj.$name"
        }
        if (isBoolean(property.type.resolve()) && name.startsWith("is") &&
                name.length > 2 && name[2].isUpperCase()) {
            return "obj.$name()"
        }
        val capitalized = name.replaceFirstChar { it.uppercaseChar() }
        return "obj.get$capitalized()"
    }

    private fun write(packageName: String, simpleName: String, source: String,
                      origin: KSClassDeclaration) {
        try {
            val deps = origin.containingFile?.let { Dependencies(aggregating = false, it) }
                    ?: Dependencies(aggregating = false)
            codeGenerator.createNewFile(deps, packageName, simpleName, extensionName = "java")
                    .use { stream ->
                        OutputStreamWriter(stream, StandardCharsets.UTF_8).use { it.write(source) }
                    }
        } catch (e: Exception) {
            logger.error("bmc-constraints: failed to write ${qualify(packageName, simpleName)}:" +
                    " ${e.message}", origin)
        }
    }

    private companion object {

        const val JVM_FIELD = "kotlin.jvm.JvmField"

        /** The jakarta constraint annotations whose presence makes a class a constraints source. */
        val TRIGGER_ANNOTATIONS = listOf(
                "jakarta.validation.constraints.NotNull",
                "jakarta.validation.constraints.Null",
                "jakarta.validation.constraints.Min",
                "jakarta.validation.constraints.Max",
                "jakarta.validation.constraints.Positive",
                "jakarta.validation.constraints.PositiveOrZero",
                "jakarta.validation.constraints.Negative",
                "jakarta.validation.constraints.NegativeOrZero",
                "jakarta.validation.constraints.AssertTrue",
                "jakarta.validation.constraints.AssertFalse",
                "jakarta.validation.constraints.Size",
                "jakarta.validation.constraints.NotEmpty",
                "jakarta.validation.constraints.NotBlank",
                "jakarta.validation.constraints.Past",
                "jakarta.validation.constraints.PastOrPresent",
                "jakarta.validation.constraints.Future",
                "jakarta.validation.constraints.FutureOrPresent",
                "jakarta.validation.constraints.DecimalMin",
                "jakarta.validation.constraints.DecimalMax",
                "jakarta.validation.constraints.Digits",
                "jakarta.validation.Valid")

        fun fqnOf(ann: KSAnnotation): String? =
                ann.annotationType.resolve().declaration.qualifiedName?.asString()

        fun isBoolean(type: KSType): Boolean =
                type.declaration.qualifiedName?.asString() == "kotlin.Boolean"

        fun qualify(packageName: String, simpleName: String): String =
                if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    }
}
