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

    /**
     * The cover fold of a COMPOUND slice condition (a short-circuiting `||` whose value is built from
     * INTERNAL branches, exactly like `x in a..b || x in c..d`) must produce well-formed, JVM-verifiable
     * bytecode — the regression that produced a false cover gap. We define the rewritten class to force
     * the verifier to run over it.
     */
    @Test
    fun cover_fold_of_a_compound_short_circuit_slice_verifies() {
        val rewritten = DomainSplitBytecode.rewriteClass(
                compoundSliceClass(), "C", "m", RunPlan.Cover)
        // Defining the class runs the JVM bytecode verifier (the same well-formedness JBMC needs).
        val loader = object : ClassLoader(javaClass.classLoader) {
            fun define(b: ByteArray): Class<*> = defineClass("C", b, 0, b.size)
        }
        loader.define(rewritten) // throws VerifyError on malformed stack/locals/frames
    }

    /**
     * The cover's synthetic locals must sit CONTIGUOUSLY above the method's own `maxLocals`, not at a
     * fixed far base — a gap of TOP slots forces ASM to emit a FULL frame (declaring every slot up to
     * the synthetic base) at the branch targets inside a compound slice condition, which is what JBMC
     * mis-tracked into a phantom gap. Assert no such oversized full frame survives.
     */
    @Test
    fun cover_fold_introduces_no_oversized_full_frame() {
        val rewritten = DomainSplitBytecode.rewriteClass(
                compoundSliceClass(), "C", "m", RunPlan.Cover)
        var maxLocalsInAnyFrame = 0
        ClassReader(rewritten).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor? {
                if (n != "m") return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?,
                                            nStack: Int, stack: Array<out Any>?) {
                        if (type == Opcodes.F_NEW || type == Opcodes.F_FULL) {
                            maxLocalsInAnyFrame = maxOf(maxLocalsInAnyFrame, nLocal)
                        }
                    }
                }
            }
        }, 0)
        // The proof method has a handful of locals; the synthetic slots add 3 on top. A full frame
        // declaring ~200 slots is the bug signature.
        assertTrue(maxLocalsInAnyFrame < 32,
                "a full frame declaring $maxLocalsInAnyFrame locals signals a TOP-slot gap above maxLocals")
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

        /**
         * A class `C` with one @BmcProof method `m` whose FIRST slice condition is a short-circuiting
         * `||` of two range checks (the shape `x in a..b || x in c..d` desugars to) — its boolean value
         * is built from internal branch targets, not a single trailing compare. This is the case the
         * cover fold must materialise before ORing. Shape:
         * `int x = 0; domainSplit(x>=0 && x<=100); slice((x in 0..9) || (x in 90..100)); slice(x in 10..89); check(x==x)`.
         */
        private fun compoundSliceClass(): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
            mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
            mv.visitCode()
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitVarInsn(Opcodes.ISTORE, 1) // int x = 0 (slot 1)
            // domainSplit(x >= 0 && x <= 100) — a short-circuit conjunction.
            pushRange(mv, 0, 100)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "domainSplit", "(Z)V", false)
            // slice((x in 0..9) || (x in 90..100)) — short-circuit DISJUNCTION (internal branches).
            pushDisjunction(mv, 0, 9, 90, 100)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "slice", "(Z)V", false)
            // slice(x in 10..89) — the middle, a plain conjunction.
            pushRange(mv, 10, 89)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "slice", "(Z)V", false)
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

        /** Push a 0/1 boolean for `lo <= x && x <= hi` (a short-circuit conjunction over slot 1). */
        private fun pushRange(mv: MethodVisitor, lo: Int, hi: Int) {
            val f = org.objectweb.asm.Label()
            val t = org.objectweb.asm.Label()
            val e = org.objectweb.asm.Label()
            mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitIntInsn(Opcodes.SIPUSH, lo)
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, f) // x < lo => false
            mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitIntInsn(Opcodes.SIPUSH, hi)
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, f) // x > hi => false
            mv.visitInsn(Opcodes.ICONST_1); mv.visitJumpInsn(Opcodes.GOTO, e)
            mv.visitLabel(f); mv.visitInsn(Opcodes.ICONST_0)
            mv.visitLabel(t); mv.visitLabel(e)
        }

        /** Push a 0/1 boolean for `(x in lo1..hi1) || (x in lo2..hi2)` — short-circuit disjunction. */
        private fun pushDisjunction(mv: MethodVisitor, lo1: Int, hi1: Int, lo2: Int, hi2: Int) {
            val t = org.objectweb.asm.Label()
            val f = org.objectweb.asm.Label()
            val e = org.objectweb.asm.Label()
            // first range: if in [lo1,hi1] jump to t.
            val trysecond = org.objectweb.asm.Label()
            mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitIntInsn(Opcodes.SIPUSH, lo1)
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, trysecond)
            mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitIntInsn(Opcodes.SIPUSH, hi1)
            mv.visitJumpInsn(Opcodes.IF_ICMPLE, t)
            mv.visitLabel(trysecond)
            // second range: if in [lo2,hi2] jump to t, else false.
            mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitIntInsn(Opcodes.SIPUSH, lo2)
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, f)
            mv.visitVarInsn(Opcodes.ILOAD, 1); mv.visitIntInsn(Opcodes.SIPUSH, hi2)
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, f)
            mv.visitLabel(t); mv.visitInsn(Opcodes.ICONST_1); mv.visitJumpInsn(Opcodes.GOTO, e)
            mv.visitLabel(f); mv.visitInsn(Opcodes.ICONST_0)
            mv.visitLabel(e)
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
