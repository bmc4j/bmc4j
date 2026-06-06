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
 * Unit tests for [SwitchBytecode]'s `SwitchBootstraps.typeSwitch` desugar. We synthesize
 * the `typeSwitch` invokedynamic directly with ASM (so the test stays at the module's Java 17
 * target — pattern `switch` source would need Java 21), then load the rewritten class and check
 * the generated helper reproduces the typeSwitch contract exactly: null -> -1, first matching label
 * index `>= restartIndex`, else `labels.length`; type labels by `instanceof`, String labels by value.
 * The sound-over-symbolic end-to-end is covered by the patternswitch example.
 */
internal class SwitchBytecodeTest {

    @Test
    fun typeSwitch_indy_is_replaced_by_a_static_helper() {
        val rewritten = SwitchBytecode.rewriteClass(classWithTypeSwitch(
                Type.getObjectType("java/lang/Integer"),
                Type.getObjectType("java/lang/String")))
        val calls = methodCalls(rewritten)
        assertFalse(sawTypeSwitchIndy(rewritten), "typeSwitch indy must be gone")
        assertTrue(calls.any { it.contains("SwC.bmc\$typeSwitch\$0") },
                "indy should be replaced by a call to a generated helper: $calls")
    }

    @Test
    fun helper_reproduces_the_typeSwitch_contract() {
        val c = define("SwC", SwitchBytecode.rewriteClass(classWithTypeSwitch(
                Type.getObjectType("java/lang/Integer"),
                Type.getObjectType("java/lang/String"))))
        val sw = c.getMethod("sw", Any::class.java, Int::class.javaPrimitiveType)

        // null -> -1
        assertEquals(-1, sw.invoke(null, null, 0))
        // Integer matches label 0
        assertEquals(0, sw.invoke(null, 42, 0))
        // String matches label 1
        assertEquals(1, sw.invoke(null, "hi", 0))
        // restartIndex skips an otherwise-matching earlier label (guard re-entry)
        assertEquals(2, sw.invoke(null, 42, 1)) // Integer's label 0 is below restartIndex -> no match
        // no match -> labels.length
        assertEquals(2, sw.invoke(null, 3.14, 0))
    }

    @Test
    fun string_constant_label_is_structurally_desugared() {
        // String-label *content* matching routes through BmcStrings/CProverString, which only has
        // meaning inside JBMC (charAt returns '\0' on a real JVM), so we don't execute that path
        // here — the matching is checked end-to-end by the patternswitch example under JBMC. We do
        // confirm the site is desugared (indy gone) and the helper calls the sound BmcStrings.objEquals.
        val rewritten = SwitchBytecode.rewriteClass(classWithTypeSwitch(
                "hi", Type.getObjectType("java/lang/String")))
        assertFalse(sawTypeSwitchIndy(rewritten), "typeSwitch indy must be gone")
        assertTrue(methodCalls(rewritten).any {
            it == "org/bmc4j/engine/BmcStrings.objEquals(Ljava/lang/Object;Ljava/lang/Object;)Z"
        }, "String constant label must compare via the sound BmcStrings.objEquals")
    }

    @Test
    fun unrecognised_label_leaves_indy_untouched() {
        // A boxed-long label IS recognised; use a bare Handle (an unexpected label kind) to force the
        // soundness bail-out: the whole site must be left as an indy rather than desugared unsoundly.
        val weird = Handle(Opcodes.H_INVOKESTATIC, "X", "y", "()V", false)
        val rewritten = SwitchBytecode.rewriteClass(classWithTypeSwitch(weird))
        assertTrue(sawTypeSwitchIndy(rewritten),
                "an unrecognised label kind must leave the typeSwitch indy in place")
    }

    @Test
    fun boxed_integer_constant_label_matches_by_value() {
        val c = define("SwC", SwitchBytecode.rewriteClass(classWithTypeSwitch(
                Integer.valueOf(7), Type.getObjectType("java/lang/Integer"))))
        val sw = c.getMethod("sw", Any::class.java, Int::class.javaPrimitiveType)
        assertEquals(0, sw.invoke(null, 7, 0))   // equals the constant 7
        assertEquals(1, sw.invoke(null, 9, 0))   // an Integer but not 7 -> the Integer type label
        assertEquals(2, sw.invoke(null, "x", 0)) // neither -> default
    }

    // ---- enumSwitch --------------------------------------------------------------------------

    /** Fixture enum for enumSwitch helpers: constants resolvable via GETSTATIC at test runtime. */
    enum class Color { RED, GREEN, BLUE }

    @Test
    fun enumSwitch_indy_is_replaced_by_a_static_helper() {
        val rewritten = SwitchBytecode.rewriteClass(classWithEnumSwitch("RED", "GREEN"))
        assertFalse(sawIndyNamed(rewritten, "enumSwitch"), "enumSwitch indy must be gone")
        assertTrue(methodCalls(rewritten).any { it.startsWith("EsC.bmc\$enumSwitch\$0") },
                "indy should be replaced by a call to a generated helper")
    }

    @Test
    fun enumSwitch_helper_reproduces_the_contract() {
        val c = define("EsC", SwitchBytecode.rewriteClass(classWithEnumSwitch("RED", "GREEN", "BLUE")))
        val sw = c.getMethod("sw", Color::class.java, Int::class.javaPrimitiveType)

        assertEquals(-1, sw.invoke(null, null, 0))            // null -> -1
        assertEquals(0, sw.invoke(null, Color.RED, 0))        // identity match -> label index
        assertEquals(1, sw.invoke(null, Color.GREEN, 0))
        assertEquals(2, sw.invoke(null, Color.BLUE, 0))
        // RED with restartIndex=1: its own label is skipped, nothing later matches -> default (3)
        assertEquals(3, sw.invoke(null, Color.RED, 1))
    }

    @Test
    fun enumSwitch_restartIndex_skips_earlier_labels_to_default() {
        val c = define("EsC", SwitchBytecode.rewriteClass(classWithEnumSwitch("RED", "GREEN")))
        val sw = c.getMethod("sw", Color::class.java, Int::class.javaPrimitiveType)
        // RED with restartIndex=1: its own label (index 0) is skipped, GREEN doesn't match -> 2 (default)
        assertEquals(2, sw.invoke(null, Color.RED, 1))
        // GREEN with restartIndex=1: still matches its own label
        assertEquals(1, sw.invoke(null, Color.GREEN, 1))
    }

    @Test
    fun enumSwitch_type_pattern_label_matches_by_instanceof() {
        val c = define("EsC", SwitchBytecode.rewriteClass(classWithEnumSwitch(
                "RED", Type.getObjectType(COLOR))))
        val sw = c.getMethod("sw", Color::class.java, Int::class.javaPrimitiveType)
        assertEquals(0, sw.invoke(null, Color.RED, 0))   // identity label first
        assertEquals(1, sw.invoke(null, Color.BLUE, 0))  // falls to the type-pattern label
    }

    @Test
    fun enumSwitch_unknown_label_kind_leaves_indy_untouched() {
        val weird = Handle(Opcodes.H_INVOKESTATIC, "X", "y", "()V", false)
        val rewritten = SwitchBytecode.rewriteClass(classWithEnumSwitch("RED", weird))
        assertTrue(sawIndyNamed(rewritten, "enumSwitch"),
                "an unrecognised enumSwitch label kind must leave the indy for the residual pass")
    }

    companion object {
        private const val BSM_OWNER = "java/lang/runtime/SwitchBootstraps"
        private const val BSM_DESC =
                "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                        "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"

        private val COLOR = Color::class.java.name.replace('.', '/')

        /**
         * Build `static int sw(Object target, int restartIndex)` whose body is exactly a
         * `typeSwitch` indy over the given labels (Type for a class label, String for a constant),
         * returning the indy result. After desugaring this becomes our helper, so invoking it tests the
         * contract directly.
         */
        private fun classWithTypeSwitch(vararg labels: Any): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "SwC", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "sw",
                    "(Ljava/lang/Object;I)I", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0) // target
            mv.visitVarInsn(Opcodes.ILOAD, 1) // restartIndex
            val bsm = Handle(Opcodes.H_INVOKESTATIC, BSM_OWNER, "typeSwitch", BSM_DESC, false)
            mv.visitInvokeDynamicInsn("typeSwitch", "(Ljava/lang/Object;I)I", bsm, *labels)
            mv.visitInsn(Opcodes.IRETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /**
         * Build `static int sw(Color target, int restartIndex)` whose body is exactly one
         * `enumSwitch` indy with the given labels (String = a constant name of the SELECTOR enum,
         * matched by identity; Type = a type-pattern label).
         */
        private fun classWithEnumSwitch(vararg labels: Any): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "EsC", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "sw",
                    "(L$COLOR;I)I", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitVarInsn(Opcodes.ILOAD, 1)
            val bsm = Handle(Opcodes.H_INVOKESTATIC, BSM_OWNER, "enumSwitch", BSM_DESC, false)
            mv.visitInvokeDynamicInsn("enumSwitch", "(L$COLOR;I)I", bsm, *labels)
            mv.visitInsn(Opcodes.IRETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        // ---- helpers -----------------------------------------------------------------------------

        private fun sawTypeSwitchIndy(clazz: ByteArray): Boolean = sawIndyNamed(clazz, "typeSwitch")

        private fun sawIndyNamed(clazz: ByteArray, indyName: String): Boolean {
            val saw = booleanArrayOf(false)
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                            bsm: Handle?, vararg args: Any?) {
                            if (BSM_OWNER == bsm?.owner && name == indyName) {
                                saw[0] = true
                            }
                        }
                    }
                }
            }, 0)
            return saw[0]
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
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

        private fun define(name: String, bytes: ByteArray): Class<*> =
                ChildFirst(SwitchBytecodeTest::class.java.classLoader, mapOf(name to bytes)).defineNamed(name)
    }

    /** Loads the named classes from given bytes (child-first), delegating everything else to parent. */
    private class ChildFirst(parent: ClassLoader, private val defs: Map<String, ByteArray>) :
            ClassLoader(parent) {

        fun defineNamed(name: String): Class<*> {
            val b = defs[name]!!
            return defineClass(name, b, 0, b.size)
        }

        @Throws(ClassNotFoundException::class)
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (defs.containsKey(name)) {
                synchronized(getClassLoadingLock(name)) {
                    var c = findLoadedClass(name)
                    if (c == null) {
                        c = defineNamed(name)
                    }
                    if (resolve) {
                        resolveClass(c)
                    }
                    return c
                }
            }
            return super.loadClass(name, resolve)
        }
    }
}
