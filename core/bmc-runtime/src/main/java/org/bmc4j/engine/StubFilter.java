package org.bmc4j.engine;

/**
 * Decides whether a method JBMC stubbed to nondet is <em>signal</em> worth surfacing.
 *
 * <p>JBMC stubs every callee it has no body for in the reachable slice — but most of those are noise:
 * bmc4j's own bundled models, JBMC-internal synthetics (boxing, {@code <clinit>}/{@code <init>}
 * plumbing, assertion machinery, CProver intrinsics), and the like. Surfacing those would turn the
 * footnote into wallpaper. This filter keeps only the methods a user would actually want to know got
 * havoc'd: unmodeled JDK / third-party / their-own-code calls.
 *
 * <p>The complement to keeping signal is {@link #isUserOwned}, which flags a stub from the user's own
 * analysis classpath — almost always a missing-dependency config bug, warned loudly even in lenient
 * mode.
 */
public final class StubFilter {

    private StubFilter() {
    }

    /**
     * True if {@code fqn} (a {@code pkg.Class.method} stub) is signal worth surfacing — i.e. it is NOT
     * a bmc4j/core model, a JBMC-internal synthetic, or other known noise. The model packages are the
     * ones bmc4j ships stand-ins for and deliberately analyzes as models, so a stub there is expected,
     * not a gap. {@code null}/blank is never signal.
     */
    public static boolean isSignal(String fqn) {
        if (fqn == null || fqn.isBlank()) {
            return false;
        }
        // JBMC-internal synthetics: constructors, static initializers, assertion + class-init plumbing.
        // These are never user-meaningful modeling gaps — they're how JBMC bootstraps any class.
        int lastDot = fqn.lastIndexOf('.');
        String method = lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        if (method.equals("<init>") || method.equals("<clinit>")
                || method.equals("desiredAssertionStatus")) {
            return false;
        }
        // Boxing/unboxing the engine inserts for autoboxing — modeled in spirit, pure, never a gap.
        if (fqn.equals("java.lang.Integer.valueOf") || fqn.equals("java.lang.Long.valueOf")
                || fqn.equals("java.lang.Short.valueOf") || fqn.equals("java.lang.Byte.valueOf")
                || fqn.equals("java.lang.Character.valueOf") || fqn.equals("java.lang.Boolean.valueOf")
                || fqn.equals("java.lang.Double.valueOf") || fqn.equals("java.lang.Float.valueOf")) {
            return false;
        }
        // Residual-invokedynamic markers are the OPPOSITE of noise: the rewrite layer plants them
        // precisely so an un-desugared indy surfaces through the stub policy instead of being
        // silently trusted (see ResidualIndyBytecode) — so they must survive the org.bmc4j filter.
        if (fqn.startsWith(ResidualIndyBytecode.MARKER_FQN_PREFIX)) {
            return true;
        }
        // CProver intrinsics and bmc4j's own runtime/model plumbing are not user gaps.
        if (fqn.startsWith("org.cprover.") || fqn.startsWith("org.bmc4j.")) {
            return false;
        }
        // Throwables the engine synthesizes for control flow (AssertionError, the unchecked exceptions
        // it raises for built-in checks) are not modeling gaps a user acts on.
        if (fqn.startsWith("java.lang.AssertionError")
                || fqn.startsWith("java.lang.Error")) {
            return false;
        }
        return true;
    }

    /**
     * True if {@code fqn} is a stub from the user's own analysis classpath — judged by whether its
     * class is on the build-supplied {@code userPackages} prefix list (the module under test). A stub
     * here is a config bug (a missing compile/runtime dependency), not a JDK modeling gap, so it is
     * warned loudly even in lenient mode. {@code userPackages} is a comma/space-separated list of
     * package prefixes (e.g. {@code "com.acme,com.acme.util"}); empty means "unknown" → never user-owned.
     */
    public static boolean isUserOwned(String fqn, String userPackages) {
        if (fqn == null || userPackages == null || userPackages.isBlank()) {
            return false;
        }
        for (String prefix : userPackages.split("[,\\s]+")) {
            String p = prefix.trim();
            if (!p.isEmpty() && (fqn.equals(p) || fqn.startsWith(p.endsWith(".") ? p : p + "."))) {
                return true;
            }
        }
        return false;
    }
}
