package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [LoopInvariantBytecode]: recovering a structured counted loop from its `.N` engine id and
 * lowering it to the SAME base/step/summary VCs the marker form ([LoopContractBytecode]) produces, binding a
 * predicate's parameter names to the loop's locals, and the LOUD refusal of an unsupported heap assigns set.
 * The end-to-end soundness (correct invariant VERIFIES summarized; WRONG invariant REFUTES) is pinned by the
 * `proofs.loopcontract.LoopInvariantAnnotationProof` example proofs.
 */
internal class LoopInvariantBytecodeTest {

    @Test
    fun recovers_and_lowers_a_counted_loop_to_its_vcs() {
        val calls = methodCalls(
                LoopInvariantBytecode.rewriteClass(loopClass(heap = false), "C", "m"), "m")
        // No guard if_icmp remains as the loop's own control -- the loop is summarized, not iterated.
        // Base + step are TWO check() asserts; the inductive hyp, the guard, the step-cut, and the exit are
        // FOUR assume()s (assume(I), assume(g), assume(false), assume(!g)); the predicate is called for base,
        // hyp and step => THREE pred() calls.
        assertEquals(2, calls.count { it == "Bmc.check(Z)V" },
                "base + step preservation should be two check() asserts: $calls")
        assertEquals(4, calls.count { it == "CProver.assume(Z)V" },
                "hyp + guard + step-cut + exit should be four assume()s: $calls")
        assertEquals(3, calls.count { it == "C.inv(III)Z" },
                "the predicate should be invoked for base, hyp and step: $calls")
        // The havoc set is {s, i} -> two nondetInt re-symbolizations.
        assertEquals(2, calls.count { it == "CProver.nondetInt()I" },
                "the two int locals the body writes should be havoc'd: $calls")
    }

    @Test
    fun refuses_a_heap_assigns_set_loud() {
        val ex = assertThrows(LoopInvariantBytecode.LoopInvariantError::class.java) {
            LoopInvariantBytecode.rewriteClass(loopClass(heap = true), "C", "m")
        }
        assertTrue(ex.message!!.contains("HEAP"), ex.message)
    }

    @Test
    fun a_method_without_the_annotation_is_untouched() {
        val plain = loopClass(heap = false, annotated = false)
        assertTrue(LoopInvariantBytecode.specsOf(plain, "m").isEmpty())
        val out = LoopInvariantBytecode.rewriteClass(plain, "C", "m")
        assertEquals(plain.size, out.size)
    }

    @Test
    fun a_wrong_loop_id_method_is_refused_loud() {
        // The annotation names a method that is not the contract's method.
        val ex = assertThrows(LoopInvariantBytecode.LoopInvariantError::class.java) {
            LoopInvariantBytecode.rewriteClass(loopClass(heap = false, loopId = "java::C.other:()V.0"), "C", "m")
        }
        assertTrue(ex.message!!.contains("names method"), ex.message)
    }

    // ---- helpers ----

    /**
     * A class `C` with `m()` containing the canonical counted loop `for (i=0;i<n;i++) s+=i;` over locals
     * i (slot 3), s (slot 2), n (slot 1), and a `@LoopInvariant(loop=..., predicate="inv")`. `inv(int i,
     * int s, int n)` is a static boolean stub. With [heap]=true the body also writes a field (an unsupported
     * frame). The locals carry an LVT (names i/s/n) so the predicate param binding resolves.
     */
    private fun loopClass(
            heap: Boolean,
            annotated: Boolean = true,
            loopId: String = "java::C.m:()V.0"): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
        if (heap) {
            cw.visitField(Opcodes.ACC_PUBLIC, "f", "I", null, null).visitEnd()
        }
        // static boolean inv(int i, int s, int n) { return true; }  -- with parameter LVT names.
        val iv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "inv", "(III)Z", null, null)
        iv.visitCode()
        val ivs = Label(); val ive = Label()
        iv.visitLabel(ivs)
        iv.visitInsn(Opcodes.ICONST_1); iv.visitInsn(Opcodes.IRETURN)
        iv.visitLabel(ive)
        iv.visitLocalVariable("i", "I", null, ivs, ive, 0)
        iv.visitLocalVariable("s", "I", null, ivs, ive, 1)
        iv.visitLocalVariable("n", "I", null, ivs, ive, 2)
        iv.visitMaxs(0, 0); iv.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        if (annotated) {
            val av = mv.visitAnnotation("Lorg/bmc4j/LoopInvariant;", true)
            av.visit("loop", loopId)
            av.visit("predicate", "inv")
            av.visitEnd()
        }
        mv.visitCode()
        val mStart = Label(); val mEnd = Label()
        val header = Label(); val exit = Label()
        mv.visitLabel(mStart)
        // n = 5 (slot 1)
        mv.visitIntInsn(Opcodes.BIPUSH, 5); mv.visitVarInsn(Opcodes.ISTORE, 1)
        // s = 0 (slot 2)
        mv.visitInsn(Opcodes.ICONST_0); mv.visitVarInsn(Opcodes.ISTORE, 2)
        // i = 0 (slot 3)
        mv.visitInsn(Opcodes.ICONST_0); mv.visitVarInsn(Opcodes.ISTORE, 3)
        // header: if (i >= n) goto exit
        mv.visitLabel(header)
        mv.visitVarInsn(Opcodes.ILOAD, 3); mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, exit)
        // body: s = s + i; (+ optional this.f = i;)  i = i + 1
        mv.visitVarInsn(Opcodes.ILOAD, 2); mv.visitVarInsn(Opcodes.ILOAD, 3)
        mv.visitInsn(Opcodes.IADD); mv.visitVarInsn(Opcodes.ISTORE, 2)
        if (heap) {
            mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitVarInsn(Opcodes.ILOAD, 3)
            mv.visitFieldInsn(Opcodes.PUTFIELD, "C", "f", "I")
        }
        mv.visitIincInsn(3, 1)
        // back-edge
        mv.visitJumpInsn(Opcodes.GOTO, header)
        mv.visitLabel(exit)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitLabel(mEnd)
        mv.visitLocalVariable("this", "LC;", null, mStart, mEnd, 0)
        mv.visitLocalVariable("n", "I", null, mStart, mEnd, 1)
        mv.visitLocalVariable("s", "I", null, mStart, mEnd, 2)
        mv.visitLocalVariable("i", "I", null, header, exit, 3)
        mv.visitMaxs(0, 0); mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** Every `owner.name desc` INVOKESTATIC/SPECIAL/VIRTUAL in [methodName], in order. */
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

    @Suppress("UNUSED_PARAMETER")
    private fun unused() {
        assertFalse(false)
    }
}
