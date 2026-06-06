package org.bmc4j.engine;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Injects the <b>vacuity reachability marker</b> into every {@code @BmcProof} method
 * (including generated enforce-proofs, which are themselves {@code @BmcProof}-annotated).
 *
 * <p>For each such method this pass <em>replaces every normal {@code return}</em> with a synthetic
 *
 * <pre>{@code throw new AssertionError("bmc4j.reachability"); }</pre>
 *
 * stamped on a {@linkplain BmcReachability#SENTINEL_LINE sentinel source line}. JBMC turns each into
 * an {@code assertion} property: a marker FAILS iff that exit is reachable under the proof's
 * assumptions. {@code JbmcOutputParser} then treats a proof as <b>vacuous</b> (assumptions
 * unsatisfiable) exactly when it has markers and <em>all</em> of them are SUCCESS (every normal exit
 * dead) — see {@link BmcReachability}.
 *
 * <p>Replacing each {@code return} (rather than inserting before it) keeps the bytecode trivially
 * well-formed: the {@code athrow} ends the block with no dead successor, so no new stack-map frame is
 * needed and the original frames are preserved. Proof methods are only ever JBMC entry points, so
 * never returning normally during analysis is harmless.
 *
 * <p>Ordering: this runs <b>last</b> in {@code JbmcBackend.prepareClasspath} (after the
 * concat/lambda/switch/config desugars and the contract rewrite) so the marker is injected into the
 * <em>final</em> proof bodies and no later pass can strip it. Like the sibling passes, both directory
 * and jar entries are mirrored via {@code ClasspathMirror}.
 */
public final class ReachabilityBytecode {

    private static final String BMC_PROOF_DESC = "Lorg/bmc4j/BmcProof;";

    private ReachabilityBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, memoized per classpath (race-free). */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, ReachabilityBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "reachability",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        // COMPUTE_MAXS only: we add a few stack slots (new/dup/ldc) but introduce no new jump targets,
        // so existing stack-map frames stay valid and we avoid the class-loading COMPUTE_FRAMES needs.
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MarkerMethodVisitor(mv);
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /**
     * Buffers a method until it knows whether it is a proof: a method visitor cannot inject after it
     * has already streamed instructions to the writer. We detect {@code @BmcProof} via the first
     * {@code visitAnnotation}, which the JVM/ASM delivers before any code — so we can decide whether to
     * rewrite {@code return} sites as we visit them.
     */
    private static final class MarkerMethodVisitor extends MethodVisitor {
        private boolean isProof;

        MarkerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (BMC_PROOF_DESC.equals(descriptor)) {
                isProof = true;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitInsn(int opcode) {
            if (isProof && isReturn(opcode)) {
                emitMarker();
                return; // replace the return with the marker throw
            }
            super.visitInsn(opcode);
        }

        private void emitMarker() {
            Label l = new Label();
            super.visitLabel(l);
            super.visitLineNumber(BmcReachability.SENTINEL_LINE, l);
            super.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
            super.visitInsn(Opcodes.DUP);
            super.visitLdcInsn(BmcReachability.MARKER_TEXT);
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError",
                    "<init>", "(Ljava/lang/Object;)V", false);
            super.visitInsn(Opcodes.ATHROW);
        }

        private static boolean isReturn(int opcode) {
            return opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN
                    || opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN;
        }
    }
}
