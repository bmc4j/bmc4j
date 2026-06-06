package org.bmc4j.engine;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * Makes symbolic non-null object parameters usable in <b>Kotlin</b> proofs. kotlinc emits
 * {@code Intrinsics.checkNotNullParameter(p, "p")} as the prologue of every method with a
 * non-null-typed parameter; JBMC's nondet domain for a proof-method parameter includes
 * {@code null}, so the (correctly modeled) check throws and every Kotlin proof with a symbolic
 * object parameter spuriously refutes before its body runs — with a counterexample
 * ({@code p = null}) no Kotlin caller could ever construct.
 *
 * <p>kotlinc states the non-null contract through TWO mechanisms, and JBMC enforces both against
 * its nondet entry inputs, so both must be relaxed — for {@code @BmcProof}-annotated methods ONLY:
 * <ol>
 *   <li>the {@code Intrinsics.checkNotNullParameter} prologue call → redirected to
 *       {@link BmcKotlin#assumeNotNullParameter}, i.e. {@code assume(p != null)};</li>
 *   <li>the {@code @org.jetbrains.annotations.NotNull} parameter annotation, which JBMC asserts
 *       on the entry function's inputs ("Not null annotation check") → stripped from the proof
 *       method's parameters.</li>
 * </ol>
 * The proof then ranges over the inputs the Kotlin type system admits — the same trust move the
 * author already made by writing the non-null type.
 *
 * <ul>
 *   <li><b>Interior calls keep throwing semantics.</b> Only the proof method's own prologue is
 *       rewritten; a null flowing into a non-null parameter <em>inside</em> the analyzed code is a
 *       real, refutable bug and stays one. (The intrinsic is only ever emitted as a parameter
 *       prologue, so matching the call inside an annotated method is exact.)</li>
 *   <li><b>Nullable parameters are untouched</b> — kotlinc emits no check for {@code p: T?}, so
 *       their domain keeps {@code null}.</li>
 *   <li><b>Honest-JVM mode stays available</b>: {@code -Dbmc.kotlinNullableParams=true} (or the
 *       {@code bmc { kotlinNullableParams = true }} DSL) skips this pass, restoring the throwing
 *       prologue for proofs that deliberately model hostile Java callers. The flag is folded into
 *       the verdict-cache key (see {@code VerdictCache.computeKey}) so flipping it re-judges.</li>
 *   <li><b>Java proofs are unaffected</b> — no Kotlin prologue, nothing matches.</li>
 * </ul>
 *
 * Mirrors the {@link MathBytecode} pattern: identical descriptor, one-instruction owner swap,
 * directory and jar entries mirrored via {@link ClasspathMirror}.
 */
public final class KotlinParamBytecode {

    private static final String INTRINSICS = "kotlin/jvm/internal/Intrinsics";
    private static final String BMC_KOTLIN = "org/bmc4j/engine/BmcKotlin";
    private static final String DESC = "(Ljava/lang/Object;Ljava/lang/String;)V";
    private static final String BMC_PROOF = "Lorg/bmc4j/BmcProof;";
    private static final String NOT_NULL = "Lorg/jetbrains/annotations/NotNull;";

    /** Current + legacy (pre-1.4 kotlinc) names of the parameter-check intrinsic. */
    private static final Set<String> PARAM_CHECKS =
            Set.of("checkNotNullParameter", "checkParameterIsNotNull");

    private KotlinParamBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, returning the new classpath. Memoized
     *  per (flag, classpath) — the honest-JVM flag changes the output, so it is part of the key. */
    public static String rewrite(String classpath) {
        boolean honestJvm = Boolean.getBoolean("bmc.kotlinNullableParams");
        if (honestJvm) {
            return classpath; // honest-JVM mode: keep the throwing prologue, untouched.
        }
        return CACHE.computeIfAbsent(classpath, KotlinParamBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "kotlinparam",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    /** Pure transform: inside {@code @BmcProof} methods, swap the parameter-check intrinsic's owner
     *  to {@link BmcKotlin}. Package-private for tests. */
    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean isProof;

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        // Method annotations are visited before parameter annotations and before
                        // any instruction, so the flag is set in time for both rewrites below.
                        if (BMC_PROOF.equals(desc)) {
                            isProof = true;
                        }
                        return super.visitAnnotation(desc, visible);
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                        // JBMC asserts @NotNull on the entry function's nondet inputs ("Not null
                        // annotation check") — drop it from the proof's parameters so the relaxed
                        // prologue isn't re-imposed by the annotation. @Nullable is untouched.
                        if (isProof && NOT_NULL.equals(desc)) {
                            return null;
                        }
                        return super.visitParameterAnnotation(parameter, desc, visible);
                    }

                    @Override
                    public void visitMethodInsn(int op, String mOwner, String name, String desc, boolean itf) {
                        if (isProof && op == Opcodes.INVOKESTATIC && INTRINSICS.equals(mOwner)
                                && DESC.equals(desc) && PARAM_CHECKS.contains(name)) {
                            // Identical descriptor -> operand stack unchanged; swap the owner (and
                            // normalize the legacy name) only.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_KOTLIN,
                                    "assumeNotNullParameter", DESC, false);
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
