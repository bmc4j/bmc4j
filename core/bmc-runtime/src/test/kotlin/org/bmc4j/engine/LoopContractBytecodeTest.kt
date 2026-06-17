package org.bmc4j.engine

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
 * Unit tests for [LoopContractBytecode]: the marker-sequence lowering (each loop* marker -> its
 * check/assume VC), the auto-computed local assigns set (havoc), and the LOUD refusal of an unsupported
 * heap (field/array) assigns set. The END-TO-END soundness (a correct invariant VERIFIES without
 * unrolling; a WRONG invariant REFUTES) is pinned by the `proofs.loopcontract` example proofs.
 */
internal class LoopContractBytecodeTest {

    @Test
    fun lowers_every_marker_to_its_vc() {
        val calls = methodCalls(
                LoopContractBytecode.rewriteClass(contractClass(heap = false), "C", "m"), "m")
        // No loop* marker survives.
        assertFalse(calls.any { it.contains("Bmc.loop") },
                "no loop* marker calls should remain: $calls")
        // Base + step are TWO check() asserts (loopInvariant, loopPreserve); the inductive hyp, the guard,
        // and the exit are THREE assume() calls; the step's assume(false) adds a fourth assume.
        assertEquals(2, calls.count { it == "Bmc.check(Z)V" },
                "base + step preservation should be two check() asserts: $calls")
        assertEquals(4, calls.count { it == "CProver.assume(Z)V" },
                "inductive-hyp + guard + step-cut + exit should be four assume()s: $calls")
        // The havoc set is {s, i} -> two nondetInt re-symbolizations.
        assertEquals(2, calls.count { it == "CProver.nondetInt()I" },
                "the two int locals the body writes should be havoc'd: $calls")
    }

    @Test
    fun refuses_a_heap_assigns_set_loud() {
        val ex = assertThrows(LoopContractBytecode.LoopContractError::class.java) {
            LoopContractBytecode.rewriteClass(contractClass(heap = true), "C", "m")
        }
        assertTrue(ex.message!!.contains("HEAP"), ex.message)
    }

    @Test
    fun a_class_without_markers_is_untouched() {
        val plain = plainClass()
        val out = LoopContractBytecode.rewriteClass(plain, "C", "m")
        assertFalse(LoopContractBytecode.hasLoopContract(out, "m"))
    }

    // ---- helpers ----

    /** A class `C` with a method `m()` carrying the canonical loop-contract marker sequence. The body
     *  writes two int locals (slots 1, 2); with [heap]=true it also writes a field (an unsupported frame). */
    private fun contractClass(heap: Boolean): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
        if (heap) {
            cw.visitField(Opcodes.ACC_PUBLIC, "f", "I", null, null).visitEnd()
        }
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        mv.visitCode()
        // init locals s=0 (slot 1), i=0 (slot 2). (slot 0 = this)
        mv.visitInsn(Opcodes.ICONST_0); mv.visitVarInsn(Opcodes.ISTORE, 1)
        mv.visitInsn(Opcodes.ICONST_0); mv.visitVarInsn(Opcodes.ISTORE, 2)
        // loopInvariant(true)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "loopInvariant", "(Z)V", false)
        // loopHavoc()
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "loopHavoc", "()V", false)
        // loopAssume(true)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "loopAssume", "(Z)V", false)
        // loopGuard(true)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "loopGuard", "(Z)V", false)
        // --- body: s = s + i; i = i + 1; (and optionally this.f = i) ---
        mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitVarInsn(Opcodes.ILOAD, 2)
        mv.visitInsn(Opcodes.IADD); mv.visitVarInsn(Opcodes.ISTORE, 1)
        mv.visitVarInsn(Opcodes.ILOAD, 2); mv.visitInsn(Opcodes.ICONST_1)
        mv.visitInsn(Opcodes.IADD); mv.visitVarInsn(Opcodes.ISTORE, 2)
        if (heap) {
            mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitVarInsn(Opcodes.ILOAD, 2)
            mv.visitFieldInsn(Opcodes.PUTFIELD, "C", "f", "I")
        }
        // loopPreserve(true)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "loopPreserve", "(Z)V", false)
        // loopExit(true)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "loopExit", "(Z)V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun plainClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        mv.visitCode(); mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 0); mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** Every `owner.name desc` INVOKESTATIC in [methodName], in order. */
    private fun methodCalls(bytes: ByteArray, methodName: String): List<String> {
        val out = mutableListOf<String>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != methodName) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        val short = owner?.substringAfterLast('/')
                        out.add("$short.$name$desc")
                    }
                }
            }
        }, 0)
        return out
    }
}
