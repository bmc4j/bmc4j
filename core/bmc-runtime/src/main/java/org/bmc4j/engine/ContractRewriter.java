package org.bmc4j.engine;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Implements the <b>replace</b> direction of method contracts at the bytecode
 * level — "Route A", proven by the spike. For a proof that should reuse a contract instead
 * of re-analyzing a method's body, it rewrites the <em>call sites</em> of contracted
 * methods to call generated stub methods (which {@code assert(requires); return
 * assume(ensures) over a nondet result}). The contracted method's real class is never
 * shadowed, so its own enforce-proof still analyzes the real body — replace and enforce
 * coexist by which classpath a given proof is handed.
 *
 * <p>The rewrite is routed through {@link ClasspathMirror} — the one fail-loud, content-hashed
 * mirroring engine every other rewrite pass uses. Both directory and jar entries are mirrored
 * (each {@code .class} call-site-rewritten, everything else copied verbatim); the redirect set and
 * the excluded caller are folded into the mirror's content hash as extra key material, so distinct
 * contract configurations over the same source never alias one mirror. A mirror failure THROWS
 * (reclassified to UNKNOWN by the engine-error handler) rather than silently analysing the real,
 * un-redirected call sites as if they were the contract proof.
 *
 * <p>v1 redirects {@code invokestatic} calls to contracted static methods. The stub has the
 * same descriptor, so the operand stack is unchanged.
 *
 * <p><b>Modular enforce.</b> A redirect set may be applied with one class <em>excluded</em>
 * as a caller: its call sites are left untouched. This is how an enforce-proof analyzes a
 * method's real body while every contracted callee (including a recursive self-call) is
 * still summarized — the proof class is excluded so its direct call to the method-under-test
 * stays real, but the method's own body (in a different class) has its contracted calls
 * redirected. That is exactly the inductive step for recursion and modular composition for
 * call chains. A replace-proof excludes nothing, so all of its contracted calls are summarized.
 */
public final class ContractRewriter {

    /** A single call-site redirect: calls to {@code owner.name(descriptor)} become
     *  {@code invokestatic stubOwner.stubName(descriptor)}. A null descriptor matches any. */
    public static final class Redirect {
        final String owner;
        final String name;
        final String descriptor;
        final String stubOwner;
        final String stubName;

        public Redirect(String owner, String name, String descriptor, String stubOwner, String stubName) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.stubOwner = stubOwner;
            this.stubName = stubName;
        }

        boolean matches(String o, String n, String d) {
            return owner.equals(o) && name.equals(n) && (descriptor == null || descriptor.equals(d));
        }

        /** Stable, fully-specified form — part of the contract mirror's cache key, so two distinct
         *  redirect sets can never alias the same mirror. */
        @Override
        public String toString() {
            return owner + "." + name + descriptor + "->" + stubOwner + "." + stubName;
        }
    }

    private ContractRewriter() {
    }

    /** Rewrite directory entries of {@code classpath}, returning the new classpath. */
    public static String rewrite(String classpath, List<Redirect> redirects) {
        return rewrite(classpath, redirects, null);
    }

    /**
     * Rewrite the call sites on {@code classpath}, leaving the call sites of
     * {@code excludeCallerInternalName} (an internal class name like {@code pkg/Proof}, or
     * {@code null} to exclude nothing) untouched — see the class doc on modular enforce.
     *
     * <p>Routed through {@link ClasspathMirror}: each entry is mirrored into a fresh dir/jar keyed by
     * a full SHA-256 of (content + this config), atomically published, and marked {@code .done} last;
     * a mirror failure throws (→ UNKNOWN) rather than silently passing the un-rewritten entry through.
     * The {@code redirects} and {@code excludeCallerInternalName} are the extra key material — the
     * source bytes are identical regardless of them, so the mirror identity must include them
     * explicitly or two distinct configurations would collide into one cached mirror.
     */
    public static String rewrite(String classpath, List<Redirect> redirects, String excludeCallerInternalName) {
        if (redirects.isEmpty()) {
            return classpath;
        }
        String extraKey = redirects + "|x=" + excludeCallerInternalName;
        return ClasspathMirror.mirror(
                classpath,
                "contracts",
                bytes -> new ClasspathMirror.Transformed(
                        rewriteClass(bytes, redirects, excludeCallerInternalName)),
                extraKey);
    }

    /** Pure transform: redirect matching {@code invokestatic} call sites. Package-private for tests. */
    static byte[] rewriteClass(byte[] bytes, List<Redirect> redirects) {
        return rewriteClass(bytes, redirects, null);
    }

    /**
     * Pure transform: redirect matching {@code invokestatic} call sites, unless the class
     * being rewritten is {@code excludeCaller} (its call sites pass through unchanged).
     * Package-private for tests.
     */
    static byte[] rewriteClass(byte[] bytes, List<Redirect> redirects, String excludeCaller) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            private boolean excluded;

            @Override
            public void visit(int v, int a, String name, String sig, String sup, String[] ifs) {
                this.excluded = excludeCaller != null && excludeCaller.equals(name);
                super.visit(v, a, name, sig, sup, ifs);
            }

            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && !excluded) {
                            for (Redirect r : redirects) {
                                if (r.matches(owner, name, desc)) {
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, r.stubOwner, r.stubName, desc, false);
                                    return;
                                }
                            }
                        }
                        super.visitMethodInsn(op, owner, name, desc, itf);
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
