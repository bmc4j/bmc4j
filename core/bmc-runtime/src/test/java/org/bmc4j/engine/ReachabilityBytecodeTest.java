package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Tests for {@link ReachabilityBytecode}: every {@code @BmcProof} method's {@code return} is replaced
 * by the reachability marker ({@code throw new AssertionError}) on the sentinel line, while non-proof
 * methods are left untouched. The marker is what makes a vacuous proof visible.
 */
class ReachabilityBytecodeTest {

    @Test
    void proof_method_return_is_replaced_by_a_throwing_marker() throws Exception {
        byte[] in = sampleClass("Reach$Proof", true);
        byte[] out = ReachabilityBytecode.rewriteClass(in);
        Class<?> c = define("Reach$Proof", out);
        Method f = c.getMethod("f");
        f.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> f.invoke(null));
        assertTrue(ex.getCause() instanceof AssertionError, "proof return should become a marker throw");
        assertTrue(hasSentinelLine(out), "marker must be stamped on the sentinel source line");
    }

    @Test
    void non_proof_method_is_left_untouched() throws Exception {
        byte[] in = sampleClass("Reach$Plain", false);
        byte[] out = ReachabilityBytecode.rewriteClass(in);
        Class<?> c = define("Reach$Plain", out);
        Method f = c.getMethod("f");
        f.setAccessible(true);
        assertEquals(null, f.invoke(null)); // returns normally; no marker injected
        assertFalse(hasSentinelLine(out), "non-proof methods must not get a marker");
    }

    /** A class with one {@code static void f() { return; }}, optionally {@code @BmcProof}-annotated. */
    private static byte[] sampleClass(String name, boolean proof) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "f", "()V", null, null);
        if (proof) {
            mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd();
        }
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static boolean hasSentinelLine(byte[] bytes) {
        AtomicBoolean found = new AtomicBoolean(false);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLineNumber(int line, Label start) {
                        if (line == BmcReachability.SENTINEL_LINE) {
                            found.set(true);
                        }
                    }
                };
            }
        }, 0);
        return found.get();
    }

    private static Class<?> define(String internalName, byte[] bytes) {
        String binary = internalName.replace('/', '.');
        return new ClassLoader(ReachabilityBytecodeTest.class.getClassLoader()) {
            Class<?> go() {
                return defineClass(binary, bytes, 0, bytes.length);
            }
        }.go();
    }
}
