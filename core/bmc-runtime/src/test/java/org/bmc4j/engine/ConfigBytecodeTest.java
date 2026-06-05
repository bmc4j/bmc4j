package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Behavioral tests for {@link ConfigBytecode}: a {@code Bmc.intFromProperty("KEY")} call site is
 * rewritten to the real value when the property is set, and to a throwing redirect when it isn't.
 * Run on the real JVM (no engine needed) — set case returns the constant, unset case throws.
 */
class ConfigBytecodeTest {

    @Test
    void set_property_is_baked_as_the_real_value() throws Exception {
        System.setProperty("bmc.test.k", "4242");
        try {
            byte[] cls = callingClass("Cfg$Set", "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.k");
            Class<?> c = define("Cfg$Set", ConfigBytecode.rewriteClass(cls));
            Method f = c.getMethod("f");
            f.setAccessible(true);
            assertEquals(4242, (int) f.invoke(null));
        } finally {
            System.clearProperty("bmc.test.k");
        }
    }

    @Test
    void unset_property_fails_the_proof() throws Exception {
        byte[] cls = callingClass("Cfg$Unset", "intFromProperty", "(Ljava/lang/String;)I",
                "bmc.test.definitely.unset.kjsdf");
        Class<?> c = define("Cfg$Unset", ConfigBytecode.rewriteClass(cls));
        Method f = c.getMethod("f");
        f.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> f.invoke(null));
        assertTrue(ex.getCause() instanceof AssertionError, "unset config should throw AssertionError");
    }

    // --- resolvedValue / resolvedConfig: the single-source-of-truth the verdict cache folds in -------

    @Test
    void resolvedValue_set_matches_the_baked_value() {
        System.setProperty("bmc.test.k", "4242");
        try {
            assertEquals("4242", ConfigBytecode.resolvedValue(
                    "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.k"),
                    "resolvedValue must equal the literal ConfigBytecode bakes (single source of truth)");
        } finally {
            System.clearProperty("bmc.test.k");
        }
    }

    @Test
    void resolvedValue_unset_is_the_unset_sentinel() {
        assertEquals(ConfigBytecode.UNSET, ConfigBytecode.resolvedValue(
                "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.definitely.unset.kjsdf"),
                "an unset/unparseable key resolves to the <unset> sentinel (redirected to thrower)");
    }

    @Test
    void resolvedConfig_reflects_referenced_keys_and_their_values(@org.junit.jupiter.api.io.TempDir
                                                                  java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.write(dir.resolve("Cfg.class"),
                callingClass("Cfg", "intFromProperty", "(Ljava/lang/String;)I", "bmc.test.cfgscan"));
        String prev = System.getProperty("bmc.test.cfgscan");
        try {
            System.setProperty("bmc.test.cfgscan", "11");
            String at11 = ConfigBytecode.resolvedConfig(dir.toString());
            assertTrue(at11.contains("bmc.test.cfgscan=11"),
                    "scanned config must carry the referenced key's resolved value, was: " + at11);

            System.setProperty("bmc.test.cfgscan", "22");
            String at22 = ConfigBytecode.resolvedConfig(dir.toString());
            assertTrue(at22.contains("bmc.test.cfgscan=22") && !at22.equals(at11),
                    "changing the value must change the scan result, was: " + at22);
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.test.cfgscan");
            } else {
                System.setProperty("bmc.test.cfgscan", prev);
            }
        }
    }

    /** A class with {@code static int f() { return Bmc.<method>("key"); }}. */
    private static byte[] callingClass(String name, String method, String desc, String key) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "f", "()I", null, null);
        mv.visitCode();
        mv.visitLdcInsn(key);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", method, desc, false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Class<?> define(String internalName, byte[] bytes) {
        String binary = internalName.replace('/', '.');
        return new ClassLoader(ConfigBytecodeTest.class.getClassLoader()) {
            Class<?> go() {
                return defineClass(binary, bytes, 0, bytes.length);
            }
        }.go();
    }
}
