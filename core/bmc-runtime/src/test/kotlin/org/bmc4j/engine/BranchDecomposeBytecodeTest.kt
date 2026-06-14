package org.bmc4j.engine

import org.bmc4j.engine.BranchDecomposeBytecode.RunPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Unit tests for [BranchDecomposeBytecode]: that the CFG discovery finds a top-level value branch,
 * that the per-run rewrites produce WELL-FORMED bytecode (verified with ASM's [CheckClassAdapter]),
 * and that the synthetic methods (extracted branch, relation predicate, summarize stub, leaf enforce
 * proof) are emitted. End-to-end SOUNDNESS (a real decomposition that VERIFIES; a leaf-side and a
 * parent-side counterexample that REFUTE) is pinned by the `proofs.branchdecompose` example proofs.
 */
internal class BranchDecomposeBytecodeTest {

    // A class with `int clamp(): val r = if (x < -10) -10 else if (x > 10) 10 else x; check(r); return`
    // built the way kotlinc/javac emit it: guard operand pushes, conditional jumps, arm values, a join
    // store, then a use of the stored value.
    private fun clampClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
        // default ctor
        run {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        mv.visitCode()
        // int x = anyInt();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
        mv.visitVarInsn(Opcodes.ISTORE, 1)
        // int r = (x < -10) ? -10 : (x > 10) ? 10 : x;
        val elseIf = Label()
        val elseArm = Label()
        val join = Label()
        val join2 = Label()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitIntInsn(Opcodes.BIPUSH, -10)
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, elseIf)
        mv.visitIntInsn(Opcodes.BIPUSH, -10)
        mv.visitJumpInsn(Opcodes.GOTO, join)
        mv.visitLabel(elseIf)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitIntInsn(Opcodes.BIPUSH, 10)
        mv.visitJumpInsn(Opcodes.IF_ICMPLE, elseArm)
        mv.visitIntInsn(Opcodes.BIPUSH, 10)
        mv.visitJumpInsn(Opcodes.GOTO, join2)
        mv.visitLabel(elseArm)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitLabel(join2)
        mv.visitLabel(join)
        mv.visitVarInsn(Opcodes.ISTORE, 2)
        // Bmc.check(r >= -10 && r <= 10) -- simplified to check(true) shape: load r, compare, call check.
        mv.visitVarInsn(Opcodes.ILOAD, 2)
        mv.visitIntInsn(Opcodes.BIPUSH, 10)
        val ok = Label()
        val end = Label()
        mv.visitJumpInsn(Opcodes.IF_ICMPLE, ok)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitJumpInsn(Opcodes.GOTO, end)
        mv.visitLabel(ok)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitLabel(end)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "check", "(Z)V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    // A class whose `m` has TWO sibling value branches, each `val = if (x cmp k) a else b` joining to
    // its own store, then a check. Exercises the multi-branch fan-out.
    private fun twoBranchClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
        defaultCtor(cw)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        mv.visitCode()
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
        mv.visitVarInsn(Opcodes.ISTORE, 1)
        // int a = (x < 0) ? -1 : 1;
        valueBranch(mv, 1, Opcodes.IF_ICMPGE, -1, 1, 2)
        // int b = (x > 10) ? 10 : 0;
        valueBranch(mv, 1, Opcodes.IF_ICMPLE, 10, 0, 3)
        mv.visitVarInsn(Opcodes.ILOAD, 2)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "check", "(Z)V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    // `int v = (x cmp 0-ish) ? thenVal : elseVal; ISTORE store`. The guard compares local [xSlot] to a
    // BIPUSH constant via [cmpOp] (the JUMP-to-else opcode); the THEN arm pushes [thenVal], the ELSE
    // [elseVal]. Kept simple (BIPUSH operands) so it matches the recognized value-branch shape.
    private fun valueBranch(mv: MethodVisitor, xSlot: Int, cmpOp: Int, thenVal: Int, elseVal: Int,
                            store: Int) {
        val elseArm = Label()
        val join = Label()
        mv.visitVarInsn(Opcodes.ILOAD, xSlot)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitJumpInsn(cmpOp, elseArm)
        mv.visitIntInsn(Opcodes.BIPUSH, thenVal)
        mv.visitJumpInsn(Opcodes.GOTO, join)
        mv.visitLabel(elseArm)
        mv.visitIntInsn(Opcodes.BIPUSH, elseVal)
        mv.visitLabel(join)
        mv.visitVarInsn(Opcodes.ISTORE, store)
    }

    // A class whose `m` has a LEADING early-return guard before the value branch:
    //   if (x < 0) return;  int r = (x < 10) ? x : 10;  check(r >= 0)
    // The value branch is reachable only when x >= 0, so it carries a path condition the leaf assumes.
    private fun guardedBranchClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
        defaultCtor(cw)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        mv.visitCode()
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
        mv.visitVarInsn(Opcodes.ISTORE, 1)
        // if (x < 0) return;
        val afterGuard = Label()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitJumpInsn(Opcodes.IFGE, afterGuard)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitLabel(afterGuard)
        // int r = (x < 10) ? x : 10;
        val elseArm = Label()
        val join = Label()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitIntInsn(Opcodes.BIPUSH, 10)
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, elseArm)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitJumpInsn(Opcodes.GOTO, join)
        mv.visitLabel(elseArm)
        mv.visitIntInsn(Opcodes.BIPUSH, 10)
        mv.visitLabel(join)
        mv.visitVarInsn(Opcodes.ISTORE, 2)
        mv.visitVarInsn(Opcodes.ILOAD, 2)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "check", "(Z)V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun defaultCtor(cw: ClassWriter) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }

    @Test
    fun discovers_a_top_level_value_branch() {
        val plan = BranchDecomposeBytecode.analyzeBytes(clampClass(), "m")
        assertTrue(plan.isDecomposed, "the if/else-if/else value branch is decomposable")
        assertEquals(1, plan.branchCount)
    }

    @Test
    fun discovers_multiple_value_branches_in_one_method() {
        val plan = BranchDecomposeBytecode.analyzeBytes(twoBranchClass(), "m")
        assertEquals(2, plan.branchCount, "both sibling value branches are discovered")
    }

    @Test
    fun no_branch_is_not_decomposed() {
        // A straight-line method (no conditional value branch joining to a store).
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "D", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null)
        mv.visitCode()
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
        mv.visitVarInsn(Opcodes.ISTORE, 1)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        assertFalse(BranchDecomposeBytecode.analyzeBytes(cw.toByteArray(), "m").isDecomposed)
    }

    @Test
    fun parent_rewrite_is_well_formed_and_emits_synthetic_methods() {
        val parent = BranchDecomposeBytecode.rewriteClassForTest(
                clampClass(), "C", "m", RunPlan.Parent)
        assertVerifies(parent)
        val names = methodNames(parent)
        assertTrue("branch\$0" in names, "extracted branch method present: $names")
        assertTrue("branch\$0\$post" in names, "relation predicate present: $names")
        assertTrue("branch\$0\$stub" in names, "summarize stub present: $names")
        // The parent's proof body now calls the stub instead of inlining the arms.
        assertTrue(methodCalls(parent, "m").any { it.contains("branch\$0\$stub") },
                "parent body calls the summarize stub: ${methodCalls(parent, "m")}")
    }

    @Test
    fun leaf_rewrite_is_well_formed_and_emits_the_enforce_proof() {
        val leaf = BranchDecomposeBytecode.rewriteClassForTest(
                clampClass(), "C", "m", RunPlan.Leaf(0))
        assertVerifies(leaf)
        val names = methodNames(leaf)
        assertTrue("branch\$0\$enforce" in names, "leaf enforce proof present: $names")
        // The enforce proof calls the extracted branch and checks the relation predicate.
        val calls = methodCalls(leaf, "branch\$0\$enforce")
        assertTrue(calls.any { it.contains("branch\$0(") || it.contains(".branch\$0") },
                "enforce calls the extracted branch: $calls")
        assertTrue(calls.any { it.contains("Bmc.check") }, "enforce checks the relation: $calls")
    }

    @Test
    fun parent_rewrite_of_two_branches_emits_both_and_calls_both_stubs() {
        val parent = BranchDecomposeBytecode.rewriteClassForTest(
                twoBranchClass(), "C", "m", RunPlan.Parent)
        assertVerifies(parent)
        val names = methodNames(parent)
        assertTrue("branch\$0\$stub" in names && "branch\$1\$stub" in names,
                "both summarize stubs present: $names")
        val calls = methodCalls(parent, "m")
        assertTrue(calls.any { it.contains("branch\$0\$stub") }
                && calls.any { it.contains("branch\$1\$stub") },
                "parent body calls both stubs: $calls")
    }

    @Test
    fun leaf_with_a_path_condition_assumes_the_pre_relation() {
        // A method with a leading early-return guard before the value branch: the leaf must assume the
        // path-condition pre relation so it proves the summary only where the branch is reached.
        val leaf = BranchDecomposeBytecode.rewriteClassForTest(
                guardedBranchClass(), "C", "m", RunPlan.Leaf(0))
        assertVerifies(leaf)
        val names = methodNames(leaf)
        assertTrue("branch\$0\$pre" in names, "path-condition predicate present: $names")
        val calls = methodCalls(leaf, "branch\$0\$enforce")
        assertTrue(calls.any { it.contains("branch\$0\$pre") }, "enforce assumes the pre relation: $calls")
        assertTrue(calls.any { it.contains("CProver.assume") }, "enforce calls assume: $calls")
    }

    // ---- helpers ----

    private fun assertVerifies(bytes: ByteArray) {
        val sw = StringWriter()
        CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(sw))
        val report = sw.toString()
        assertTrue(report.isEmpty(), "bytecode failed ASM verification:\n$report")
    }

    private fun methodNames(bytes: ByteArray): Set<String> {
        val out = HashSet<String>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != null) out.add(n)
                return null
            }
        }, 0)
        return out
    }

    private fun methodCalls(bytes: ByteArray, method: String): List<String> {
        val out = ArrayList<String>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != method) return null
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
}
