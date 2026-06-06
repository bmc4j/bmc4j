package org.bmc4j.engine

/**
 * Decides whether a method JBMC stubbed to nondet is *signal* worth surfacing.
 *
 * JBMC stubs every callee it has no body for in the reachable slice — but most of those are noise:
 * bmc4j's own bundled models, JBMC-internal synthetics (boxing, `<clinit>`/`<init>`
 * plumbing, assertion machinery, CProver intrinsics), and the like. Surfacing those would turn the
 * footnote into wallpaper. This filter keeps only the methods a user would actually want to know got
 * havoc'd: unmodeled JDK / third-party / their-own-code calls.
 *
 * The complement to keeping signal is [isUserOwned], which flags a stub from the user's own
 * analysis classpath — almost always a missing-dependency config bug, warned loudly even in lenient
 * mode.
 */
object StubFilter {

    /** Boxing/unboxing the engine inserts for autoboxing — modeled in spirit, pure, never a gap. */
    private val BOXING_VALUE_OF = setOf(
            "java.lang.Integer.valueOf", "java.lang.Long.valueOf",
            "java.lang.Short.valueOf", "java.lang.Byte.valueOf",
            "java.lang.Character.valueOf", "java.lang.Boolean.valueOf",
            "java.lang.Double.valueOf", "java.lang.Float.valueOf")

    /**
     * True if [fqn] (a `pkg.Class.method` stub) is signal worth surfacing — i.e. it is NOT
     * a bmc4j/core model, a JBMC-internal synthetic, or other known noise. The model packages are the
     * ones bmc4j ships stand-ins for and deliberately analyzes as models, so a stub there is expected,
     * not a gap. `null`/blank is never signal.
     */
    @JvmStatic
    fun isSignal(fqn: String?): Boolean {
        if (fqn.isNullOrBlank()) {
            return false
        }
        // JBMC-internal synthetics: constructors, static initializers, assertion + class-init plumbing.
        // These are never user-meaningful modeling gaps — they're how JBMC bootstraps any class.
        val method = fqn.substringAfterLast('.')
        if (method == "<init>" || method == "<clinit>" || method == "desiredAssertionStatus") {
            return false
        }
        if (fqn in BOXING_VALUE_OF) {
            return false
        }
        // Residual-invokedynamic markers are the OPPOSITE of noise: the rewrite layer plants them
        // precisely so an un-desugared indy surfaces through the stub policy instead of being
        // silently trusted (see ResidualIndyBytecode) — so they must survive the org.bmc4j filter.
        if (fqn.startsWith(ResidualIndyBytecode.MARKER_FQN_PREFIX)) {
            return true
        }
        // CProver intrinsics and bmc4j's own runtime/model plumbing are not user gaps.
        if (fqn.startsWith("org.cprover.") || fqn.startsWith("org.bmc4j.")) {
            return false
        }
        // Throwables the engine synthesizes for control flow (AssertionError, the unchecked exceptions
        // it raises for built-in checks) are not modeling gaps a user acts on.
        if (fqn.startsWith("java.lang.AssertionError") || fqn.startsWith("java.lang.Error")) {
            return false
        }
        return true
    }

    /**
     * True if [fqn] is a stub from the user's own analysis classpath — judged by whether its
     * class is on the build-supplied [userPackages] prefix list (the module under test). A stub
     * here is a config bug (a missing compile/runtime dependency), not a JDK modeling gap, so it is
     * warned loudly even in lenient mode. [userPackages] is a comma/space-separated list of
     * package prefixes (e.g. `"com.acme,com.acme.util"`); empty means "unknown" → never user-owned.
     */
    @JvmStatic
    fun isUserOwned(fqn: String?, userPackages: String?): Boolean {
        if (fqn == null || userPackages.isNullOrBlank()) {
            return false
        }
        return userPackages.split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .any { p -> fqn == p || fqn.startsWith(if (p.endsWith(".")) p else "$p.") }
    }
}
