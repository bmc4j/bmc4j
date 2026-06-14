package org.bmc4j.engine

import org.bmc4j.engine.BranchDecomposeBytecode.RunPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [BranchDecomposeBytecode]: the static plan analysis (one-branch-per-proof processing
 * error) and the per-run marker rewrites (the leaf's `assume(cond)`, the parent's `assume(!cond)`). The
 * END-TO-END soundness (a real decomposition that VERIFIES; a leaf-side and a parent-side counterexample
 * that REFUTE) is pinned by the `proofs.branchdecompose` example proofs.
 */
internal class BranchDecomposeBytecodeTest {

    // ---- plan analysis ----

    @Test
    fun analyzes_a_well_formed_decomposition() {
        val plan = BranchDecomposeBytecode.analyzeBytes(branchClass(markers = 1), "m")
        assertTrue(plan.isDecomposed, "a coldBranch marker is a decomposition")
        assertEquals(1, plan.branchCount)
    }

    @Test
    fun no_marker_is_not_a_decomposition() {
        val plan = BranchDecomposeBytecode.analyzeBytes(branchClass(markers = 0), "m")
        assertFalse(plan.isDecomposed)
        assertEquals(0, plan.branchCount)
    }

    @Test
    fun two_cold_branches_is_an_error() {
        val ex = assertThrows(BranchDecomposeBytecode.BranchDecomposeError::class.java) {
            BranchDecomposeBytecode.analyzeBytes(branchClass(markers = 2), "m")
        }
        assertTrue(ex.message!!.contains("at most ONE"), ex.message)
    }

    // ---- leaf rewrite ----

    @Test
    fun leaf_run_routes_the_marker_to_assume_without_negation() {
        val calls = methodCalls(
                BranchDecomposeBytecode.rewriteClass(branchClass(markers = 1), "C", "m", RunPlan.Leaf), "m")
        assertFalse(calls.any { it.contains("Bmc.coldBranch") },
                "the coldBranch marker must be gone: $calls")
        assertEquals(1, calls.count { it.contains("CProver.assume(Z)V") },
                "the leaf turns the marker into exactly one assume: $calls")
    }

    @Test
    fun leaf_run_inserts_no_xor() {
        // The leaf assumes the condition AS-IS (no negation), so no IXOR is injected.
        val ops = methodInsns(
                BranchDecomposeBytecode.rewriteClass(branchClass(markers = 1), "C", "m", RunPlan.Leaf), "m")
        assertFalse(ops.contains(Opcodes.IXOR), "the leaf must not negate the condition (no IXOR)")
    }

    // ---- parent rewrite ----

    @Test
    fun parent_run_routes_the_marker_to_assume_of_the_negation() {
        val rewritten = BranchDecomposeBytecode.rewriteClass(branchClass(markers = 1), "C", "m", RunPlan.Parent)
        val calls = methodCalls(rewritten, "m")
        assertFalse(calls.any { it.contains("Bmc.coldBranch") },
                "the coldBranch marker must be gone: $calls")
        assertEquals(1, calls.count { it.contains("CProver.assume(Z)V") },
                "the parent turns the marker into exactly one assume: $calls")
        // The parent negates the condition with ICONST_1 ; IXOR before the assume.
        assertTrue(methodInsns(rewritten, "m").contains(Opcodes.IXOR),
                "the parent must negate the condition (ICONST_1 ; IXOR)")
    }

    @Test
    fun rewrite_leaves_a_non_entry_class_untouched() {
        val bytes = branchClass(markers = 1)
        val out = BranchDecomposeBytecode.rewriteClass(bytes, "Other", "m", RunPlan.Parent)
        assertEquals(bytes.toList(), out.toList(), "a non-entry class must be returned verbatim")
    }

    @Test
    fun both_runs_produce_jvm_verifiable_bytecode() {
        // Defining the rewritten class runs the JVM bytecode verifier (the same well-formedness JBMC
        // needs) - guards against a stack/locals mismatch from the marker swap or the XOR injection.
        for (run in listOf(RunPlan.Leaf, RunPlan.Parent)) {
            val rewritten = BranchDecomposeBytecode.rewriteClass(branchClass(markers = 1), "C", "m", run)
            val loader = object : ClassLoader(javaClass.classLoader) {
                fun define(b: ByteArray): Class<*> = defineClass("C", b, 0, b.size)
            }
            loader.define(rewritten) // throws VerifyError on malformed stack/locals/frames
        }
    }

    companion object {
        private const val BMC = "org/bmc4j/Bmc"

        /**
         * A class `C` with one @BmcProof method `m` that loads an int local then calls [markers]
         * coldBranch markers (each over `x == 0`). The shape mirrors a real proof:
         * `int x = any; coldBranch(x == 0); check(x == x)`.
         */
        private fun branchClass(markers: Int): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
            mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
            mv.visitAnnotation("Lorg/bmc4j/BmcBranchDecompose;", true).visitEnd()
            mv.visitCode()
            // int x = 0 (slot 1); each marker condition is (x == 0).
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitVarInsn(Opcodes.ISTORE, 1)
            repeat(markers) {
                // coldBranch(x == 0): push (x == 0) as a 0/1 boolean.
                mv.visitVarInsn(Opcodes.ILOAD, 1)
                val ne = org.objectweb.asm.Label()
                val end = org.objectweb.asm.Label()
                mv.visitJumpInsn(Opcodes.IFNE, ne)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitJumpInsn(Opcodes.GOTO, end)
                mv.visitLabel(ne)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitLabel(end)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "coldBranch", "(Z)V", false)
            }
            // body: check(x == x).
            mv.visitVarInsn(Opcodes.ILOAD, 1)
            mv.visitVarInsn(Opcodes.ILOAD, 1)
            val eq = org.objectweb.asm.Label()
            val end = org.objectweb.asm.Label()
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, eq)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitJumpInsn(Opcodes.GOTO, end)
            mv.visitLabel(eq)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitLabel(end)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", "(Z)V", false)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** The `owner.name(desc)` of every INVOKE in method [methodName] of [bytes]. */
        private fun methodCalls(bytes: ByteArray, methodName: String): List<String> {
            val out = mutableListOf<String>()
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor? {
                    if (n != methodName) return null
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            out.add("$owner.$name$desc")
                        }
                    }
                }
            }, 0)
            return out
        }

        /** Every opcode visited in method [methodName] of [bytes] (for asserting IXOR presence/absence). */
        private fun methodInsns(bytes: ByteArray, methodName: String): List<Int> {
            val out = mutableListOf<Int>()
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor? {
                    if (n != methodName) return null
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitInsn(opcode: Int) { out.add(opcode) }
                    }
                }
            }, 0)
            return out
        }
    }
}
