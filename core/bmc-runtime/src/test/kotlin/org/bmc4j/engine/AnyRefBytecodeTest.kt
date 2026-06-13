package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [AnyRefBytecode]: every `Bmc.anyRef(Foo.class)` call site must become
 * `POP; CProver.nondetWithoutNull()` (dropping the Class token, calling the JBMC-intrinsic havoc) with
 * the surrounding `LDC Foo.class` and trailing `checkcast Foo` left intact, so the erasure cast holds.
 * A class that never references `anyRef` comes back byte-for-byte unchanged.
 */
internal class AnyRefBytecodeTest {

    /** The instruction trace of [clazz]'s single method `m`, each as a short token. */
    private fun trace(clazz: ByteArray): List<String> {
        val out = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                if (n != "m") return super.visitMethod(a, n, d, s, e)
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitLdcInsn(value: Any?) { out.add("ldc $value") }
                    override fun visitInsn(op: Int) {
                        if (op == Opcodes.POP) out.add("pop") else if (op == Opcodes.RETURN) out.add("return")
                    }
                    override fun visitTypeInsn(op: Int, type: String?) {
                        if (op == Opcodes.CHECKCAST) out.add("checkcast $type")
                    }
                    override fun visitVarInsn(op: Int, v: Int) {
                        if (op == Opcodes.ASTORE) out.add("astore $v")
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
    fun anyref_call_site_is_intrinsified_keeping_ldc_and_checkcast() {
        // Foo repo = Bmc.anyRef(Foo.class); -> ldc Foo.class; invokestatic anyRef; checkcast Foo; astore.
        val bytes = methodWith { mv ->
            mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType("pkg/Foo"))
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyRef",
                    "(Ljava/lang/Class;)Ljava/lang/Object;", false)
            mv.visitTypeInsn(Opcodes.CHECKCAST, "pkg/Foo")
            mv.visitVarInsn(Opcodes.ASTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertEquals(
                listOf(
                        "ldc Lpkg/Foo;",                       // the Class token, untouched
                        "pop",                                 // drop it
                        "invoke org/cprover/CProver.nondetWithoutNull()Ljava/lang/Object;",
                        "checkcast pkg/Foo",                   // erasure cast, untouched - now holds
                        "astore 0",
                        "return"),
                trace(AnyRefBytecode.rewriteClass(bytes)))
    }

    @Test
    fun a_class_without_anyref_is_unchanged() {
        val bytes = methodWith { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
            mv.visitVarInsn(Opcodes.ISTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertArrayEquals(bytes, AnyRefBytecode.rewriteClass(bytes),
                "a class that never references anyRef comes back byte-for-byte unchanged")
    }

    private fun methodWith(body: (MethodVisitor) -> Unit): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/P", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "m", "()V", null, null)
        mv.visitCode()
        body(mv)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
