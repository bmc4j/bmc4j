package org.bmc4j.contracts

import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.ExpectEnforce
import org.bmc4j.Requires
import org.bmc4j.engine.ContractEnforceProofGenerator
import org.bmc4j.engine.ContractManifest
import org.bmc4j.engine.ContractStubGenerator
import java.io.IOException
import java.util.AbstractMap
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * Turns a test-side [BmcContractsFor] type into the artifacts the runtime needs for
 * modular (assume-guarantee) proofs, keeping `@Requires`/`@Ensures`
 * and their predicates **out of production code**. The annotated type mirrors a production
 * class's methods by signature and holds the predicates; for each contract this generates:
 *
 * - a **`<Contract>__BmcStubs`** class — the replace-direction summary
 *   (`assert requires; nondet; assume ensures; return`) the call-site rewriter
 *   redirects the *target* method to;
 * - a **`<Contract>__BmcEnforce`** class — one `@BmcProof` per contract that
 *   calls the *real* target method and asserts `@Ensures`, so a false contract
 *   turns the build red ("annotate != proven" is structural);
 * - lines in [ContractManifest.RESOURCE] mapping the target
 *   method (owner/name/descriptor) to the stub, and naming each enforce class.
 *
 * v1 targets `static`, value-returning methods; predicates are non-private static
 * `boolean` methods on the contract type.
 */
@SupportedAnnotationTypes("org.bmc4j.BmcContractsFor")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class ContractProcessor : AbstractProcessor() {

    private val manifestLines = mutableListOf<String>()
    private val generated = LinkedHashSet<String>()
    private var manifestWritten = false

    override fun process(annotations: Set<TypeElement>, round: RoundEnvironment): Boolean {
        if (round.processingOver()) {
            writeManifest()
            return false
        }
        round.getElementsAnnotatedWith(BmcContractsFor::class.java)
                .filterIsInstance<TypeElement>()
                .forEach(::generateFor)
        return false
    }

    private fun generateFor(contractType: TypeElement) {
        val contractFqn = contractType.qualifiedName.toString()
        if (!generated.add(contractFqn)) {
            return
        }
        val target = targetOf(contractType) ?: return // error already reported
        val targetFqn = target.qualifiedName.toString()
        val targetInternal = internalName(target)
        val packageName = processingEnv.elementUtils.getPackageOf(contractType)
                .qualifiedName.toString()

        val stubSimple = "${contractType.simpleName}__BmcStubs"
        val enforceSimple = "${contractType.simpleName}__BmcEnforce"
        val stubInternal = qualify(packageName, stubSimple).replace('.', '/')

        val contracts = mutableListOf<ContractStubGenerator.Contract>()
        val contractRecords = mutableListOf<String>()

        for (member in contractType.enclosedElements) {
            if (member.kind != ElementKind.METHOD) {
                continue
            }
            val mirror = member as ExecutableElement
            val requires = mirror.getAnnotation(Requires::class.java)
            val ensures = mirror.getAnnotation(Ensures::class.java)
            if (requires == null && ensures == null) {
                continue // a predicate method (or other), not a contract mirror
            }
            val name = mirror.simpleName.toString()
            val params: List<Map.Entry<String, String>> = mirror.parameters.map {
                AbstractMap.SimpleImmutableEntry(typeSource(it.asType()), it.simpleName.toString())
            }
            // Resolve the mirror's signature against the target class to learn whether the contracted
            // method is static or a pure instance method (the receiver is then threaded as `self`). A
            // mirror that binds to nothing on the target is an orphan — a production rename or typo;
            // report it now with a clear message rather than letting the generated enforce-proof fail
            // to compile against a missing method.
            val bound = resolveTargetMethod(target, mirror)
            if (bound == null) {
                error(mirror, "no method on ${target.simpleName} matches the contract mirror" +
                        " '$name${signature(mirror)}' — the target may have been renamed or its" +
                        " signature changed; update the mirror or the production method to match")
                continue
            }
            val isInstance = !bound.modifiers.contains(javax.lang.model.element.Modifier.STATIC)
            val receiverType = if (isInstance) targetFqn else null
            // Per-method @ExpectEnforce wins over the type-level expectEnforce default, so one
            // contract type can mix a deliberately-false demo mirror with genuine contracts.
            val methodExpect = mirror.getAnnotation(ExpectEnforce::class.java)
            val expectEnforce = methodExpect?.value?.name
                    ?: contractType.getAnnotation(BmcContractsFor::class.java).expectEnforce.name
            contracts.add(ContractStubGenerator.Contract(targetFqn, contractFqn, name,
                    typeSource(mirror.returnType), params,
                    requires?.value, ensures?.value, expectEnforce, receiverType))
            // SOUNDNESS: only a contract whose enforce-proof is expected to VERIFY may publish a
            // reusable redirect. A non-VERIFIED contract (@ExpectEnforce REFUTED/VACUOUS, or a
            // type-level non-VERIFIED expectEnforce) declares the framework KNOWS its @Ensures is
            // not discharged — its __stub assume(<ensures>) would summarize callers against a FALSE
            // postcondition, a false green. Its __BmcEnforce proof is STILL generated below (the
            // refutation/vacuity demo must keep running); we just emit no `contract` redirect line,
            // so JbmcBackend never rewrites any other proof's call sites to that stub.
            if (expectEnforce == "VERIFIED") {
                // The call-site descriptor: the instance descriptor (no receiver) for matching the
                // virtual call, plus the receiver-prepended descriptor for the static stub.
                val instanceDesc = descriptor(mirror)
                val stubDesc = if (isInstance) {
                    "(L$targetInternal;" + instanceDesc.removePrefix("(")
                } else {
                    instanceDesc
                }
                contractRecords.add(ContractManifest.contractLine(
                        targetInternal, name, instanceDesc, stubInternal, "${name}__stub",
                        isInstance, stubDesc))
            }
        }
        if (contracts.isEmpty()) {
            warn(contractType, "@BmcContractsFor type has no @Requires/@Ensures mirror methods")
            return
        }

        write(qualify(packageName, stubSimple), contractType,
                ContractStubGenerator.generate(packageName, stubSimple, contracts))
        write(qualify(packageName, enforceSimple), contractType,
                ContractEnforceProofGenerator.generate(packageName, enforceSimple, contracts))
        manifestLines.addAll(contractRecords)
        manifestLines.add(ContractManifest.enforceLine(
                qualify(packageName, enforceSimple).replace('.', '/')))
    }

    /**
     * The method on [target] (or a supertype, via [javax.lang.model.util.Elements]) whose name and
     * erased parameter types match the contract [mirror], or `null` if none does. Binding is by
     * signature — exactly like a `src/bmcModel` model binds to its class — so an orphaned mirror (a
     * production rename) resolves to `null` and is reported. The match determines whether the
     * contract is static or pure-instance (the caller reads the resolved method's `STATIC` modifier).
     */
    private fun resolveTargetMethod(target: TypeElement, mirror: ExecutableElement): ExecutableElement? {
        val name = mirror.simpleName.toString()
        val want = mirror.parameters.map { erasedDescriptor(it.asType()) }
        for (member in processingEnv.elementUtils.getAllMembers(target)) {
            if (member.kind != ElementKind.METHOD || member.simpleName.toString() != name) {
                continue
            }
            val candidate = member as ExecutableElement
            val have = candidate.parameters.map { erasedDescriptor(it.asType()) }
            if (have == want) {
                return candidate
            }
        }
        return null
    }

    /** A readable `(int, java.lang.String)` parameter list for diagnostics. */
    private fun signature(mirror: ExecutableElement): String =
            mirror.parameters.joinToString(", ", "(", ")") { typeSource(it.asType()) }

    /** Erased JVM descriptor of a single type, for signature-matching the target method. */
    private fun erasedDescriptor(t: TypeMirror): String = typeDescriptor(
            processingEnv.typeUtils.erasure(t))

    /** The production class named by `@BmcContractsFor(value)`. */
    private fun targetOf(contractType: TypeElement): TypeElement? {
        try {
            contractType.getAnnotation(BmcContractsFor::class.java).value // throws — value is a Class
            return null
        } catch (mte: MirroredTypeException) {
            val m = mte.typeMirror
            if (m is DeclaredType) {
                return m.asElement() as TypeElement
            }
            warn(contractType, "@BmcContractsFor value must be a class")
            return null
        }
    }

    private fun writeManifest() {
        if (manifestWritten || manifestLines.isEmpty()) {
            return
        }
        manifestWritten = true
        try {
            processingEnv.filer
                    .createResource(StandardLocation.CLASS_OUTPUT, "", ContractManifest.RESOURCE)
                    .openWriter().use { w ->
                        manifestLines.forEach { line ->
                            w.write(line)
                            w.write("\n")
                        }
                    }
        } catch (e: IOException) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                    "bmc-contracts: failed to write ${ContractManifest.RESOURCE}: ${e.message}")
        }
    }

    private fun write(generatedFqn: String, origin: TypeElement, source: String) {
        try {
            processingEnv.filer.createSourceFile(generatedFqn, origin)
                    .openWriter().use { it.write(source) }
        } catch (e: IOException) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                    "bmc-contracts: failed to write $generatedFqn: ${e.message}", origin)
        }
    }

    private fun warn(at: Element, message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "bmc-contracts: $message", at)
    }

    private fun error(at: Element, message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "bmc-contracts: $message", at)
    }

    private fun internalName(type: TypeElement): String =
            processingEnv.elementUtils.getBinaryName(type).toString().replace('.', '/')

    /** Source-usable type name (canonical, generics erased) for codegen. */
    private fun typeSource(t: TypeMirror): String = when (t.kind) {
        TypeKind.ARRAY -> typeSource((t as ArrayType).componentType) + "[]"
        TypeKind.DECLARED -> ((t as DeclaredType).asElement() as TypeElement).qualifiedName.toString()
        else -> t.toString() // primitives, void
    }

    /** JVM method descriptor, e.g. `(ILjava/lang/String;)I`. */
    private fun descriptor(method: ExecutableElement): String = buildString {
        append('(')
        method.parameters.forEach { append(typeDescriptor(it.asType())) }
        append(')')
        append(typeDescriptor(method.returnType))
    }

    private fun typeDescriptor(t: TypeMirror): String = when (t.kind) {
        TypeKind.BOOLEAN -> "Z"
        TypeKind.BYTE -> "B"
        TypeKind.CHAR -> "C"
        TypeKind.SHORT -> "S"
        TypeKind.INT -> "I"
        TypeKind.LONG -> "J"
        TypeKind.FLOAT -> "F"
        TypeKind.DOUBLE -> "D"
        TypeKind.VOID -> "V"
        TypeKind.ARRAY -> "[" + typeDescriptor((t as ArrayType).componentType)
        TypeKind.DECLARED -> "L" + internalName((t as DeclaredType).asElement() as TypeElement) + ";"
        else -> throw IllegalArgumentException("unsupported type in contract descriptor: $t")
    }

    companion object {
        private fun qualify(packageName: String, simpleName: String): String =
                if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    }
}
