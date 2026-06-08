package org.bmc4j.analysis

import org.bmc4j.engine.ReachableCone

/**
 * Public facade over the internal [reachable-cone walk][ReachableCone], exposing just the set of
 * classes a proof entry transitively reaches. Used by the package-grain completeness ratchet (the audit
 * gate): every class the proof cone reaches must be either MODELED or under a declared out-of-scope
 * package, else a newly-reached undeclared package is a build failure — a deliberate decision point, not
 * a silently-tolerated UNKNOWN.
 *
 * This reuses the SAME reachability the verdict cache and model slicing already compute (it is a sound
 * over-approximation of a proof's dependencies), rather than introducing a second walk. A reachability
 * the walk cannot bound (reflection / an un-attributable invokedynamic / a missing entry class) returns
 * the empty set here — the conservative whole-classpath fallback carries no enumerable cone — so the
 * ratchet treats "could not bound" as "no new evidence", never as a spurious failure.
 */
object PackageReach {

    /**
     * The internal class names (dot form, e.g. `java.sql.Date`) transitively reachable from
     * [entryClass] over [classpath]. Empty when the cone could not be bounded (the conservative
     * whole-classpath signal) or the entry isn't resolvable. Never throws.
     */
    /** The system property the Gradle plugin forwards the declared out-of-scope package globs through. */
    private const val NOT_MODELED_PACKAGES_PROP = "bmc.notModeledPackages"

    /**
     * The declared deliberately-out-of-scope package globs (`bmc { notModeledPackages { … } }`,
     * forwarded as `-Dbmc.notModeledPackages`, comma-separated). Empty when none are declared.
     */
    @JvmStatic
    fun declaredGlobs(): List<String> =
            System.getProperty(NOT_MODELED_PACKAGES_PROP, "").split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

    @JvmStatic
    fun reachedClasses(entryClass: String, classpath: String?): Set<String> =
            ReachableCone.coneClasses(entryClass, classpath)
                    .mapTo(LinkedHashSet()) { it.replace('/', '.') }

    /**
     * True if [fqn] (a dotted class name `pkg.Class`, or a `pkg.Class.method` member name) is under one
     * of the declared out-of-scope package [globs]. The single source of truth for the package-waiver
     * glob match, shared by the verdict-side out-of-scope demotion and the audit-gate completeness
     * ratchet.
     *
     * **Glob semantics — RECURSIVE.** A glob covers the named package AND all subpackages: `java.nio.*`
     * (or the bare `java.nio`) matches `java.nio.ByteBuffer.get` AND `java.nio.file.Path.resolve` — a
     * subpackage of an out-of-scope area is itself out of scope. A trailing `.*` / `*` is normalized to
     * the dotted prefix; the dotted boundary prevents `java.sql` from spuriously matching `java.sqlx`.
     */
    @JvmStatic
    fun matchesPackage(fqn: String, globs: List<String>): Boolean {
        for (raw in globs) {
            var p = raw.trim()
            if (p.endsWith(".*")) {
                p = p.substring(0, p.length - 2)
            } else if (p.endsWith("*")) {
                p = p.substring(0, p.length - 1).trimEnd('.')
            }
            if (p.isEmpty()) {
                continue
            }
            if (fqn == p || fqn.startsWith("$p.")) {
                return true
            }
        }
        return false
    }
}
