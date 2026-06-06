package org.bmc4j.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Prepares a JBMC analysis classpath for Kotlin coroutines.
 *
 * <p>Kotlin compiles a {@code suspend} function with more than one suspension point
 * into a state machine whose {@code LocalVariableTable} has overlapping entries in
 * the parameter slot range. JBMC 6.9.0 trips an internal invariant on that
 * ({@code create_parameter_names: "should have at most one entry per index"}) and
 * aborts before it can verify anything. We sidestep it by mirroring each classpath
 * <em>directory</em> with the {@code LocalVariableTable} removed from coroutine
 * methods only — suspend functions (those with a trailing
 * {@code kotlin.coroutines.Continuation} parameter) and generated
 * {@code invokeSuspend} bodies. Line numbers and all other methods' debug info are
 * untouched, so ordinary counterexamples keep their variable names.
 *
 * <p>Both directory and jar entries are mirrored via {@code ClasspathMirror}: a published consumer's
 * coroutine classes can arrive in a jar just like its own compiled output.
 */
public final class CoroutineBytecode {

    private static final String CONTINUATION = "Lkotlin/coroutines/Continuation;";

    private CoroutineBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Strip coroutine LVTs in directory AND jar entries of {@code classpath}; memoized per classpath
     *  (computed once per worker, which also makes concurrent proofs race-free). */
    public static String strip(String classpath) {
        return CACHE.computeIfAbsent(classpath, CoroutineBytecode::doStrip);
    }

    private static String doStrip(String classpath) {
        return ClasspathMirror.mirror(classpath, "stripped",
                b -> new ClasspathMirror.Transformed(stripClass(b)));
    }

    private static byte[] stripClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
                boolean coroutine = name.equals("invokeSuspend") || desc.contains(CONTINUATION);
                if (!coroutine) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitLocalVariable(String n, String d, String s,
                                                   Label start, Label end, int index) {
                        // drop LVT/LVTT entries for coroutine methods
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
