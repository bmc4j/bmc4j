package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [AssumeContractBytecode]: STATIC decoding of the `assumeEvery`/`assumeStable` markers
 * from their `LambdaMetafactory` bootstrap handles (no invokedynamic execution), and the shape of the
 * generated constrained-nondet stub class. END-TO-END soundness (a load-bearing assumption that
 * VERIFIES, dropping it => REFUTED/UNKNOWN, the stable env case, over-tight => VACUOUS) is pinned by the
 * `assumecontracts` example proofs.
 */
internal class AssumeContractBytecodeTest {

    private fun fixtureBytes(): ByteArray {
        val resource = "org/bmc4j/engine/AssumeContractBytecodeTestFixtures.class"
        return javaClass.classLoader.getResourceAsStream(resource)!!.use { it.readAllBytes() }
    }

    private fun decode(method: String): List<AssumeContractBytecode.Decoded> =
            AssumeContractBytecode.decodeBytes(fixtureBytes(), method)

    // ---- static decoding ----

    @Test
    fun decodes_an_output_only_bound_reference() {
        val d = decode("outputOnly")
        assertEquals(1, d.size)
        val c = d[0]
        assertEquals("org/bmc4j/engine/AssumeContractBytecodeTestFixtures\$Repo", c.targetOwner)
        assertEquals("findById", c.targetName)
        assertEquals("(I)Lorg/bmc4j/engine/AssumeContractBytecodeTestFixtures\$User;", c.targetDesc)
        assertFalse(c.targetIsStatic, "a bound instance reference is not static")
        assertFalse(c.stable, "assumeEvery is fresh-per-call, not stable")
        // The predicate is the proof class's own synthetic lambda body, taking the result only.
        assertEquals(1, org.objectweb.asm.Type.getArgumentTypes(c.predDesc).size)
    }

    @Test
    fun decodes_an_args_aware_predicate() {
        val c = decode("argsAware").single()
        assertEquals("findById", c.targetName)
        // Args-aware predicate: (result, id) — two parameters.
        assertEquals(2, org.objectweb.asm.Type.getArgumentTypes(c.predDesc).size)
    }

    @Test
    fun decodes_a_stable_zero_arg_reference() {
        val c = decode("stable").single()
        assertEquals("availableProcessors", c.targetName)
        assertEquals("()I", c.targetDesc)
        assertTrue(c.stable, "assumeStable is memoized")
    }

    @Test
    fun decodes_two_contracts_in_one_proof() {
        val d = decode("two")
        assertEquals(2, d.size)
        assertEquals("findById", d[0].targetName)
        assertFalse(d[0].stable)
        assertEquals("availableProcessors", d[1].targetName)
        assertTrue(d[1].stable)
    }

    @Test
    fun decodes_a_two_argument_args_aware_reference() {
        val c = decode("twoArg").single()
        assertEquals("find", c.targetName)
        assertEquals(2, org.objectweb.asm.Type.getArgumentTypes(c.targetDesc).size)
        // Args-aware over two call args: (result, tenant, id) — three parameters.
        assertEquals(3, org.objectweb.asm.Type.getArgumentTypes(c.predDesc).size)
    }

    @Test
    fun a_proof_without_markers_decodes_to_nothing() {
        // <init> has no markers.
        assertTrue(decode("<init>").isEmpty())
    }

    // ---- stub generation ----

    @Test
    fun generates_a_loadable_stub_class_with_one_method_per_contract() {
        val decoded = decode("two")
        val bytes = AssumeContractBytecode.generateStubClass(decoded)
        // The class verifies / loads.
        val loaded = StubClassLoader().define(bytes)
        assertEquals("bmc4jgen.AssumeContractStubs", loaded.name)
        // One stub method per contract, each static.
        val stubNames = decoded.map { it.stubName }.toSet()
        val found = loaded.declaredMethods.filter { it.name in stubNames }
        assertEquals(2, found.size)
        found.forEach { assertTrue(java.lang.reflect.Modifier.isStatic(it.modifiers)) }
    }

    @Test
    fun the_fresh_stub_calls_nondet_then_assume_then_returns() {
        val c = decode("outputOnly").single()
        val calls = stubCalls(AssumeContractBytecode.generateStubClass(listOf(c)), c.stubName)
        // A fresh stub: havoc the result, assume the predicate, return — no static memo field reads.
        assertTrue(calls.any { it.contains("CProver.nondet") }, "havocs a result: $calls")
        assertTrue(calls.any { it == "org/cprover/CProver.assume" }, "assumes the predicate: $calls")
        assertTrue(calls.any { it.endsWith(c.predName) }, "calls the user predicate: $calls")
    }

    @Test
    fun the_stable_stub_reads_a_static_memo_field() {
        val c = decode("stable").single()
        val bytes = AssumeContractBytecode.generateStubClass(listOf(c))
        // A stable stub memoizes into static fields (value + init guard).
        var fields = 0
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(a: Int, n: String?, d: String?, s: String?, v: Any?):
                    org.objectweb.asm.FieldVisitor? {
                if ((a and Opcodes.ACC_STATIC) != 0) {
                    fields++
                }
                return null
            }
        }, ClassReader.SKIP_CODE)
        assertEquals(2, fields, "stable stub has a value field and an init-once guard")
    }

    // ---- redirect wiring ----

    @Test
    fun the_redirect_targets_the_real_method_and_routes_to_the_stub() {
        val c = decode("outputOnly").single()
        val r = c.redirect()
        assertTrue(r.matchesInsn(Opcodes.INVOKEINTERFACE, c.targetOwner, c.targetName, c.targetDesc),
                "the instance target's interface call site is matched")
        // The stub descriptor prepends the receiver type.
        assertTrue(c.stubDesc.startsWith("(L${c.targetOwner};"),
                "instance stub prepends the receiver: ${c.stubDesc}")
    }

    // ---- helpers ----

    /** The `owner.name` call sites inside [stubName] of the generated [bytes], in order. */
    private fun stubCalls(bytes: ByteArray, stubName: String): List<String> {
        val out = ArrayList<String>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != stubName) {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        out.add("$owner.$name")
                    }
                }
            }
        }, 0)
        return out
    }

    private class StubClassLoader : ClassLoader(StubClassLoader::class.java.classLoader) {
        fun define(bytes: ByteArray): Class<*> =
                defineClass("bmc4jgen.AssumeContractStubs", bytes, 0, bytes.size)
    }
}
