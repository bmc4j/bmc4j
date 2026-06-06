package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Unit tests for {@link ResidualIndyBytecode}: any {@code invokedynamic} still standing after the
 * desugar passes must become an {@code invokestatic} to the deliberately-bodiless marker class —
 * same descriptor (stack-compatible drop-in), method name carrying the indy name + bootstrap owner
 * — so the engine's normal opaque-symbol reporting (and with it the whole stub policy) sees the
 * site instead of JBMC silently linking it to an unconstrained result. Synthesized with ASM like
 * {@link SwitchBytecodeTest}, so the test stays at the module's Java 17 target.
 */
class ResidualIndyBytecodeTest {

    private static final String BSM_DESC =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;";

    /** A class whose single static method body is exactly one indy with the given name/bootstrap. */
    private static byte[] classWithIndy(String indyName, String bsmOwner, String indyDesc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "RiC", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m",
                indyDesc, null, null);
        mv.visitCode();
        // Load each declared argument so the indy's stack contract is satisfied.
        org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(indyDesc);
        int slot = 0;
        for (org.objectweb.asm.Type t : args) {
            mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot);
            slot += t.getSize();
        }
        Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, bsmOwner, "bootstrap", BSM_DESC, false);
        mv.visitInvokeDynamicInsn(indyName, indyDesc, bsm);
        mv.visitInsn(org.objectweb.asm.Type.getReturnType(indyDesc).getOpcode(Opcodes.IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void residual_indy_becomes_marker_invokestatic_with_same_descriptor() {
        byte[] rewritten = ResidualIndyBytecode.rewriteClass(classWithIndy(
                "enumSwitch", "java/lang/runtime/SwitchBootstraps", "(Ljava/lang/Object;I)I"));
        assertFalse(hasIndy(rewritten), "the residual indy must be gone");
        List<String> calls = methodCalls(rewritten);
        assertTrue(calls.contains(
                        ResidualIndyBytecode.MARKER_CLASS + ".enumSwitch__SwitchBootstraps(Ljava/lang/Object;I)I"),
                "expected a marker call with the indy's exact descriptor, got: " + calls);
    }

    @Test
    void marker_name_carries_indy_name_and_bootstrap_owner() {
        Handle objectMethods = new Handle(Opcodes.H_INVOKESTATIC,
                "java/lang/runtime/ObjectMethods", "bootstrap", BSM_DESC, false);
        assertEquals("toString__ObjectMethods",
                ResidualIndyBytecode.markerMethodName("toString", objectMethods));
        // Non-identifier characters sanitize ('-' -> '_'); '$' is a legal identifier char and stays.
        Handle weird = new Handle(Opcodes.H_INVOKESTATIC, "com/x/Weird$Boot-strap", "b", BSM_DESC, false);
        assertEquals("apply__Weird$Boot_strap",
                ResidualIndyBytecode.markerMethodName("apply", weird));
    }

    @Test
    void class_without_indy_keeps_its_calls_untouched() {
        // A plain class: one static method calling String.length via invokevirtual.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Plain", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "len",
                "(Ljava/lang/String;)I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] rewritten = ResidualIndyBytecode.rewriteClass(cw.toByteArray());
        assertEquals(List.of("java/lang/String.length()I"), methodCalls(rewritten));
    }

    @Test
    void marker_survives_the_stub_noise_filter_and_other_bmc4j_plumbing_does_not() {
        String marker = ResidualIndyBytecode.MARKER_CLASS.replace('/', '.') + ".enumSwitch__SwitchBootstraps";
        assertTrue(StubFilter.isSignal(marker),
                "the residual-indy marker exists to be SEEN - the noise filter must not eat it");
        assertFalse(StubFilter.isSignal("org.bmc4j.engine.BmcStrings.equalsSound"),
                "ordinary bmc4j plumbing stays filtered");
    }

    // ---- bytecode inspection helpers (same shape as SwitchBytecodeTest) ----

    private static boolean hasIndy(byte[] bytes) {
        boolean[] saw = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
                        saw[0] = true;
                    }
                };
            }
        }, 0);
        return saw[0];
    }

    private static List<String> methodCalls(byte[] bytes) {
        List<String> calls = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        calls.add(owner + "." + name + desc);
                    }
                };
            }
        }, 0);
        return calls;
    }
}
