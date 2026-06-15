package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [StringLengthBytecode]: a symbolic-string introduction (`nondetWithoutNull()` +
 * `CHECKCAST String`) becomes a bounded [BmcStrings.anyCharBacked] call - the bound is the helper's own
 * `maxLength` parameter inside a recognized `Bmc` helper, else the global `maxStringLength` constant - and
 * the trailing `checkcast` is dropped. A `nondetWithoutNull()` used for a non-String type, and a class
 * that never references it, are left untouched.
 */
internal class StringLengthBytecodeTest {

    private fun trace(clazz: ByteArray): List<String> {
        val out = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                if (n != METHOD_NAME) return super.visitMethod(a, n, d, s, e)
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitLdcInsn(value: Any?) { out.add("ldc $value") }
                    override fun visitInsn(op: Int) { if (op == Opcodes.RETURN) out.add("return") }
                    override fun visitIntInsn(op: Int, operand: Int) {
                        if (op == Opcodes.BIPUSH) out.add("bipush $operand")
                    }
                    override fun visitTypeInsn(op: Int, type: String?) {
                        if (op == Opcodes.CHECKCAST) out.add("checkcast $type")
                    }
                    override fun visitVarInsn(op: Int, v: Int) {
                        if (op == Opcodes.ASTORE) out.add("astore $v")
                        if (op == Opcodes.ILOAD) out.add("iload $v")
                    }
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                                 itf: Boolean) {
                        out.add("invoke $owner.$name$desc")
                    }
                }
            }
        }, 0)
        return out
    }

    @Test
    fun bare_symbolic_string_site_is_bounded_by_global() {
        // String s = (String) nondetWithoutNull();  in a non-Bmc class -> bound = global constant.
        val bytes = methodWith("pkg/P", METHOD_NAME, "()V") { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/cprover/CProver", "nondetWithoutNull",
                    "()Ljava/lang/Object;", false)
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String")
            mv.visitVarInsn(Opcodes.ASTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertEquals(
                listOf(
                        "bipush 7",   // the global maxStringLength, pushed as the bound (ldc small int -> bipush)
                        "invoke org/bmc4j/engine/BmcStrings.anyCharBacked(I)Ljava/lang/String;",
                        // checkcast dropped (the factory already returns String)
                        "astore 0",
                        "return"),
                trace(StringLengthBytecode.rewriteClass(bytes, 7)))
    }

    @Test
    fun bmc_helper_site_is_bounded_by_its_own_maxlength_param() {
        // Inside Bmc.anyString(II) the bound is the helper's own maxLength param at slot 1, NOT the global.
        val bytes = methodWith("org/bmc4j/Bmc", "anyString", "(II)Ljava/lang/String;") { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/cprover/CProver", "nondetWithoutNull",
                    "()Ljava/lang/Object;", false)
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String")
            mv.visitVarInsn(Opcodes.ASTORE, 2)
            mv.visitInsn(Opcodes.RETURN)
        }
        // The probe method is named anyString with desc (II)..., so the slot map yields slot 1.
        assertEquals(
                listOf(
                        "iload 1",    // the per-call maxLength parameter, not the global
                        "invoke org/bmc4j/engine/BmcStrings.anyCharBacked(I)Ljava/lang/String;",
                        "astore 2",
                        "return"),
                traceNamed(StringLengthBytecode.rewriteClass(bytes, 7), "anyString"))
    }

    @Test
    fun non_string_nondet_is_left_untouched() {
        // (Foo) nondetWithoutNull() - an anyRef-style use - must NOT be rewritten.
        val bytes = methodWith("pkg/P", METHOD_NAME, "()V") { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/cprover/CProver", "nondetWithoutNull",
                    "()Ljava/lang/Object;", false)
            mv.visitTypeInsn(Opcodes.CHECKCAST, "pkg/Foo")
            mv.visitVarInsn(Opcodes.ASTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertEquals(
                listOf(
                        "invoke org/cprover/CProver.nondetWithoutNull()Ljava/lang/Object;",
                        "checkcast pkg/Foo",
                        "astore 0",
                        "return"),
                trace(StringLengthBytecode.rewriteClass(bytes, 7)))
    }

    @Test
    fun a_class_without_nondet_is_unchanged() {
        val bytes = methodWith("pkg/P", METHOD_NAME, "()V") { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
            mv.visitVarInsn(Opcodes.ISTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertArrayEquals(bytes, StringLengthBytecode.rewriteClass(bytes, 7),
                "a class that never references nondetWithoutNull comes back byte-for-byte unchanged")
    }

    @Test
    fun constant_string_literal_is_routed_through_ofChars() {
        // A `ldc "ab"` String constant is rewritten to a fixed-length char-backed construction
        // (new char[]{'a','b'} -> BmcStrings.ofChars), so the literal's KNOWN length is concrete and
        // downstream length-bounded ops do not loop on the symbolic char-array backing JBMC would
        // otherwise give an ldc constant under CHAR_ARRAY_MODEL.
        val bytes = methodWith("pkg/P", METHOD_NAME, "()V") { mv ->
            mv.visitLdcInsn("ab")
            mv.visitVarInsn(Opcodes.ASTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        val t = trace(StringLengthBytecode.rewriteClass(bytes, 7))
        assertTrue(t.none { it.startsWith("ldc ") }, "the String literal should be rewritten away: $t")
        assertTrue(t.any { it.contains("BmcStrings.ofChars") }, "should route through ofChars: $t")
    }

    /** Like [trace] but for a method named [name] (the helper-slot test uses a non-default name). */
    private fun traceNamed(clazz: ByteArray, name: String): List<String> {
        val out = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                if (n != name) return super.visitMethod(a, n, d, s, e)
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInsn(op: Int) { if (op == Opcodes.RETURN) out.add("return") }
                    override fun visitVarInsn(op: Int, v: Int) {
                        if (op == Opcodes.ASTORE) out.add("astore $v")
                        if (op == Opcodes.ILOAD) out.add("iload $v")
                    }
                    override fun visitTypeInsn(op: Int, type: String?) {
                        if (op == Opcodes.CHECKCAST) out.add("checkcast $type")
                    }
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                                 itf: Boolean) {
                        out.add("invoke $owner.$name$desc")
                    }
                }
            }
        }, 0)
        return out
    }

    private companion object {
        const val METHOD_NAME = "m"
    }

    private fun methodWith(owner: String, name: String, desc: String,
                           body: (MethodVisitor) -> Unit): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, desc, null, null)
        mv.visitCode()
        body(mv)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
