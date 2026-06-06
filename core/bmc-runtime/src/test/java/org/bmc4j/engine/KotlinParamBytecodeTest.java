package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Unit tests for {@link KotlinParamBytecode}: the kotlinc {@code checkNotNullParameter} prologue is
 * redirected to the assume shim inside {@code @BmcProof} methods ONLY — interior (un-annotated)
 * methods keep the throwing intrinsic, which is what keeps a null flowing into a non-null parameter
 * <em>inside</em> the analyzed code a real, refutable bug. End-to-end semantics are pinned by the
 * {@code proofs.symbolicparams} examples.
 */
class KotlinParamBytecodeTest {

    private static final String INTRINSICS = "kotlin/jvm/internal/Intrinsics";
    private static final String DESC = "(Ljava/lang/Object;Ljava/lang/String;)V";
    private static final String SHIM = "org/bmc4j/engine/BmcKotlin.assumeNotNullParameter" + DESC;

    @Test
    void redirects_param_check_inside_proof_methods() {
        List<String> calls = methodCalls(KotlinParamBytecode.rewriteClass(
                classWithCheck(true, "checkNotNullParameter")));
        assertTrue(calls.contains("INVOKESTATIC " + SHIM),
                "proof-method prologue should be redirected to the assume shim: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("Intrinsics.checkNotNullParameter")),
                "original intrinsic call must be gone: " + calls);
    }

    @Test
    void redirects_legacy_param_check_name() {
        List<String> calls = methodCalls(KotlinParamBytecode.rewriteClass(
                classWithCheck(true, "checkParameterIsNotNull")));
        assertTrue(calls.contains("INVOKESTATIC " + SHIM),
                "legacy-named prologue should be redirected too: " + calls);
    }

    @Test
    void leaves_interior_methods_throwing() {
        // The same call in an UN-annotated method — an interior callee — must keep the throwing
        // intrinsic: a null reaching a non-null parameter inside analyzed code is a real bug.
        List<String> calls = methodCalls(KotlinParamBytecode.rewriteClass(
                classWithCheck(false, "checkNotNullParameter")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("Intrinsics.checkNotNullParameter")),
                "interior parameter check must keep throwing semantics: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("BmcKotlin")),
                "no redirect outside @BmcProof methods: " + calls);
    }

    @Test
    void leaves_other_intrinsics_untouched_even_in_proof_methods() {
        // checkNotNull / checkNotNullExpressionValue back !!, lateinit, platform-type asserts —
        // those are the user's own null-safety logic and stay refutable inside proofs.
        for (String other : new String[] {"checkNotNullExpressionValue", "checkNotNull"}) {
            String desc = other.equals("checkNotNull") ? "(Ljava/lang/Object;)V" : DESC;
            List<String> calls = methodCalls(KotlinParamBytecode.rewriteClass(
                    classWithCall(true, other, desc)));
            assertTrue(calls.stream().anyMatch(c -> c.contains("Intrinsics." + other)),
                    other + " must pass through: " + calls);
            assertFalse(calls.stream().anyMatch(c -> c.contains("BmcKotlin")),
                    "no redirect for " + other + ": " + calls);
        }
    }

    @Test
    void strips_notnull_param_annotation_from_proof_methods() {
        // kotlinc also stamps @org.jetbrains.annotations.NotNull on the parameter, and JBMC asserts
        // it against the entry function's nondet inputs ("Not null annotation check") — it must go
        // wherever the prologue is relaxed, or the annotation re-imposes the refutation.
        assertFalse(paramAnnotations(KotlinParamBytecode.rewriteClass(
                        classWithCheck(true, "checkNotNullParameter")))
                        .contains("Lorg/jetbrains/annotations/NotNull;"),
                "@NotNull must be stripped from proof-method parameters");
    }

    @Test
    void keeps_notnull_param_annotation_on_interior_methods() {
        assertTrue(paramAnnotations(KotlinParamBytecode.rewriteClass(
                        classWithCheck(false, "checkNotNullParameter")))
                        .contains("Lorg/jetbrains/annotations/NotNull;"),
                "interior methods keep their @NotNull parameter annotations");
    }

    @Test
    void honest_jvm_flag_skips_the_pass() {
        // -Dbmc.kotlinNullableParams=true restores the throwing prologue: the pass returns the
        // classpath untouched (no mirror is even attempted — the path below doesn't exist).
        String prev = System.getProperty("bmc.kotlinNullableParams");
        try {
            System.setProperty("bmc.kotlinNullableParams", "true");
            org.junit.jupiter.api.Assertions.assertEquals("/no/such/classes",
                    KotlinParamBytecode.rewrite("/no/such/classes"),
                    "honest-JVM mode must leave the classpath untouched");
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.kotlinNullableParams");
            } else {
                System.setProperty("bmc.kotlinNullableParams", prev);
            }
        }
    }

    // ---- helpers ----

    private static byte[] classWithCheck(boolean annotated, String checkName) {
        return classWithCall(annotated, checkName, DESC);
    }

    /** A class with one method (optionally @BmcProof-annotated) making one Intrinsics static call. */
    private static byte[] classWithCall(boolean annotated, String name, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "(Ljava/lang/Object;)V", null, null);
        if (annotated) {
            mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd();
        }
        mv.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false).visitEnd();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        if (DESC.equals(desc)) {
            mv.visitLdcInsn("p");
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, INTRINSICS, name, desc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static List<String> paramAnnotations(byte[] clazz) {
        List<String> annotations = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitParameterAnnotation(
                            int parameter, String desc, boolean visible) {
                        annotations.add(desc);
                        return null;
                    }
                };
            }
        }, 0);
        return annotations;
    }

    private static List<String> methodCalls(byte[] clazz) {
        List<String> calls = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        calls.add((op == Opcodes.INVOKESTATIC ? "INVOKESTATIC " : "INVOKE ")
                                + owner + "." + name + desc);
                    }
                };
            }
        }, 0);
        return calls;
    }
}
