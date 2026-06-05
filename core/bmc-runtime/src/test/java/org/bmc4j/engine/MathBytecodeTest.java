package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Unit tests for {@link MathBytecode}'s redirect of the unmodeled integer {@code Math.*} methods to
 *  {@link BmcMath}. The call-site rewrite needs no engine; the soundness of the redirected values is
 *  pinned both here (BmcMath computed on a real JVM) and end-to-end by the MathLaws model proofs. */
class MathBytecodeTest {

    // ---- the redirect happens for every targeted method, with the descriptor unchanged ----

    @Test
    void redirects_unmodeled_integer_math_to_BmcMath() {
        assertRedirected("floorDiv", "(II)I");
        assertRedirected("floorDiv", "(JJ)J");
        assertRedirected("floorDiv", "(JI)J");
        assertRedirected("floorMod", "(II)I");
        assertRedirected("floorMod", "(JJ)J");
        assertRedirected("floorMod", "(JI)I");
        assertRedirected("addExact", "(II)I");
        assertRedirected("addExact", "(JJ)J");
        assertRedirected("subtractExact", "(II)I");
        assertRedirected("subtractExact", "(JJ)J");
        assertRedirected("multiplyExact", "(II)I");
        assertRedirected("multiplyExact", "(JJ)J");
        assertRedirected("multiplyExact", "(JI)J");
        assertRedirected("negateExact", "(I)I");
        assertRedirected("negateExact", "(J)J");
        assertRedirected("incrementExact", "(I)I");
        assertRedirected("incrementExact", "(J)J");
        assertRedirected("decrementExact", "(I)I");
        assertRedirected("decrementExact", "(J)J");
        assertRedirected("toIntExact", "(J)I");
        assertRedirected("absExact", "(I)I");
        assertRedirected("absExact", "(J)J");
        assertRedirected("abs", "(I)I");
        assertRedirected("abs", "(J)J");
    }

    private static void assertRedirected(String name, String desc) {
        List<String> calls = methodCalls(MathBytecode.rewriteClass(
                classCallingStatic("java/lang/Math", name, desc)));
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcMath." + name + desc),
                name + desc + " should be redirected to BmcMath: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("java/lang/Math." + name)),
                "original Math." + name + " call must be gone");
    }

    @Test
    void leaves_modeled_math_calls_untouched() {
        // sqrt/pow/sin etc. are soundly modeled by JBMC; they must NOT be redirected (keeps the real
        // math models). Also abs(double) is a floating overload we do not touch.
        for (String[] m : new String[][] {
                {"sqrt", "(D)D"}, {"pow", "(DD)D"}, {"sin", "(D)D"}, {"abs", "(D)D"}, {"abs", "(F)F"}}) {
            List<String> calls = methodCalls(MathBytecode.rewriteClass(
                    classCallingStatic("java/lang/Math", m[0], m[1])));
            assertTrue(calls.stream().anyMatch(c -> c.contains("java/lang/Math." + m[0] + m[1])),
                    "Math." + m[0] + m[1] + " must pass through: " + calls);
            assertFalse(calls.stream().anyMatch(c -> c.contains("BmcMath")),
                    "BmcMath redirect must not fire for " + m[0] + m[1]);
        }
    }

    // ---- BmcMath computes the correct values (the sound stand-ins, on a real JVM) ----

    @Test
    void bmcMath_floorDiv_floorMod_match_jdk_including_negatives() {
        assertEquals(-3, BmcMath.floorDiv(-7, 3));
        assertEquals(2, BmcMath.floorMod(-7, 3));
        assertEquals(-3L, BmcMath.floorDiv(-7L, 3L));
        assertEquals(2L, BmcMath.floorMod(-7L, 3L));
        assertEquals(-3L, BmcMath.floorDiv(-7L, 3));
        assertEquals(2, BmcMath.floorMod(-7L, 3));
        // Cross-check a swept range against the JDK on a real JVM.
        for (int a = -20; a <= 20; a++) {
            for (int b = -7; b <= 7; b++) {
                if (b == 0) {
                    continue;
                }
                assertEquals(Math.floorDiv(a, b), BmcMath.floorDiv(a, b), "floorDiv " + a + "/" + b);
                assertEquals(Math.floorMod(a, b), BmcMath.floorMod(a, b), "floorMod " + a + "%" + b);
            }
        }
    }

    @Test
    void bmcMath_exact_family_matches_jdk_in_range() {
        assertEquals(5, BmcMath.addExact(2, 3));
        assertEquals(-1, BmcMath.subtractExact(2, 3));
        assertEquals(20, BmcMath.multiplyExact(4, 5));
        assertEquals(-9, BmcMath.negateExact(9));
        assertEquals(8, BmcMath.incrementExact(7));
        assertEquals(6, BmcMath.decrementExact(7));
        assertEquals(5, BmcMath.toIntExact(5L));
        assertEquals(7, BmcMath.absExact(-7));
        assertEquals(7, BmcMath.abs(-7));
        assertEquals(7L, BmcMath.abs(-7L));
    }

    @Test
    void bmcMath_exact_family_is_loud_on_overflow() {
        // Matches the JDK: each *Exact throws ArithmeticException on overflow (JBMC sees it as a
        // property violation, so overflow is flagged, never silently wrapped).
        assertThrows(ArithmeticException.class, () -> BmcMath.addExact(Integer.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class, () -> BmcMath.addExact(Long.MAX_VALUE, 1L));
        assertThrows(ArithmeticException.class, () -> BmcMath.subtractExact(Integer.MIN_VALUE, 1));
        assertThrows(ArithmeticException.class, () -> BmcMath.multiplyExact(Integer.MAX_VALUE, 2));
        assertThrows(ArithmeticException.class, () -> BmcMath.multiplyExact(Long.MAX_VALUE, 2L));
        assertThrows(ArithmeticException.class, () -> BmcMath.multiplyExact(Long.MIN_VALUE, -1L));
        assertThrows(ArithmeticException.class, () -> BmcMath.negateExact(Integer.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> BmcMath.incrementExact(Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> BmcMath.decrementExact(Integer.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> BmcMath.toIntExact((long) Integer.MAX_VALUE + 1));
        assertThrows(ArithmeticException.class, () -> BmcMath.absExact(Integer.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> BmcMath.absExact(Long.MIN_VALUE));
        // floorDiv/floorMod by zero throw, exactly like the JDK.
        assertThrows(ArithmeticException.class, () -> BmcMath.floorDiv(1, 0));
        assertThrows(ArithmeticException.class, () -> BmcMath.floorMod(1, 0));
    }

    // ---- helpers ----

    /** A class with a method that makes one static call to owner.name desc, with nondescript args. */
    private static byte[] classCallingStatic(String owner, String name, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null);
        org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(desc);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", desc, null, null);
        mv.visitCode();
        int slot = 0;
        for (org.objectweb.asm.Type t : args) {
            mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot);
            slot += t.getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false);
        org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(desc);
        mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static List<String> methodCalls(byte[] clazz) {
        List<String> calls = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        String kind = op == Opcodes.INVOKESTATIC ? "INVOKESTATIC"
                                : op == Opcodes.INVOKEVIRTUAL ? "INVOKEVIRTUAL" : "INVOKE";
                        calls.add(kind + " " + owner + "." + name + desc);
                    }
                };
            }
        }, 0);
        return calls;
    }
}
