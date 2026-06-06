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
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tests for [ReachabilityBytecode]: every `@BmcProof` method's `return` is replaced
 * by the reachability marker (`throw new AssertionError`) on the sentinel line, while non-proof
 * methods are left untouched. The marker is what makes a vacuous proof visible.
 */
internal class ReachabilityBytecodeTest {

    @Test
    fun proof_method_return_is_replaced_by_a_throwing_marker() {
        val input = sampleClass("Reach\$Proof", true)
        val out = ReachabilityBytecode.rewriteClass(input)
        val c = define("Reach\$Proof", out)
        val f = c.getMethod("f")
        f.isAccessible = true
        val ex = assertThrows(InvocationTargetException::class.java) { f.invoke(null) }
        assertTrue(ex.cause is AssertionError, "proof return should become a marker throw")
        assertTrue(hasSentinelLine(out), "marker must be stamped on the sentinel source line")
    }

    @Test
    fun non_proof_method_is_left_untouched() {
        val input = sampleClass("Reach\$Plain", false)
        val out = ReachabilityBytecode.rewriteClass(input)
        val c = define("Reach\$Plain", out)
        val f = c.getMethod("f")
        f.isAccessible = true
        assertEquals(null, f.invoke(null)) // returns normally; no marker injected
        assertFalse(hasSentinelLine(out), "non-proof methods must not get a marker")
    }

    companion object {
        /** A class with one `static void f() { return; }`, optionally `@BmcProof`-annotated. */
        private fun sampleClass(name: String, proof: Boolean): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "()V", null, null)
            if (proof) {
                mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
            }
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun hasSentinelLine(bytes: ByteArray): Boolean {
            val found = AtomicBoolean(false)
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitLineNumber(line: Int, start: Label?) {
                            if (line == BmcReachability.SENTINEL_LINE) {
                                found.set(true)
                            }
                        }
                    }
                }
            }, 0)
            return found.get()
        }

        private fun define(internalName: String, bytes: ByteArray): Class<*> {
            val binary = internalName.replace('/', '.')
            return object : ClassLoader(ReachabilityBytecodeTest::class.java.classLoader) {
                fun go(): Class<*> = defineClass(binary, bytes, 0, bytes.size)
            }.go()
        }
    }
}
