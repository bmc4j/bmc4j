package org.bmc4j.engine

import org.bmc4j.contracts.ContractDefinition
import org.bmc4j.contracts.ContractRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Path

/**
 * Unit tests for the contracts-DSL lowering. They execute the fixture facade (so its top-level `val`
 * contracts self-register), then decode the same facade's bytecode and zip it against the registered
 * definitions - the execute-then-translate pipeline. END-TO-END soundness (the generated proofs VERIFY /
 * REFUTE under JBMC) is pinned by the `examples/contracts-kotlin` DSL proofs.
 */
internal class ContractDslBytecodeTest {

    private fun classRoot(): Path {
        val res = javaClass.classLoader.getResource(
                "org/bmc4j/engine/DslFixtureAccount.class")!!
        var p = Path.of(res.toURI())
        repeat(4) { p = p.parent } // engine, bmc4j, org, root
        return p
    }

    private fun facadeBytes(): ByteArray {
        val resource = "org/bmc4j/engine/ContractDslBytecodeTestFixturesKt.class"
        return javaClass.classLoader.getResourceAsStream(resource)!!.use { it.readAllBytes() }
    }

    private fun lower(): List<ContractDslBytecode.Lowered> {
        ContractDslBytecode.classRoots = listOf(classRoot())
        return ContractDslBytecode.lower(facadeBytes(), defs)
    }

    companion object {
        /** The facade `<clinit>` runs ONCE per classloader (loading the class), so the top-level `val`
         *  contracts register exactly once. We capture the snapshot in @BeforeAll - clearing then reloading
         *  would NOT re-run the already-run `<clinit>`. (The real build loads each facade in a fresh
         *  classloader, so its per-facade clear+load is sound there.) */
        private lateinit var defs: List<ContractDefinition>

        @BeforeAll
        @JvmStatic
        fun loadFacade() {
            ContractRegistry.clear()
            Class.forName("org.bmc4j.engine.ContractDslBytecodeTestFixturesKt", true,
                    ContractDslBytecodeTest::class.java.classLoader)
            defs = ContractRegistry.snapshot()
        }
    }

    @Test
    fun executes_and_registers_three_definitions_in_source_order() {
        assertEquals(3, defs.size)
        // The middle definition is the static capped contract; first and last are the instance deposit.
        assertEquals(1, defs[0].cases.size)
        assertTrue(defs[0].cases[0].hasExplicitFrame)
    }

    @Test
    fun resolves_the_mutating_instance_target_and_predicate_handles() {
        val l = lower()[0]
        assertEquals("org/bmc4j/engine/DslFixtureAccount", l.targetOwner)
        assertEquals("deposit", l.targetName)
        assertEquals("(I)V", l.targetDesc)
        assertTrue(l.isInstance)
        val case = l.cases.single()
        // pre binds (self, amount); post binds (before, after, amount, ret); frame present.
        assertEquals(2, Type.getArgumentTypes(case.pre.desc).size)
        assertEquals(4, Type.getArgumentTypes(case.posts.single().desc).size)
        assertNotNull(case.frame)
    }

    @Test
    fun resolves_the_static_target_threading_the_first_arg_as_self() {
        val l = lower()[1]
        assertEquals("org/bmc4j/engine/DslFixtureMath", l.targetOwner)
        assertEquals("capped", l.targetName)
        assertEquals("(II)I", l.targetDesc)
        assertTrue(!l.isInstance)
        assertNull(l.cases.single().frame)
    }

    @Test
    fun generates_bmcproof_enforce_methods_that_call_the_real_body() {
        val lowered = lower()
        val bytes = ContractDslBytecode.generateEnforceClass("org/bmc4j/engine/GenDslEnforce", lowered)
        val proofs = HashSet<String>()
        var callsDeposit = false
        var refutedAnnotation = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n == null || n == "<init>") return null
                proofs.add(n)
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(desc: String?, vis: Boolean):
                            org.objectweb.asm.AnnotationVisitor? {
                        if (desc == "Lorg/bmc4j/BmcProof;") {
                            return object : org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                                override fun visitEnum(name: String?, d2: String?, value: String?) {
                                    if (name == "expect" && value == "REFUTED") refutedAnnotation = true
                                }
                            }
                        }
                        return null
                    }

                    override fun visitMethodInsn(op: Int, owner: String?, name: String?, dd: String?,
                                                 itf: Boolean) {
                        if (op == Opcodes.INVOKEVIRTUAL && owner == "org/bmc4j/engine/DslFixtureAccount"
                                && name == "deposit") {
                            callsDeposit = true
                        }
                    }
                }
            }
        }, 0)
        assertTrue(proofs.any { it.startsWith("enforce__deposit") }, "an enforce for deposit")
        assertTrue(proofs.any { it.startsWith("enforce__capped") }, "an enforce for capped")
        assertTrue(callsDeposit, "the enforce proof must call the REAL deposit body")
        assertTrue(refutedAnnotation, "the deliberately-false contract pins @BmcProof(expect = REFUTED)")
    }
}
