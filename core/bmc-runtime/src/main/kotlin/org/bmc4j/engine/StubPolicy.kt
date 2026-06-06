package org.bmc4j.engine

/**
 * Turns the harvested nondet-stub *fact* into a *policy* decision: given the
 * stubs a proof reached, the acknowledged-stub allowlist, the user's own package prefixes, and whether
 * strict mode is on, decide what to surface.
 *
 * This is deliberately a pure function of (fact, policy) so it can run identically on a fresh engine
 * result and on a cache hit — flipping `strictStubs` or editing `allowStubs` re-judges the
 * *stored* stub list without re-running the engine.
 *
 * Separation of concerns:
 * - **acknowledged** stubs (matched by an `allowStubs` pattern) are silent;
 * - **user-owned** unacknowledged stubs ([StubFilter.isUserOwned]) are a config bug —
 *   warned loudly even in lenient mode, and they force UNKNOWN in strict mode;
 * - **other** unacknowledged stubs (unmodeled JDK / third-party) get the default footnote, and
 *   force UNKNOWN in strict mode.
 */
class StubPolicy private constructor(
        /** Stubs that are NOT acknowledged by the allowlist — the ones a footnote/strict mode acts on. */
        @get:JvmName("unacknowledged") val unacknowledged: List<String>,
        /** The unacknowledged stubs from the user's own packages — the loud config-bug subset. */
        @get:JvmName("userOwned") val userOwned: List<String>) {

    /** True if there's anything unacknowledged to warn about (footnote in lenient, UNKNOWN in strict). */
    fun hasUnacknowledged(): Boolean = unacknowledged.isNotEmpty()

    /** True if a stub from the user's own classpath was reached — a likely missing-dependency config bug. */
    fun hasUserOwned(): Boolean = userOwned.isNotEmpty()

    companion object {

        /**
         * Judge [stubbed] against the [allowStubs] patterns and [userPackages]. A pattern
         * is a fully-qualified method name with an optional trailing `.*` or `*` wildcard, e.g.
         * `"java.util.Formatter.format"`, `"java.util.Formatter.*"`, or `"java.util.*"`.
         */
        @JvmStatic
        fun judge(stubbed: List<String?>, allowStubs: List<String?>?, userPackages: String?): StubPolicy {
            val unack = mutableListOf<String>()
            val user = mutableListOf<String>()
            val seen = LinkedHashSet<String>()
            for (fqn in stubbed) {
                if (fqn == null || !seen.add(fqn) || matchesAny(fqn, allowStubs)) {
                    continue
                }
                unack.add(fqn)
                if (StubFilter.isUserOwned(fqn, userPackages)) {
                    user.add(fqn)
                }
            }
            return StubPolicy(unack, user)
        }

        /**
         * True if [fqn] matches one of [patterns]. Each pattern is an exact method FQN, or ends
         * in `.*` / `*` to match a package or class prefix. `"a.b.C.*"` matches every method
         * of `a.b.C`; `"a.b.*"` matches everything under `a.b`; `"a.b.C.m"` is exact.
         */
        internal fun matchesAny(fqn: String, patterns: List<String?>?): Boolean {
            if (patterns == null) {
                return false
            }
            for (raw in patterns) {
                val p = raw?.trim() ?: continue
                if (p.isEmpty()) {
                    continue
                }
                when {
                    p.endsWith(".*") -> {
                        // Keep the dot: "a.b.C." — a prefix match on the dotted boundary.
                        if (fqn.startsWith(p.substring(0, p.length - 1))) {
                            return true
                        }
                    }
                    p.endsWith("*") -> {
                        if (fqn.startsWith(p.substring(0, p.length - 1))) {
                            return true
                        }
                    }
                    fqn == p -> return true
                }
            }
            return false
        }
    }
}
