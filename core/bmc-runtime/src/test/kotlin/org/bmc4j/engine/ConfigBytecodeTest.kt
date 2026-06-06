package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Behavioral tests for [ConfigBytecode]: a `Bmc.intFromProperty("KEY")` call site is
 * rewritten to the real value when the property is set, and to a throwing redirect when it isn't.
 * Run on the real JVM (no engine needed) — set case returns the constant, unset case throws.
 */
internal class ConfigBytecodeTest {

    @Test
    fun set_property_is_baked_as_the_real_value() {
        System.setProperty("bmc.test.k", "4242")
        try {
            val cls = callingClass("Cfg\$Set", "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.k")
            val c = define("Cfg\$Set", ConfigBytecode.rewriteClass(cls))
            val f = c.getMethod("f")
            f.isAccessible = true
            assertEquals(4242, f.invoke(null) as Int)
        } finally {
            System.clearProperty("bmc.test.k")
        }
    }

    @Test
    fun unset_property_fails_the_proof() {
        val cls = callingClass("Cfg\$Unset", "intFromProperty", "(Ljava/lang/String;)I",
                "bmc.test.definitely.unset.kjsdf")
        val c = define("Cfg\$Unset", ConfigBytecode.rewriteClass(cls))
        val f = c.getMethod("f")
        f.isAccessible = true
        val ex = assertThrows(InvocationTargetException::class.java) { f.invoke(null) }
        assertTrue(ex.cause is AssertionError, "unset config should throw AssertionError")
    }

    // --- boolean readers: strict "true"/"false", malformed fails loudly ------------------------------

    @Test
    fun bool_property_parses_true_false_in_any_case() {
        System.setProperty("bmc.test.flag", "TRUE")
        try {
            val cls = callingClass("Cfg\$BoolSet", "boolFromProperty", "(Ljava/lang/String;)Z", "bmc.test.flag")
            val c = define("Cfg\$BoolSet", ConfigBytecode.rewriteClass(cls))
            val f = c.getMethod("f")
            f.isAccessible = true
            assertEquals(1, f.invoke(null) as Int, "\"TRUE\" (any case) must bake as true")
        } finally {
            System.clearProperty("bmc.test.flag")
        }
    }

    @Test
    fun malformed_bool_fails_the_proof_instead_of_baking_false() {
        System.setProperty("bmc.test.flag", "1") // truthy in many config schemes, but NOT "true"/"false"
        try {
            val cls = callingClass("Cfg\$BoolBad", "boolFromProperty", "(Ljava/lang/String;)Z", "bmc.test.flag")
            val c = define("Cfg\$BoolBad", ConfigBytecode.rewriteClass(cls))
            val f = c.getMethod("f")
            f.isAccessible = true
            val ex = assertThrows(InvocationTargetException::class.java) { f.invoke(null) }
            assertTrue(ex.cause is AssertionError,
                    "a malformed boolean must fail loudly, never silently bake false")
        } finally {
            System.clearProperty("bmc.test.flag")
        }
    }

    @Test
    fun resolvedValue_malformed_bool_is_the_unset_sentinel() {
        System.setProperty("bmc.test.flag", "yes")
        try {
            assertEquals(ConfigBytecode.UNSET, ConfigBytecode.resolvedValue(
                    "boolFromProperty", "(Ljava/lang/String;)Z", "bmc.test.flag"),
                    "malformed bool resolves like unset (thrower path), keeping cache key and bake in sync")
        } finally {
            System.clearProperty("bmc.test.flag")
        }
    }

    // --- resolvedValue / resolvedConfig: the single-source-of-truth the verdict cache folds in -------

    @Test
    fun resolvedValue_set_matches_the_baked_value() {
        System.setProperty("bmc.test.k", "4242")
        try {
            assertEquals("4242", ConfigBytecode.resolvedValue(
                    "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.k"),
                    "resolvedValue must equal the literal ConfigBytecode bakes (single source of truth)")
        } finally {
            System.clearProperty("bmc.test.k")
        }
    }

    @Test
    fun resolvedValue_unset_is_the_unset_sentinel() {
        assertEquals(ConfigBytecode.UNSET, ConfigBytecode.resolvedValue(
                "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.definitely.unset.kjsdf"),
                "an unset/unparseable key resolves to the <unset> sentinel (redirected to thrower)")
    }

    @Test
    fun resolvedConfig_reflects_referenced_keys_and_their_values(@TempDir dir: Path) {
        Files.write(dir.resolve("Cfg.class"),
                callingClass("Cfg", "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.cfgscan"))
        val prev = System.getProperty("bmc.test.cfgscan")
        try {
            System.setProperty("bmc.test.cfgscan", "11")
            val at11 = ConfigBytecode.resolvedConfig(dir.toString())
            assertTrue(at11.contains("bmc.test.cfgscan=11"),
                    "scanned config must carry the referenced key's resolved value, was: $at11")

            System.setProperty("bmc.test.cfgscan", "22")
            val at22 = ConfigBytecode.resolvedConfig(dir.toString())
            assertTrue(at22.contains("bmc.test.cfgscan=22") && at22 != at11,
                    "changing the value must change the scan result, was: $at22")
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.test.cfgscan")
            } else {
                System.setProperty("bmc.test.cfgscan", prev)
            }
        }
    }

    companion object {
        /** A class with `static int f() { return Bmc.<method>("key"); }`. */
        private fun callingClass(name: String, method: String, desc: String, key: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "()I", null, null)
            mv.visitCode()
            mv.visitLdcInsn(key)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", method, desc, false)
            mv.visitInsn(Opcodes.IRETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun define(internalName: String, bytes: ByteArray): Class<*> {
            val binary = internalName.replace('/', '.')
            return object : ClassLoader(ConfigBytecodeTest::class.java.classLoader) {
                fun go(): Class<*> = defineClass(binary, bytes, 0, bytes.size)
            }.go()
        }
    }
}
