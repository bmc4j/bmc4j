package org.bmc4j.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * Redirects the integer-valued {@code java.lang.Math} call sites that JBMC's bundled
 * {@code core-models.jar} does NOT model — {@code floorDiv}, {@code floorMod}, the {@code *Exact}
 * family, {@code toIntExact}, {@code absExact} and {@code abs(int/long)} — to the sound
 * {@link BmcMath} reimplementations. JBMC stubs those intrinsics to an unconstrained (nondet)
 * result, so a proof touching them is silently unsound (e.g. {@code Math.floorDiv(-7, 3) == -3}
 * spuriously refutes; this routed several {@code java.time}/{@code Period} proofs to the
 * differential axis). The methods JBMC <em>does</em> model soundly ({@code sqrt}/{@code pow}/{@code
 * sin}/...) are left untouched — this is a targeted redirect, NOT a wholesale {@code Math} shadow,
 * so JBMC's real floating-point math models are preserved.
 *
 * <p>Mirrors the {@link StringBytecode} pattern: every redirected {@code Math.*} signature has a
 * {@link BmcMath} method with the <em>identical</em> descriptor, so the rewrite is a one-instruction
 * owner swap ({@code java/lang/Math} → {@code org/bmc4j/engine/BmcMath}) with the operand stack
 * unchanged. Like the other passes, both directory and jar entries are mirrored (with sites
 * rewritten) via {@code ClasspathMirror}.
 */
public final class MathBytecode {

    private static final String MATH = "java/lang/Math";
    private static final String BMC_MATH = "org/bmc4j/engine/BmcMath";

    /** {@code "name desc"} of every {@code Math} static method we redirect to {@link BmcMath}. Each
     *  one is reimplemented soundly in {@link BmcMath} with the exact same descriptor. Methods JBMC
     *  already models soundly (sqrt/pow/trig/etc.) are deliberately absent so they pass through. */
    private static final Set<String> REDIRECTS = Set.of(
            // floorDiv / floorMod (all JDK overloads)
            "floorDiv (II)I",
            "floorDiv (JJ)J",
            "floorDiv (JI)J",
            "floorMod (II)I",
            "floorMod (JJ)J",
            "floorMod (JI)I",
            // addExact / subtractExact / multiplyExact
            "addExact (II)I",
            "addExact (JJ)J",
            "subtractExact (II)I",
            "subtractExact (JJ)J",
            "multiplyExact (II)I",
            "multiplyExact (JJ)J",
            "multiplyExact (JI)J",
            // negateExact / incrementExact / decrementExact
            "negateExact (I)I",
            "negateExact (J)J",
            "incrementExact (I)I",
            "incrementExact (J)J",
            "decrementExact (I)I",
            "decrementExact (J)J",
            // toIntExact / absExact
            "toIntExact (J)I",
            "absExact (I)I",
            "absExact (J)J",
            // abs(int/long) — JBMC's stub returns nondet for the integer overloads
            "abs (I)I",
            "abs (J)J");

    private MathBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, returning the new classpath. Memoized
     *  per classpath — computed once per worker, which also makes concurrent proofs race-free. */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, MathBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "math",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    /** Pure transform: redirect the unmodeled {@code Math.*} static call sites to {@link BmcMath}.
     *  Package-private for tests. */
    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int op, String mOwner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && MATH.equals(mOwner)
                                && REDIRECTS.contains(name + " " + desc)) {
                            // Identical descriptor -> operand stack unchanged; swap the owner only.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_MATH, name, desc, false);
                        } else {
                            super.visitMethodInsn(op, mOwner, name, desc, itf);
                        }
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
