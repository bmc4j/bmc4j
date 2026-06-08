package org.bmc4j.engine

import org.bmc4j.engine.DomainSplitBytecode.RunPlan
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
 * Unit tests for [DomainSplitBytecode]: the static plan analysis (one-split-per-proof + orphan-slice
 * processing errors) and the per-run marker rewrites (a slice's `assume`, the cover obligation). The
 * END-TO-END soundness (a real split that VERIFIES, a GAP that the cover REFUTES, a REFUTED slice's
 * counterexample, an UNKNOWN slice) is pinned by the `proofs.domainsplit` example proofs.
 */
internal class DomainSplitBytecodeTest {

    // ---- plan analysis ----

    @Test
    fun analyzes_a_well_formed_split() {
        val plan = DomainSplitBytecode.analyzeBytes(splitClass(slices = 3), "m")
        assertTrue(plan.isSplit, "a domainSplit + slices is a split")
        assertEquals(3, plan.sliceCount)
    }

    @Test
    fun no_markers_is_not_a_split() {
        val plan = DomainSplitBytecode.analyzeBytes(splitClass(slices = 0, withSplit = false), "m")
        assertFalse(plan.isSplit)
        assertEquals(0, plan.sliceCount)
    }

    @Test
    fun two_domain_splits_is_an_error() {
        val ex = assertThrows(DomainSplitBytecode.DomainSplitError::class.java) {
            DomainSplitBytecode.analyzeBytes(splitClass(slices = 2, splits = 2), "m")
        }
        assertTrue(ex.message!!.contains("at most ONE"), ex.message)
    }

    @Test
    fun slice_without_split_is_an_error() {
        val ex = assertThrows(DomainSplitBytecode.DomainSplitError::class.java) {
            DomainSplitBytecode.analyzeBytes(splitClass(slices = 2, withSplit = false), "m")
        }
        assertTrue(ex.message!!.contains("no enclosing domainSplit"), ex.message)
    }

    @Test
    fun split_without_slices_is_an_error() {
        val ex = assertThrows(DomainSplitBytecode.DomainSplitError::class.java) {
            DomainSplitBytecode.analyzeBytes(splitClass(slices = 0), "m")
        }
        assertTrue(ex.message!!.contains("no slice"), ex.message)
    }

    // ---- slice rewrite ----

    @Test
    fun slice_run_routes_chosen_slice_to_assume_and_pops_the_rest() {
        val calls = methodCalls(DomainSplitBytecode.rewriteClass(
                splitClass(slices = 3), "C", "m", RunPlan.Slice(1)), "m")
        // The markers must be gone; exactly one CProver.assume (for slice #1) must appear.
        assertFalse(calls.any { it.contains("Bmc.slice") || it.contains("Bmc.domainSplit") },
                "no domainSplit/slice marker calls should remain: $calls")
        assertEquals(1, calls.count { it.contains("CProver.assume(Z)V") },
                "the chosen slice should become exactly one assume: $calls")
    }

    @Test
    fun slice_run_pops_every_marker_when_none_chosen() {
        // Choosing an out-of-range slice (defensive): no assume, all markers discarded.
        val calls = methodCalls(DomainSplitBytecode.rewriteClass(
                splitClass(slices = 3), "C", "m", RunPlan.Slice(99)), "m")
        assertFalse(calls.any { it.contains("CProver.assume") },
                "no slice chosen => no assume: $calls")
        assertFalse(calls.any { it.contains("Bmc.slice") || it.contains("Bmc.domainSplit") },
                "markers must be gone: $calls")
    }

    // ---- cover rewrite ----

    @Test
    fun cover_run_emits_a_check_and_drops_the_markers() {
        val calls = methodCalls(DomainSplitBytecode.rewriteClass(
                splitClass(slices = 3), "C", "m", RunPlan.Cover), "m")
        assertTrue(calls.any { it.contains("Bmc.check(Z)V") },
                "the cover run must emit Bmc.check for the cover obligation: $calls")
        assertFalse(calls.any { it.contains("Bmc.slice") || it.contains("Bmc.domainSplit") },
                "markers must be gone in the cover run: $calls")
        assertFalse(calls.any { it.contains("CProver.assume") },
                "the cover run assumes nothing: $calls")
    }

    @Test
    fun rewrite_leaves_a_non_entry_class_untouched() {
        val bytes = splitClass(slices = 2)
        val out = DomainSplitBytecode.rewriteClass(bytes, "Other", "m", RunPlan.Cover)
        assertEquals(bytes.toList(), out.toList(), "a non-entry class must be returned verbatim")
    }

    companion object {
        private const val BMC = "org/bmc4j/Bmc"

        /**
         * A class `C` with one @BmcProof method `m` that loads an int local then calls
         * [splits] domainSplit markers and [slices] slice markers (each over `x relational 0`). The
         * shape mirrors a real proof: `int x = any; domainSplit(...); slice(...)*; check(...)`.
         */
        private fun splitClass(slices: Int, splits: Int = 1, withSplit: Boolean = true): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
            mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
            mv.visitCode()
            // int x = 0 (slot 1); the conditions are x-relational expressions.
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitVarInsn(Opcodes.ISTORE, 1)
            val effectiveSplits = if (withSplit) splits else 0
            repeat(effectiveSplits) {
                // domainSplit(x >= 0) — push (x >= 0) as a 0/1 boolean.
                pushCmp(mv, Opcodes.IFLT)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "domainSplit", "(Z)V", false)
            }
            repeat(slices) { i ->
                // slice(x op 0): use a different comparison per slice for realism.
                pushCmp(mv, if (i % 2 == 0) Opcodes.IFGE else Opcodes.IFLT)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "slice", "(Z)V", false)
            }
            // body: check(x == x) — a trivial property so the rewritten method is well-formed.
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

        /** Push a 0/1 boolean for `x <cmp> 0` where [branchIfFalse] is the IF* that jumps when false. */
        private fun pushCmp(mv: MethodVisitor, branchIfFalse: Int) {
            mv.visitVarInsn(Opcodes.ILOAD, 1)
            val t = org.objectweb.asm.Label()
            val e = org.objectweb.asm.Label()
            mv.visitJumpInsn(branchIfFalse, t)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitJumpInsn(Opcodes.GOTO, e)
            mv.visitLabel(t)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitLabel(e)
        }

        private fun methodCalls(clazz: ByteArray, method: String): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor? {
                    if (n != method) {
                        return null
                    }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            calls.add("$owner.$name$desc")
                        }
                    }
                }
            }, 0)
            return calls
        }
    }
}
