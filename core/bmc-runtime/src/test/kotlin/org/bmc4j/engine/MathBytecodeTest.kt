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
import org.objectweb.asm.Type

/** Unit tests for [MathBytecode]'s redirect of the unmodeled integer `Math.*` methods to
 *  [BmcMath]. The call-site rewrite needs no engine; the soundness of the redirected values is
 *  pinned both here (BmcMath computed on a real JVM) and end-to-end by the MathLaws model proofs. */
internal class MathBytecodeTest {

    // ---- the redirect happens for every targeted method, with the descriptor unchanged ----

    @Test
    fun redirects_unmodeled_integer_math_to_BmcMath() {
        assertRedirected("floorDiv", "(II)I")
        assertRedirected("floorDiv", "(JJ)J")
        assertRedirected("floorDiv", "(JI)J")
        assertRedirected("floorMod", "(II)I")
        assertRedirected("floorMod", "(JJ)J")
        assertRedirected("floorMod", "(JI)I")
        assertRedirected("addExact", "(II)I")
        assertRedirected("addExact", "(JJ)J")
        assertRedirected("subtractExact", "(II)I")
        assertRedirected("subtractExact", "(JJ)J")
        assertRedirected("multiplyExact", "(II)I")
        assertRedirected("multiplyExact", "(JJ)J")
        assertRedirected("multiplyExact", "(JI)J")
        assertRedirected("negateExact", "(I)I")
        assertRedirected("negateExact", "(J)J")
        assertRedirected("incrementExact", "(I)I")
        assertRedirected("incrementExact", "(J)J")
        assertRedirected("decrementExact", "(I)I")
        assertRedirected("decrementExact", "(J)J")
        assertRedirected("toIntExact", "(J)I")
        assertRedirected("absExact", "(I)I")
        assertRedirected("absExact", "(J)J")
        assertRedirected("abs", "(I)I")
        assertRedirected("abs", "(J)J")
    }

    @Test
    fun leaves_modeled_math_calls_untouched() {
        // sqrt/pow/sin etc. are soundly modeled by JBMC; they must NOT be redirected (keeps the real
        // math models). Also abs(double) is a floating overload we do not touch.
        for (m in arrayOf(
                arrayOf("sqrt", "(D)D"), arrayOf("pow", "(DD)D"), arrayOf("sin", "(D)D"),
                arrayOf("abs", "(D)D"), arrayOf("abs", "(F)F"))) {
            val calls = methodCalls(MathBytecode.rewriteClass(
                    classCallingStatic("java/lang/Math", m[0], m[1])))
            assertTrue(calls.any { it.contains("java/lang/Math." + m[0] + m[1]) },
                    "Math." + m[0] + m[1] + " must pass through: " + calls)
            assertFalse(calls.any { it.contains("BmcMath") },
                    "BmcMath redirect must not fire for " + m[0] + m[1])
        }
    }

    // ---- BmcMath computes the correct values (the sound stand-ins, on a real JVM) ----

    @Test
    fun bmcMath_floorDiv_floorMod_match_jdk_including_negatives() {
        assertEquals(-3, BmcMath.floorDiv(-7, 3))
        assertEquals(2, BmcMath.floorMod(-7, 3))
        assertEquals(-3L, BmcMath.floorDiv(-7L, 3L))
        assertEquals(2L, BmcMath.floorMod(-7L, 3L))
        assertEquals(-3L, BmcMath.floorDiv(-7L, 3))
        assertEquals(2, BmcMath.floorMod(-7L, 3))
        // Cross-check a swept range against the JDK on a real JVM.
        for (a in -20..20) {
            for (b in -7..7) {
                if (b == 0) {
                    continue
                }
                assertEquals(Math.floorDiv(a, b), BmcMath.floorDiv(a, b), "floorDiv $a/$b")
                assertEquals(Math.floorMod(a, b), BmcMath.floorMod(a, b), "floorMod $a%$b")
            }
        }
    }

    @Test
    fun bmcMath_exact_family_matches_jdk_in_range() {
        assertEquals(5, BmcMath.addExact(2, 3))
        assertEquals(-1, BmcMath.subtractExact(2, 3))
        assertEquals(20, BmcMath.multiplyExact(4, 5))
        assertEquals(-9, BmcMath.negateExact(9))
        assertEquals(8, BmcMath.incrementExact(7))
        assertEquals(6, BmcMath.decrementExact(7))
        assertEquals(5, BmcMath.toIntExact(5L))
        assertEquals(7, BmcMath.absExact(-7))
        assertEquals(7, BmcMath.abs(-7))
        assertEquals(7L, BmcMath.abs(-7L))
    }

    @Test
    fun bmcMath_exact_family_is_loud_on_overflow() {
        // Matches the JDK: each *Exact throws ArithmeticException on overflow (JBMC sees it as a
        // property violation, so overflow is flagged, never silently wrapped).
        assertThrows(ArithmeticException::class.java) { BmcMath.addExact(Integer.MAX_VALUE, 1) }
        assertThrows(ArithmeticException::class.java) { BmcMath.addExact(Long.MAX_VALUE, 1L) }
        assertThrows(ArithmeticException::class.java) { BmcMath.subtractExact(Integer.MIN_VALUE, 1) }
        assertThrows(ArithmeticException::class.java) { BmcMath.multiplyExact(Integer.MAX_VALUE, 2) }
        assertThrows(ArithmeticException::class.java) { BmcMath.multiplyExact(Long.MAX_VALUE, 2L) }
        assertThrows(ArithmeticException::class.java) { BmcMath.multiplyExact(Long.MIN_VALUE, -1L) }
        assertThrows(ArithmeticException::class.java) { BmcMath.negateExact(Integer.MIN_VALUE) }
        assertThrows(ArithmeticException::class.java) { BmcMath.incrementExact(Integer.MAX_VALUE) }
        assertThrows(ArithmeticException::class.java) { BmcMath.decrementExact(Integer.MIN_VALUE) }
        assertThrows(ArithmeticException::class.java) { BmcMath.toIntExact(Integer.MAX_VALUE.toLong() + 1) }
        assertThrows(ArithmeticException::class.java) { BmcMath.absExact(Integer.MIN_VALUE) }
        assertThrows(ArithmeticException::class.java) { BmcMath.absExact(Long.MIN_VALUE) }
        // floorDiv/floorMod by zero throw, exactly like the JDK.
        assertThrows(ArithmeticException::class.java) { BmcMath.floorDiv(1, 0) }
        assertThrows(ArithmeticException::class.java) { BmcMath.floorMod(1, 0) }
    }

    companion object {
        private fun assertRedirected(name: String, desc: String) {
            val calls = methodCalls(MathBytecode.rewriteClass(
                    classCallingStatic("java/lang/Math", name, desc)))
            assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcMath.$name$desc"),
                    "$name$desc should be redirected to BmcMath: $calls")
            assertFalse(calls.any { it.contains("java/lang/Math.$name") },
                    "original Math.$name call must be gone")
        }

        // ---- helpers ----

        /** A class with a method that makes one static call to owner.name desc, with nondescript args. */
        private fun classCallingStatic(owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val args = Type.getArgumentTypes(desc)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use", desc, null, null)
            mv.visitCode()
            var slot = 0
            for (t in args) {
                mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot)
                slot += t.size
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false)
            val ret = Type.getReturnType(desc)
            mv.visitInsn(ret.getOpcode(Opcodes.IRETURN))
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            val kind = when (op) {
                                Opcodes.INVOKESTATIC -> "INVOKESTATIC"
                                Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
                                else -> "INVOKE"
                            }
                            calls.add("$kind $owner.$name$desc")
                        }
                    }
                }
            }, 0)
            return calls
        }
    }
}
