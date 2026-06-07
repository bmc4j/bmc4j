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
            // NOTE on value/inline classes: a mirror that takes or returns one is name-mangled by
            // Kotlin (`f-<hash>`) and OMITTED from kapt's Java stub, so it never reaches this loop —
            // the type ends up binding zero contracts and is rejected loudly below. (We can't report
            // it by name here because the element simply isn't present.)
            val requires = mirror.getAnnotation(Requires::class.java)
            val ensures = mirror.getAnnotation(Ensures::class.java)
            if (requires == null && ensures == null) {
                continue // a predicate method (or other), not a contract mirror
            }
            val name = mirror.simpleName.toString()
            // A `suspend` mirror is lowered to `(args, Continuation)Object`. The contract binds the
            // DECLARED Kotlin shape: the trailing Continuation is coroutine plumbing (hidden from the
            // predicates) and the declared result type is the Continuation's type argument, not the
            // erased `Object` return. Everything else (binding, instance/static, expect) is unchanged.
            val suspend = isSuspend(mirror)
            // Predicate parameters: all mirror parameters except a suspend mirror's trailing Continuation.
            val predicateParams = if (suspend) mirror.parameters.dropLast(1) else mirror.parameters
            val params: List<Map.Entry<String, String>> = predicateParams.map {
                AbstractMap.SimpleImmutableEntry(typeSource(it.asType()), it.simpleName.toString())
            }
            // Resolve the mirror's signature against the target class to learn whether the contracted
            // method is static or a pure instance method (the receiver is then threaded as `self`). A
            // mirror that binds to nothing on the target is an orphan — a production rename or typo;
            // report it now with a clear message rather than letting the generated enforce-proof fail
            // to compile against a missing method. (For a suspend mirror this matches the lowered
            // `(…,Continuation)Object` target the Kotlin compiler emits, so binding works unchanged.)
            val bound = resolveTargetMethod(target, mirror)
            if (bound == null) {
                error(mirror, "no method on ${target.simpleName} matches the contract mirror" +
                        " '$name${signature(mirror)}' — the target may have been renamed or its" +
                        " signature changed; update the mirror or the production method to match")
                continue
            }
            // The declared result type and its boxed reference form for a suspend contract. Out-of-scope
            // suspend result shapes (Flow/streaming, an unrecoverable declared type) are rejected loudly.
            var declaredReturn = typeSource(mirror.returnType)
            var suspendBoxedReturn: String? = null
            if (suspend) {
                val declared = suspendDeclaredReturn(mirror)
                if (declared == null) {
                    error(mirror, "suspend contract mirror '$name' has an unrecoverable declared result" +
                            " type: its lowered Continuation parameter must be" +
                            " kotlin.coroutines.Continuation<? super R> for a concrete R. A type-variable" +
                            " or unbounded result can't be contracted")
                    continue
                }
                if (isFlowType(declared)) {
                    error(mirror, "suspend contract mirror '$name' returns a Flow ($declared):" +
                            " contracts describe a single completed result, not a stream of emissions." +
                            " Streaming/Flow behavior is out of scope — contract a suspend function that" +
                            " returns one value")
                    continue
                }
                declaredReturn = unboxIfBoxedPrimitive(declared)
                suspendBoxedReturn = declared
            }
            val isInstance = !bound.modifiers.contains(javax.lang.model.element.Modifier.STATIC)
            val receiverType = if (isInstance) targetFqn else null
            // Per-method @ExpectEnforce wins over the type-level expectEnforce default, so one
            // contract type can mix a deliberately-false demo mirror with genuine contracts.
            val methodExpect = mirror.getAnnotation(ExpectEnforce::class.java)
            val expectEnforce = methodExpect?.value?.name
                    ?: contractType.getAnnotation(BmcContractsFor::class.java).expectEnforce.name
            contracts.add(ContractStubGenerator.Contract(targetFqn, contractFqn, name,
                    declaredReturn, params,
                    requires?.value, ensures?.value, expectEnforce, receiverType, suspendBoxedReturn))
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
            // A @BmcContractsFor type that binds NO contract is always a mistake, and a SILENT one is
            // the failure mode to kill — so this is a hard error, not a warning. The most common
            // Kotlin cause is a value/inline-class parameter or return type: kapt name-mangles such a
            // method (`f-<hash>`) and OMITS it from the Java stub it feeds javac entirely, so the
            // processor never sees the mirror at all (it can't even be reported by name). Unwrap the
            // value class at the contract boundary — mirror a method whose parameters and return type
            // are plain (non-value) types. (Other causes: every mirror is a `suspend` function, or the
            // type genuinely declares no @Requires/@Ensures method.)
            error(contractType, "@BmcContractsFor type ${contractType.simpleName} binds no contract:" +
                    " no @Requires/@Ensures mirror method was visible. If a mirror takes or returns a" +
                    " Kotlin value/inline class, kapt mangles its name and drops it before the" +
                    " processor runs — unwrap the value class and contract a plain-typed method instead")
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

    /**
     * True if [mirror] is a Kotlin `suspend` function as seen by kapt: its last parameter is a
     * `kotlin.coroutines.Continuation`. Kotlin lowers `suspend fun f(args): T` to
     * `Object f(args, Continuation<? super T>)`, so the Continuation tail is the reliable marker on
     * the javac-visible signature.
     */
    private fun isSuspend(mirror: ExecutableElement): Boolean {
        val last = mirror.parameters.lastOrNull()?.asType() ?: return false
        if (last.kind != TypeKind.DECLARED) {
            return false
        }
        val element = (last as DeclaredType).asElement()
        return element is TypeElement &&
                element.qualifiedName.toString() == "kotlin.coroutines.Continuation"
    }

    /**
     * The DECLARED Kotlin result type of a `suspend` [mirror], recovered from its lowered
     * `Continuation<? super R>` tail parameter, or `null` if it can't be recovered as a concrete type.
     * Kotlin lowers `suspend fun f(args): R` to `Object f(args, Continuation<? super R>)`, so the
     * declared result is the Continuation's single type argument (`R`). A boxed primitive (`Integer`
     * for `Int`, etc.) is left boxed here — the caller unboxes it for the predicate signature.
     *
     * The type argument is a `? super R` wildcard whose **super bound** is `R`. A bare `Continuation`
     * (raw / no type argument) or a type-variable argument yields `null` (unrecoverable).
     */
    private fun suspendDeclaredReturn(mirror: ExecutableElement): String? {
        val last = mirror.parameters.lastOrNull()?.asType() ?: return null
        if (last.kind != TypeKind.DECLARED) {
            return null
        }
        val args = (last as DeclaredType).typeArguments
        if (args.size != 1) {
            return null // raw Continuation — no recoverable declared type
        }
        val arg = args[0]
        // `Continuation<? super R>` -> a WildcardType with super bound R. kapt usually presents the
        // declaration-site `out`-projected argument as a `? super R` wildcard; accept a plain declared
        // argument too (defensive) for the rare invariant projection.
        val resolved: TypeMirror = when (arg.kind) {
            TypeKind.WILDCARD -> (arg as javax.lang.model.type.WildcardType).superBound ?: return null
            TypeKind.DECLARED -> arg
            else -> return null // TYPEVAR, ARRAY-of-typevar, etc. — unrecoverable
        }
        if (resolved.kind != TypeKind.DECLARED) {
            return null
        }
        return typeSource(resolved)
    }

    /** True when [declaredFqn] is a Kotlin `Flow` (a stream of emissions, not a single completed
     *  value) — out of scope for contracts, which describe one completed result. */
    private fun isFlowType(declaredFqn: String): Boolean =
            declaredFqn == "kotlinx.coroutines.flow.Flow" ||
                    declaredFqn == "kotlinx.coroutines.flow.SharedFlow" ||
                    declaredFqn == "kotlinx.coroutines.flow.StateFlow"

    /** Unbox a boxed-primitive FQN to its Kotlin-declared primitive (so the predicate binds `int`, not
     *  `Integer` — kapt lowers a `suspend fun … : Int` predicate parameter to a primitive `int`). A
     *  non-boxed reference type (e.g. java.lang.String) is returned unchanged. */
    private fun unboxIfBoxedPrimitive(boxedFqn: String): String = when (boxedFqn) {
        "java.lang.Integer" -> "int"
        "java.lang.Long" -> "long"
        "java.lang.Short" -> "short"
        "java.lang.Byte" -> "byte"
        "java.lang.Character" -> "char"
        "java.lang.Boolean" -> "boolean"
        "java.lang.Float" -> "float"
        "java.lang.Double" -> "double"
        else -> boxedFqn
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
