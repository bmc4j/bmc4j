package org.bmc4j.contracts

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import org.bmc4j.engine.ContractEnforceProofGenerator
import org.bmc4j.engine.ContractManifest
import org.bmc4j.engine.ContractStubGenerator
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.AbstractMap

/**
 * The KSP analogue of [ContractProcessor]: for a Kotlin consumer it reads the test-side
 * [org.bmc4j.BmcContractsFor] types and emits the **same** artifacts a javac run would — replace
 * `__BmcStubs`, enforce `__BmcEnforce` `@BmcProof`s, and the [ContractManifest.RESOURCE] lines —
 * by building the identical [ContractStubGenerator.Contract] model and handing it to the shared
 * generators. KSP replaces kapt (deprecated): it runs natively over the Kotlin declarations rather
 * than over kapt's generated Java stubs, so the generated output is byte-for-byte equivalent and
 * downstream proofs are unaffected.
 *
 * The javac [ContractProcessor] stays the AP for pure-Java consumers (`testAnnotationProcessor`);
 * this is the Kotlin path (`kspTest`).
 *
 * KSP sees the **declared** Kotlin shape, not kapt's lowered Java view, so this processor performs
 * the lowering the generators expect itself:
 * - a `suspend fun f(args): R` is detected by [Modifier.SUSPEND]; its call-site/manifest descriptor
 *   is the lowered `(args, Continuation)Object` ABI, and the contract carries the boxed declared
 *   result so the stub/enforce box on return and unbox before the predicate (identical to the kapt
 *   path, which observed the already-lowered signature);
 * - a value/inline-class parameter or return type is visible here (kapt dropped it), and is rejected
 *   loudly with the same "binds no contract" guidance the javac path produced.
 */
class ContractSymbolProcessor(
        private val codeGenerator: CodeGenerator,
        private val logger: KSPLogger) : SymbolProcessor {

    private val manifestLines = mutableListOf<String>()
    private val generated = LinkedHashSet<String>()
    /** Origin files of every generated artifact, for KSP incremental dependency tracking. */
    private val originFiles = LinkedHashSet<com.google.devtools.ksp.symbol.KSFile>()
    private var manifestWritten = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(BMC_CONTRACTS_FOR)
                .filterIsInstance<KSClassDeclaration>()
                .forEach(::generateFor)
        return emptyList()
    }

    override fun finish() {
        writeManifest()
    }

    private fun generateFor(contractType: KSClassDeclaration) {
        val contractFqn = contractType.qualifiedName?.asString() ?: return
        if (!generated.add(contractFqn)) {
            return
        }
        val target = targetOf(contractType) ?: return // error already reported
        val targetFqn = target.qualifiedName?.asString() ?: return
        val targetInternal = internalName(target)
        val packageName = contractType.packageName.asString()

        val stubSimple = "${contractType.simpleName.asString()}__BmcStubs"
        val enforceSimple = "${contractType.simpleName.asString()}__BmcEnforce"
        val stubInternal = qualify(packageName, stubSimple).replace('.', '/')

        val contracts = mutableListOf<ContractStubGenerator.Contract>()
        val contractRecords = mutableListOf<String>()

        for (mirror in contractType.getDeclaredFunctions()) {
            val requires = annotationValue(mirror, REQUIRES)
            val ensures = annotationValue(mirror, ENSURES)
            if (requires == null && ensures == null) {
                continue // a predicate method (or other), not a contract mirror
            }
            val name = mirror.simpleName.asString()
            // KSP reports the DECLARED Kotlin shape: `suspend` is a modifier (kapt instead lowered it
            // to a trailing Continuation parameter). The predicates bind the declared parameters; the
            // Continuation is synthesized into the descriptor/stub/enforce below.
            val suspend = mirror.modifiers.contains(Modifier.SUSPEND)
            // A value/inline-class parameter or return type is name-mangled by Kotlin and can't be
            // contracted (the javac path never even saw it — kapt dropped it). Reject loudly here.
            if (rejectsValueClass(mirror, name)) {
                continue
            }
            val params: List<Map.Entry<String, String>> = mirror.parameters.map {
                AbstractMap.SimpleImmutableEntry(
                        typeSource(it.type.resolve()), it.name?.asString() ?: "p")
            }
            // Resolve the mirror's signature against the target class to learn static vs pure-instance
            // (the receiver is then threaded as `self`). An orphan (production rename/typo) is reported.
            val bound = resolveTargetMethod(target, mirror)
            if (bound == null) {
                logger.error("bmc-contracts: no method on ${target.simpleName.asString()} matches the" +
                        " contract mirror '$name${signature(mirror)}' — the target may have been" +
                        " renamed or its signature changed; update the mirror or the production method" +
                        " to match", mirror)
                continue
            }
            // The declared result type and its boxed reference form for a suspend contract. Out-of-scope
            // suspend result shapes (Flow/streaming) are rejected loudly.
            var declaredReturn = typeSource(mirror.returnType!!.resolve())
            var suspendBoxedReturn: String? = null
            if (suspend) {
                val declared = boxedReference(mirror.returnType!!.resolve())
                if (isFlowType(declared)) {
                    logger.error("bmc-contracts: suspend contract mirror '$name' returns a Flow" +
                            " ($declared): contracts describe a single completed result, not a stream of" +
                            " emissions. Streaming/Flow behavior is out of scope — contract a suspend" +
                            " function that returns one value", mirror)
                    continue
                }
                declaredReturn = unboxIfBoxedPrimitive(declared)
                suspendBoxedReturn = declared
            }
            val isInstance = !isStatic(bound)
            val receiverType = if (isInstance) targetFqn else null
            // Per-method @ExpectEnforce wins over the type-level expectEnforce default.
            val expectEnforce = expectEnforceOf(mirror) ?: typeExpectEnforce(contractType)
            contracts.add(ContractStubGenerator.Contract(targetFqn, contractFqn, name,
                    declaredReturn, params,
                    requires, ensures, expectEnforce, receiverType, suspendBoxedReturn))
            // SOUNDNESS: only a VERIFIED contract publishes a reusable redirect (a non-VERIFIED
            // contract's __stub would summarize callers against a postcondition the framework knows is
            // not discharged). The __BmcEnforce proof is still generated for every contract.
            if (expectEnforce == "VERIFIED") {
                val instanceDesc = descriptor(mirror, suspend)
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
            logger.error("bmc-contracts: @BmcContractsFor type ${contractType.simpleName.asString()}" +
                    " binds no contract: no @Requires/@Ensures mirror method was visible. If a mirror" +
                    " takes or returns a Kotlin value/inline class, its JVM name is mangled and can't be" +
                    " contracted — unwrap the value class and contract a plain-typed method instead",
                    contractType)
            return
        }
        contractType.containingFile?.let(originFiles::add)
        write(qualify(packageName, stubSimple),
                ContractStubGenerator.generate(packageName, stubSimple, contracts))
        write(qualify(packageName, enforceSimple),
                ContractEnforceProofGenerator.generate(packageName, enforceSimple, contracts))
        manifestLines.addAll(contractRecords)
        manifestLines.add(ContractManifest.enforceLine(
                qualify(packageName, enforceSimple).replace('.', '/')))
    }

    /**
     * The method on [target] (or a supertype) whose name and erased parameter descriptors match the
     * contract [mirror], or `null` if none does. Binding is by signature, exactly like the javac path,
     * so an orphaned mirror resolves to `null`. The match determines static vs pure-instance.
     */
    private fun resolveTargetMethod(target: KSClassDeclaration, mirror: KSFunctionDeclaration):
            KSFunctionDeclaration? {
        val name = mirror.simpleName.asString()
        val want = mirror.parameters.map { erasedDescriptor(it.type.resolve()) }
        for (candidate in target.getAllFunctions()) {
            if (candidate.simpleName.asString() != name) {
                continue
            }
            val have = candidate.parameters.map { erasedDescriptor(it.type.resolve()) }
            if (have == want) {
                return candidate
            }
        }
        return null
    }

    /** True if [fn] compiles to a static method (top-level / `object` `@JvmStatic` / companion
     *  `@JvmStatic`): the contract then binds the static shape and threads no receiver. Otherwise the
     *  contract is pure-instance. */
    private fun isStatic(fn: KSFunctionDeclaration): Boolean {
        if (hasAnnotation(fn, JVM_STATIC)) {
            return true
        }
        val owner = fn.parentDeclaration
        // A function whose enclosing declaration is not a class/interface (i.e. a top-level/file
        // function) is static on the JVM. A member of a class/interface is instance unless @JvmStatic.
        return owner !is KSClassDeclaration
    }

    /** Reject (and report) a mirror that takes or returns a Kotlin value/inline class — its JVM name
     *  is mangled, so the contract can't bind. Returns true when rejected. */
    private fun rejectsValueClass(mirror: KSFunctionDeclaration, name: String): Boolean {
        val offending = (mirror.parameters.map { it.type.resolve() } + mirror.returnType!!.resolve())
                .firstOrNull { isValueClass(it) } ?: return false
        logger.error("bmc-contracts: contract mirror '$name' takes or returns the Kotlin value/inline" +
                " class ${typeSource(offending)} — its JVM name is mangled and can't be contracted." +
                " Unwrap the value class at the contract boundary and mirror a plain-typed method" +
                " instead", mirror)
        return true
    }

    private fun isValueClass(type: KSType): Boolean {
        val decl = type.declaration as? KSClassDeclaration ?: return false
        return decl.modifiers.contains(Modifier.VALUE) || decl.modifiers.contains(Modifier.INLINE)
    }

    /** A readable `(int, java.lang.String)` parameter list for diagnostics. */
    private fun signature(mirror: KSFunctionDeclaration): String =
            mirror.parameters.joinToString(", ", "(", ")") { typeSource(it.type.resolve()) }

    /** Erased JVM descriptor of a single type, for signature-matching the target method. */
    private fun erasedDescriptor(t: KSType): String = typeDescriptor(t)

    /** The production class named by `@BmcContractsFor(value)`. */
    private fun targetOf(contractType: KSClassDeclaration): KSClassDeclaration? {
        val annotation = contractType.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == BMC_CONTRACTS_FOR
        } ?: return null
        val value = annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value
                ?: annotation.arguments.firstOrNull()?.value
        val type = value as? KSType
        val decl = type?.declaration as? KSClassDeclaration
        if (decl == null) {
            logger.warn("bmc-contracts: @BmcContractsFor value must be a class", contractType)
            return null
        }
        return decl
    }

    private fun writeManifest() {
        if (manifestWritten || manifestLines.isEmpty()) {
            return
        }
        manifestWritten = true
        try {
            codeGenerator.createNewFileByPath(
                    Dependencies(aggregating = true, *originFiles.toTypedArray()),
                    ContractManifest.RESOURCE,
                    extensionName = "").use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { w ->
                    manifestLines.forEach { line ->
                        w.write(line)
                        w.write("\n")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("bmc-contracts: failed to write ${ContractManifest.RESOURCE}: ${e.message}")
        }
    }

    private fun write(generatedFqn: String, source: String) {
        val packageName = generatedFqn.substringBeforeLast('.', "")
        val simpleName = generatedFqn.substringAfterLast('.')
        try {
            codeGenerator.createNewFile(
                    Dependencies(aggregating = true, *originFiles.toTypedArray()),
                    packageName, simpleName, extensionName = "java").use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { it.write(source) }
            }
        } catch (e: Exception) {
            logger.error("bmc-contracts: failed to write $generatedFqn: ${e.message}")
        }
    }

    private fun internalName(type: KSClassDeclaration): String =
            binaryName(type).replace('.', '/')

    /** The JVM binary name (nested types joined with `$`) of a class declaration. */
    private fun binaryName(type: KSClassDeclaration): String {
        val pkg = type.packageName.asString()
        val names = generateSequence(type as com.google.devtools.ksp.symbol.KSDeclaration) {
            it.parentDeclaration
        }.takeWhile { it is KSClassDeclaration }
                .map { it.simpleName.asString() }
                .toList()
                .asReversed()
        val nested = names.joinToString("$")
        return if (pkg.isEmpty()) nested else "$pkg.$nested"
    }

    /** Source-usable type name (canonical, generics erased) for codegen. */
    private fun typeSource(t: KSType): String {
        val decl = t.declaration
        val qn = decl.qualifiedName?.asString()
        // Map Kotlin built-ins to their Java source form, matching what kapt's javac stub presented.
        return when (qn) {
            "kotlin.Int" -> "int"
            "kotlin.Long" -> "long"
            "kotlin.Short" -> "short"
            "kotlin.Byte" -> "byte"
            "kotlin.Char" -> "char"
            "kotlin.Boolean" -> "boolean"
            "kotlin.Float" -> "float"
            "kotlin.Double" -> "double"
            "kotlin.Unit" -> "void"
            "kotlin.String" -> "java.lang.String"
            "kotlin.Any" -> "java.lang.Object"
            "kotlin.IntArray" -> "int[]"
            "kotlin.LongArray" -> "long[]"
            "kotlin.ShortArray" -> "short[]"
            "kotlin.ByteArray" -> "byte[]"
            "kotlin.CharArray" -> "char[]"
            "kotlin.BooleanArray" -> "boolean[]"
            "kotlin.FloatArray" -> "float[]"
            "kotlin.DoubleArray" -> "double[]"
            "kotlin.Array" -> typeSource(t.arguments.first().type!!.resolve()) + "[]"
            else -> mapKotlinReference(qn) ?: qn ?: "java.lang.Object"
        }
    }

    /** Boxed reference form of a type (a primitive becomes its java.lang wrapper); used for a suspend
     *  contract's declared (Continuation type-argument) result, which is always a reference at the ABI. */
    private fun boxedReference(t: KSType): String = when (typeSource(t)) {
        "int" -> "java.lang.Integer"
        "long" -> "java.lang.Long"
        "short" -> "java.lang.Short"
        "byte" -> "java.lang.Byte"
        "char" -> "java.lang.Character"
        "boolean" -> "java.lang.Boolean"
        "float" -> "java.lang.Float"
        "double" -> "java.lang.Double"
        "void" -> "kotlin.Unit"
        else -> typeSource(t)
    }

    /** JVM method descriptor. For a suspend mirror this is the LOWERED ABI:
     *  `(declared-params, Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`. */
    private fun descriptor(method: KSFunctionDeclaration, suspend: Boolean): String = buildString {
        append('(')
        method.parameters.forEach { append(typeDescriptor(it.type.resolve())) }
        if (suspend) {
            append("Lkotlin/coroutines/Continuation;")
            append(')')
            append("Ljava/lang/Object;")
        } else {
            append(')')
            append(typeDescriptor(method.returnType!!.resolve()))
        }
    }

    private fun typeDescriptor(t: KSType): String {
        val qn = t.declaration.qualifiedName?.asString()
        return when (qn) {
            "kotlin.Boolean" -> "Z"
            "kotlin.Byte" -> "B"
            "kotlin.Char" -> "C"
            "kotlin.Short" -> "S"
            "kotlin.Int" -> "I"
            "kotlin.Long" -> "J"
            "kotlin.Float" -> "F"
            "kotlin.Double" -> "D"
            "kotlin.Unit" -> "V"
            "kotlin.IntArray" -> "[I"
            "kotlin.LongArray" -> "[J"
            "kotlin.ShortArray" -> "[S"
            "kotlin.ByteArray" -> "[B"
            "kotlin.CharArray" -> "[C"
            "kotlin.BooleanArray" -> "[Z"
            "kotlin.FloatArray" -> "[F"
            "kotlin.DoubleArray" -> "[D"
            "kotlin.Array" -> "[" + typeDescriptor(t.arguments.first().type!!.resolve())
            else -> {
                val decl = t.declaration as? KSClassDeclaration
                        ?: return "Ljava/lang/Object;"
                "L" + internalName(decl) + ";"
            }
        }
    }

    /** The string value of a single-arg `@Requires`/`@Ensures` (or null if the annotation is absent). */
    private fun annotationValue(fn: KSFunctionDeclaration, fqn: String): String? {
        val annotation = fn.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
        } ?: return null
        return annotation.arguments.firstOrNull()?.value as? String
    }

    private fun hasAnnotation(fn: KSFunctionDeclaration, fqn: String): Boolean =
            fn.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
            }

    /** Method-level @ExpectEnforce value name, or null. */
    private fun expectEnforceOf(fn: KSFunctionDeclaration): String? =
            enumArgName(fn.annotations.firstOrNull {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == EXPECT_ENFORCE
            }?.arguments?.firstOrNull()?.value)

    /** Type-level expectEnforce default from @BmcContractsFor(expectEnforce = ...), default VERIFIED. */
    private fun typeExpectEnforce(contractType: KSClassDeclaration): String {
        val annotation = contractType.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == BMC_CONTRACTS_FOR
        } ?: return "VERIFIED"
        val arg = annotation.arguments.firstOrNull { it.name?.asString() == "expectEnforce" }?.value
        return enumArgName(arg) ?: "VERIFIED"
    }

    /** The simple name of an enum-valued annotation argument as KSP reports it (a KSType for the enum
     *  entry, or the entry declaration), so we get e.g. "REFUTED". */
    private fun enumArgName(value: Any?): String? = when (value) {
        null -> null
        is KSType -> value.declaration.simpleName.asString()
        is com.google.devtools.ksp.symbol.KSClassDeclaration -> value.simpleName.asString()
        else -> value.toString().substringAfterLast('.')
    }

    /** True when [declaredFqn] is a Kotlin `Flow` (out of scope for contracts). */
    private fun isFlowType(declaredFqn: String): Boolean =
            declaredFqn == "kotlinx.coroutines.flow.Flow" ||
                    declaredFqn == "kotlinx.coroutines.flow.SharedFlow" ||
                    declaredFqn == "kotlinx.coroutines.flow.StateFlow"

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

    private companion object {
        const val BMC_CONTRACTS_FOR = "org.bmc4j.BmcContractsFor"
        const val REQUIRES = "org.bmc4j.Requires"
        const val ENSURES = "org.bmc4j.Ensures"
        const val EXPECT_ENFORCE = "org.bmc4j.ExpectEnforce"
        const val JVM_STATIC = "kotlin.jvm.JvmStatic"

        fun qualify(packageName: String, simpleName: String): String =
                if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"

        /** Map a Kotlin platform type's FQN to the Java source class it lowers to (mirroring kapt's
         *  javac stub), for the handful that differ from a 1:1 name. Returns null to keep the FQN. */
        fun mapKotlinReference(qn: String?): String? = when (qn) {
            null -> null
            "kotlin.CharSequence" -> "java.lang.CharSequence"
            "kotlin.Number" -> "java.lang.Number"
            "kotlin.Comparable" -> "java.lang.Comparable"
            "kotlin.Throwable" -> "java.lang.Throwable"
            "kotlin.collections.List", "kotlin.collections.MutableList" -> "java.util.List"
            "kotlin.collections.Map", "kotlin.collections.MutableMap" -> "java.util.Map"
            "kotlin.collections.Set", "kotlin.collections.MutableSet" -> "java.util.Set"
            "kotlin.collections.Collection", "kotlin.collections.MutableCollection" ->
                "java.util.Collection"
            "kotlin.collections.Iterable", "kotlin.collections.MutableIterable" ->
                "java.lang.Iterable"
            else -> null
        }
    }
}
