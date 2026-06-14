package org.bmc4j.engine

import org.bmc4j.contracts.ContractDefinition
import org.bmc4j.contracts.ContractRegistry
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * The build-time entry point that lowers the contracts DSL ([org.bmc4j.contracts.contractFor]) to
 * generated enforce-proof classes by EXECUTING it (execute, then translate) - the architecture the issue
 * mandates, not a static type scan.
 *
 * For each contracts facade (a `...Kt` class whose `<clinit>` calls `contractFor`) it:
 * 1. loads the facade through a [URLClassLoader] over the contracts classes + their dependencies, which
 *    runs the top-level `val` initializers -> every `contractFor(...) { ... }` block executes and
 *    self-registers a [ContractDefinition] into [ContractRegistry] (in source order);
 * 2. drains that facade's registered definitions and zips them, in source order, against the predicate
 *    implementation handles read by a narrow static decode of the same facade `<clinit>`
 *    ([ContractDslBytecode.lower]);
 * 3. writes one `<Facade>__BmcDslEnforce.class` of generated `@BmcProof` enforce methods.
 *
 * The plugin puts the output dir on the test classpath + JUnit discovery roots, so the generated proofs
 * run like any other `@BmcProof`.
 */
object GradleContractsDsl {

    private const val ENFORCE_SUFFIX = "__BmcDslEnforce"
    private const val DSL_FACADE = "org/bmc4j/contracts/ContractDslKt"

    /**
     * Generate enforce-proof classes for every contracts facade under [contractsClassesDir] into
     * [outputDir]. [runtimeClasspath] are the additional jars/dirs the facade needs to load (bmc-runtime,
     * the contracted app classes, Kotlin stdlib, the consumer's deps). Returns the number of facades
     * lowered.
     */
    @JvmStatic
    fun generate(contractsClassesDir: Path, outputDir: Path, runtimeClasspath: List<Path>): Int {
        if (!Files.isDirectory(contractsClassesDir)) {
            return 0
        }
        Files.createDirectories(outputDir)
        ContractDslBytecode.classRoots = listOf(contractsClassesDir) + runtimeClasspath

        // The classloader that runs the facade <clinit>s: the contracts classes, plus everything they
        // reference (the app under contract, bmc-runtime carrying the DSL + registry, Kotlin stdlib).
        val urls = (listOf(contractsClassesDir) + runtimeClasspath)
                .map { it.toUri().toURL() }.toTypedArray()
        var count = 0
        URLClassLoader(urls, ContractRegistry::class.java.classLoader).use { loader ->
            // The registry must be the one on the PARENT loader (where this code's ContractRegistry lives),
            // so the facade - loaded by the child - resolves the same ContractRegistry and registers into
            // the snapshot we read. bmc-runtime therefore comes from the parent (not the child urls).
            Files.walk(contractsClassesDir).use { stream ->
                stream.filter { it.extension == "class" }
                        .sorted()
                        .forEach { classFile ->
                            val bytes = Files.readAllBytes(classFile)
                            if (!callsContractFor(bytes)) {
                                return@forEach
                            }
                            val facadeInternal = internalNameOf(bytes)
                            val defs = runFacade(loader, facadeInternal)
                            if (defs.isEmpty()) {
                                return@forEach
                            }
                            val lowered = ContractDslBytecode.lower(bytes, defs)
                            // Surface the verdict-taint of any not-fully-proved contract (NONE assumed /
                            // TRUSTED_PURE trusts predicate purity), mirroring the assume-guarantee note.
                            for (note in ContractDslBytecode.taintNotes(lowered)) {
                                println("[bmc4j contracts-DSL] $facadeInternal: $note")
                            }
                            val enforceInternal = facadeInternal + ENFORCE_SUFFIX
                            val enforceBytes =
                                    ContractDslBytecode.generateEnforceClass(enforceInternal, lowered)
                            val target = outputDir.resolve("$enforceInternal.class")
                            Files.createDirectories(target.parent)
                            Files.write(target, enforceBytes)
                            count++
                        }
            }
        }
        return count
    }

    /** Load [facadeInternal] (runs its `<clinit>`, executing the contractFor blocks) and return the
     *  definitions it registered, in source order. The registry is cleared first so each facade's
     *  definitions are isolated. */
    private fun runFacade(loader: ClassLoader, facadeInternal: String): List<ContractDefinition> {
        ContractRegistry.clear()
        val fqn = facadeInternal.replace('/', '.')
        // initialize = true forces <clinit>, which runs the top-level val initializers.
        Class.forName(fqn, true, loader)
        return ContractRegistry.snapshot()
    }

    /** True iff a class's `<clinit>` (or any method) calls a `contractFor` overload on the DSL facade. */
    private fun callsContractFor(bytes: ByteArray): Boolean {
        var calls = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            if (op == Opcodes.INVOKESTATIC && owner == DSL_FACADE && name != null
                                    && (name == "contractFor" || name == "contractFor\$default")) {
                                calls = true
                            }
                        }
                    }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return calls
    }

    private fun internalNameOf(bytes: ByteArray): String {
        var name = ""
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, n: String?, sig: String?,
                               sup: String?, ifs: Array<String>?) {
                name = n ?: ""
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return name
    }
}
