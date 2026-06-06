package org.bmc4j.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Surfaces every {@code invokedynamic} the desugar passes left behind, instead of silently
 * trusting it.
 *
 * <p>{@code invokedynamic} is the analysis's one fault line: JBMC links an indy call site to an
 * <em>unconstrained</em> result — and unlike a bodiless method, it does so without emitting any
 * opaque-symbol message, so a residual site is invisible to the nondet-stub policy: no footnote in
 * lenient mode, no {@code UNKNOWN} under {@code strictStubs}. The earlier passes desugar the common
 * bootstraps (string concat, record {@code equals}/{@code hashCode}/{@code toString}, lambdas /
 * method references, pattern {@code typeSwitch}), but what they deliberately leave — {@code
 * enumSwitch}, a {@code typeSwitch} label shape we don't soundly handle, a record {@code toString}
 * with a reference component, or any bootstrap a future compiler invents — was silently trusted:
 * a possible silent green.
 *
 * <p>This pass runs AFTER every indy desugarer and replaces each remaining {@code invokedynamic}
 * with an {@code invokestatic} to {@link #MARKER_CLASS} — a real class on the analysis classpath
 * that deliberately declares <b>no methods</b> (a missing METHOD on an existing class takes the
 * engine's standard nondet-stub path; a missing CLASS would add an unknown-class throw edge and
 * spuriously refute proofs that merely reach a residual site). The call has the indy's exact
 * descriptor, so it is a stack-compatible drop-in with the <em>same semantics JBMC already gave
 * the indy</em> (nondet result), but now the engine reports its standard no-body opaque symbol for
 * it, and the existing stub machinery takes over: harvested into the verdict-cache entry,
 * footnoted in lenient mode, {@code UNKNOWN} under {@code -Dbmc.strictStubs=true}, acknowledgeable
 * via {@code allowStubs}. The marker's method name carries the evidence —
 * {@code <indyName>__<bootstrapOwner>}, e.g. {@code enumSwitch__SwitchBootstraps} — so the
 * footnote names what was left un-desugared. ({@code StubFilter} exempts the marker from its
 * {@code org.bmc4j.*} noise filter.)
 *
 * <p>Soundness direction: verdicts are unchanged (nondet before, nondet now); only the
 * <em>visibility</em> changes — the fault line goes from "silently trusted" to "visibly undecided",
 * the same trust channel every other havoc'd callee already uses.
 */
public final class ResidualIndyBytecode {

    /**
     * The marker owner: {@link org.bmc4j.analysis.ResidualInvokedynamic}, a real class that
     * deliberately declares no methods — JBMC finding the class but no body for the method is the
     * entire mechanism (see the class's javadoc for why the class itself must exist).
     */
    static final String MARKER_CLASS = "org/bmc4j/analysis/ResidualInvokedynamic";

    /** The harvested-stub FQN prefix of marker methods (dot form), for the policy layers. */
    public static final String MARKER_FQN_PREFIX = MARKER_CLASS.replace('/', '.') + ".";

    private ResidualIndyBytecode() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Rewrite directory AND jar entries of {@code classpath}, memoized per classpath (race-free). */
    public static String rewrite(String classpath) {
        return CACHE.computeIfAbsent(classpath, ResidualIndyBytecode::doRewrite);
    }

    private static String doRewrite(String classpath) {
        return ClasspathMirror.mirror(classpath, "residual-indy",
                b -> new ClasspathMirror.Transformed(rewriteClass(b)));
    }

    /** Visible for unit tests: replace every remaining indy in one class with a marker call. */
    static byte[] rewriteClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                        // The indy's descriptor IS the call's stack contract (dynamic args -> return),
                        // so an invokestatic with the same descriptor is a drop-in replacement.
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, MARKER_CLASS,
                                markerMethodName(name, bsm), desc, false);
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /**
     * The marker method name: {@code <indyName>__<bootstrapOwnerSimpleName>}, sanitized to a valid
     * identifier. Both halves matter in the footnote: the indy name says which call was left
     * (e.g. {@code enumSwitch}, {@code toString}), the bootstrap owner says whose machinery it was
     * (e.g. {@code SwitchBootstraps}, {@code ObjectMethods}).
     */
    static String markerMethodName(String indyName, Handle bsm) {
        String owner = bsm == null ? "unknown" : bsm.getOwner();
        int slash = owner.lastIndexOf('/');
        String simple = slash >= 0 ? owner.substring(slash + 1) : owner;
        return sanitize(indyName) + "__" + sanitize(simple);
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return sb.length() == 0 ? "_" : sb.toString();
    }
}
