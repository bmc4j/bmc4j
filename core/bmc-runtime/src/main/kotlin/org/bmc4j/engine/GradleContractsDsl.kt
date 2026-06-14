package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * The build-time entry point that lowers the contracts DSL ([org.bmc4j.contracts.contractFor]) to
 * generated enforce-proof classes. Invoked from the bmc4j Gradle plugin's `bmcContractsDsl` task, in a
 * classloader-isolated worker whose classpath is bmc-runtime - the same indirection
 * [GradleClasspathMirror] uses, so the plugin's own ABI never links bmc-runtime.
 *
 * It walks [testClassesDir] for classes annotated `@org.bmc4j.BmcContracts`, decodes every
 * `contractFor(...)` site in each ([ContractDslBytecode.decode]), and writes one
 * `<Class>__BmcDslEnforce.class` per registration into [outputDir]. The plugin puts [outputDir] on the
 * test classpath, so JUnit discovers the generated `@BmcProof`s and runs them like any other proof.
 */
object GradleContractsDsl {

    private const val ANNOTATION = "Lorg/bmc4j/BmcContracts;"
    private const val ENFORCE_SUFFIX = "__BmcDslEnforce"

    /**
     * Generate enforce-proof classes for every `@BmcContracts` registration under [testClassesDir] into
     * [outputDir]. Returns the number of registrations lowered (0 when the consumer declares no DSL
     * contracts - the task then produces an empty dir, inert on the classpath).
     */
    @JvmStatic
    fun generate(testClassesDir: Path, outputDir: Path): Int {
        if (!Files.isDirectory(testClassesDir)) {
            return 0
        }
        Files.createDirectories(outputDir)
        // The decoder reads nested callable-reference classes (the member `Type::member` is a synthetic
        // nested class of the registration) from the test-classes root.
        ContractDslBytecode.classRoots = listOf(testClassesDir)
        var count = 0
        Files.walk(testClassesDir).use { stream ->
            stream.filter { it.extension == "class" && !it.name.contains('$') }.forEach { classFile ->
                val bytes = Files.readAllBytes(classFile)
                val expect = registrationExpect(bytes) ?: return@forEach
                val internalName = internalNameOf(bytes)
                val decoded = ContractDslBytecode.decode(bytes, expect)
                if (decoded.isEmpty()) {
                    return@forEach
                }
                val enforceInternal = internalName + ENFORCE_SUFFIX
                val enforceBytes = ContractDslBytecode.generateEnforceClass(enforceInternal, decoded)
                val target = outputDir.resolve("$enforceInternal.class")
                Files.createDirectories(target.parent)
                Files.write(target, enforceBytes)
                count++
            }
        }
        return count
    }

    /** The `expectEnforce` verdict name of a class's `@BmcContracts` annotation, or null when the class is
     *  not a registration (no annotation). Defaults to "VERIFIED" when the annotation omits the value. */
    private fun registrationExpect(bytes: ByteArray): String? {
        var expect: String? = null
        var annotated = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean) =
                    if (descriptor == ANNOTATION) {
                        annotated = true
                        object : org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                            override fun visitEnum(name: String?, descriptor: String?, value: String?) {
                                if (name == "expectEnforce") {
                                    expect = value
                                }
                            }
                        }
                    } else {
                        null
                    }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return if (annotated) (expect ?: "VERIFIED") else null
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
