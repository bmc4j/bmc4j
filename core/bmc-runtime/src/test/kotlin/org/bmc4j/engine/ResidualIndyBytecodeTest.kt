package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Unit tests for [ResidualIndyBytecode]: any `invokedynamic` still standing after the
 * desugar passes must become an `invokestatic` to the deliberately-bodiless marker class —
 * same descriptor (stack-compatible drop-in), method name carrying the indy name + bootstrap owner
 * — so the engine's normal opaque-symbol reporting (and with it the whole stub policy) sees the
 * site instead of JBMC silently linking it to an unconstrained result. Synthesized with ASM like
 * [SwitchBytecodeTest], so the test stays at the module's Java 17 target.
 */
internal class ResidualIndyBytecodeTest {

    @Test
    fun residual_indy_becomes_marker_invokestatic_with_same_descriptor() {
        val rewritten = ResidualIndyBytecode.rewriteClass(classWithIndy(
                "enumSwitch", "java/lang/runtime/SwitchBootstraps", "(Ljava/lang/Object;I)I"))
        assertFalse(hasIndy(rewritten), "the residual indy must be gone")
        val calls = methodCalls(rewritten)
        assertTrue(calls.contains(
                ResidualIndyBytecode.MARKER_CLASS + ".enumSwitch__SwitchBootstraps(Ljava/lang/Object;I)I"),
                "expected a marker call with the indy's exact descriptor, got: $calls")
    }

    @Test
    fun marker_name_carries_indy_name_and_bootstrap_owner() {
        val objectMethods = Handle(Opcodes.H_INVOKESTATIC,
                "java/lang/runtime/ObjectMethods", "bootstrap", BSM_DESC, false)
        assertEquals("toString__ObjectMethods",
                ResidualIndyBytecode.markerMethodName("toString", objectMethods))
        // Non-identifier characters sanitize ('-' -> '_'); '$' is a legal identifier char and stays.
        val weird = Handle(Opcodes.H_INVOKESTATIC, "com/x/Weird\$Boot-strap", "b", BSM_DESC, false)
        assertEquals("apply__Weird\$Boot_strap",
                ResidualIndyBytecode.markerMethodName("apply", weird))
    }

    @Test
    fun class_without_indy_keeps_its_calls_untouched() {
        // A plain class: one static method calling String.length via invokevirtual.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Plain", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "len",
                "(Ljava/lang/String;)I", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ResidualIndyBytecode.rewriteClass(cw.toByteArray())
        assertEquals(listOf("java/lang/String.length()I"), methodCalls(rewritten))
    }

    @Test
    fun marker_survives_the_stub_noise_filter_and_other_bmc4j_plumbing_does_not() {
        val marker = ResidualIndyBytecode.MARKER_CLASS.replace('/', '.') + ".enumSwitch__SwitchBootstraps"
        assertTrue(StubFilter.isSignal(marker),
                "the residual-indy marker exists to be SEEN - the noise filter must not eat it")
        assertFalse(StubFilter.isSignal("org.bmc4j.engine.BmcStrings.equalsSound"),
                "ordinary bmc4j plumbing stays filtered")
    }

    companion object {
        private const val BSM_DESC =
                "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                        "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"

        /** A class whose single static method body is exactly one indy with the given name/bootstrap. */
        private fun classWithIndy(indyName: String, bsmOwner: String, indyDesc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "RiC", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "m",
                    indyDesc, null, null)
            mv.visitCode()
            // Load each declared argument so the indy's stack contract is satisfied.
            val args = Type.getArgumentTypes(indyDesc)
            var slot = 0
            for (t in args) {
                mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot)
                slot += t.size
            }
            val bsm = Handle(Opcodes.H_INVOKESTATIC, bsmOwner, "bootstrap", BSM_DESC, false)
            mv.visitInvokeDynamicInsn(indyName, indyDesc, bsm)
            mv.visitInsn(Type.getReturnType(indyDesc).getOpcode(Opcodes.IRETURN))
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        // ---- bytecode inspection helpers (same shape as SwitchBytecodeTest) ----

        private fun hasIndy(bytes: ByteArray): Boolean {
            val saw = booleanArrayOf(false)
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                            bsm: Handle?, vararg args: Any?) {
                            saw[0] = true
                        }
                    }
                }
            }, 0)
            return saw[0]
        }

        private fun methodCalls(bytes: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
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
