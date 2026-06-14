package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Path

/**
 * Unit tests for [ContractDslBytecode]: STATIC decoding of a `contractFor(...)` registration from its
 * `LambdaMetafactory` bootstrap handles (no invokedynamic execution), and the shape of the generated
 * enforce-proof class. END-TO-END soundness (a VERIFIED enforce-proof, a deliberately-false one that
 * REFUTES) is pinned by the `dslbasics` example proofs.
 */
internal class ContractDslBytecodeTest {

    /** The directory root holding the compiled test fixtures (so the decoder can read nested ref classes). */
    private fun classRoot(): Path {
        val res = javaClass.classLoader.getResource(
                "org/bmc4j/engine/ContractDslBytecodeTestFixtures.class")!!
        // .../org/bmc4j/engine/ContractDslBytecodeTestFixtures.class -> strip the package path to the root.
        var p = Path.of(res.toURI())
        repeat(4) { p = p.parent } // engine, bmc4j, org, root
        return p
    }

    private fun fixtureBytes(): ByteArray {
        val resource = "org/bmc4j/engine/ContractDslBytecodeTestFixtures.class"
        return javaClass.classLoader.getResourceAsStream(resource)!!.use { it.readAllBytes() }
    }

    private fun decode(): List<ContractDslBytecode.Decoded> {
        ContractDslBytecode.classRoots = listOf(classRoot())
        return ContractDslBytecode.decode(fixtureBytes(), "VERIFIED")
    }

    @Test
    fun decodes_the_target_and_predicate_bodies() {
        val d = decode().single()
        assertEquals("org/bmc4j/engine/DslFixtureTarget", d.targetOwner)
        assertEquals("scale", d.targetName)
        assertEquals("(I)I", d.targetDesc)
        assertEquals("VERIFIED", d.expect)
        // The precondition body binds (self, amount); the postcondition body binds (before, after, amount,
        // ret). The bodies are the compiled lambda synthetics on the registration class.
        assertEquals(2, org.objectweb.asm.Type.getArgumentTypes(d.pre.desc).size)
        assertEquals(4, org.objectweb.asm.Type.getArgumentTypes(d.post.desc).size)
        assertEquals("enforce__scale", d.enforceMethod)
    }

    @Test
    fun generates_a_bmcproof_enforce_method_calling_the_real_body() {
        val decoded = decode()
        val bytes = ContractDslBytecode.generateEnforceClass("org/bmc4j/engine/GenEnforce", decoded)

        var hasBmcProof = false
        var callsRealBody = false
        var checksPost = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, dsc: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != "enforce__scale") {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptor: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
                        if (descriptor == "Lorg/bmc4j/BmcProof;") {
                            hasBmcProof = true
                        }
                        return null
                    }

                    override fun visitMethodInsn(op: Int, owner: String?, name: String?, d: String?,
                                                 itf: Boolean) {
                        if (op == Opcodes.INVOKEVIRTUAL && owner == "org/bmc4j/engine/DslFixtureTarget"
                                && name == "scale") {
                            callsRealBody = true
                        }
                        if (op == Opcodes.INVOKESTATIC && owner == "org/bmc4j/Bmc" && name == "check") {
                            checksPost = true
                        }
                    }
                }
            }
        }, 0)

        assertTrue(hasBmcProof, "the enforce method must carry @BmcProof")
        assertTrue(callsRealBody, "the enforce proof must call the REAL target body")
        assertTrue(checksPost, "the enforce proof must check the postcondition")
    }
}
