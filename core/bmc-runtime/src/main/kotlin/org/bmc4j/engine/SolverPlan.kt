package org.bmc4j.engine

import java.io.File

/**
 * Resolves which SAT solver a single proof actually runs under, and — the load-bearing part —
 * enforces the **safe-by-default** rule for the fast external SAT solver.
 *
 * ## The fast solver and the text/String hole
 * An external DIMACS SAT solver (the fast path) makes the engine run with its String reasoning OFF.
 * For a text-free numeric/boolean proof that's sound and much faster. For a proof that reasons about
 * text it is NOT sound — running with String reasoning off can report a false pass. So:
 *
 *  - the fast solver engages ONLY for a proof [StringUseClassifier] proves text-free;
 *  - a text/String-using proof that explicitly asked for the fast solver **fails loud by default**
 *    (a plain-language message: this proof uses text operations the fast solver can't verify soundly);
 *  - an opt-out flag turns that failure off, but even then the proof falls back to the **default
 *    solver** (a sound result, just no speedup) — there is NO path that runs the fast (text-reasoning-
 *    off) solver on a text proof and reports a pass.
 *
 * ## Resolution precedence (issue #24)
 * per-proof `@BmcProof(solver=…)` > project `bmc { solver = … }` (forwarded as `-Dbmc.solver`) >
 * global `bmc { externalSat = … }` / `-PsatPath` (forwarded as `-Dbmc.externalSat`). A
 * [SolverRequest.solver] carries the already-resolved per-proof-else-project name; the global
 * external-SAT property is the lowest-precedence fallback.
 *
 * ## Named-solver registry
 *  - `"kissat"` (or `"fast"`) resolves to the bundled fast solver binary via [BundledEngine.kissatPath];
 *    if no fast solver is bundled for this platform (e.g. windows-x64) it gracefully falls back to the
 *    default solver with a plain-language log, never an error;
 *  - an explicit filesystem path is used as the external SAT binary directly;
 *  - `"minisat"` / `"minisat2"` / unset / an SMT name (`z3`, `boolector`, `cvc4`, `cvc5`) means the
 *    engine's built-in / SMT path — NOT external SAT — so it is never subject to the text guard here.
 */
internal object SolverPlan {

    /** Opt-out flag: when true, an external-SAT request on a text proof does NOT fail — it silently
     *  falls back to the sound default solver (no speedup) instead. Default false (fail loud). */
    const val STRING_FALLBACK_PROP = "bmc.externalSatStringFallback"

    /**
     * EXPERT-ONLY override (off by default, never enabled silently). When true, an explicitly-requested
     * external SAT solver is used on a text proof ANYWAY — running with String reasoning off. This can
     * report an UNSOUND pass for a property that actually depends on text; it exists only for an expert
     * who has reasoned that the strings in their proof don't affect the property. Documented loudly and
     * called out in the release notes; ordinary users never touch it.
     */
    const val UNSAFE_TEXT_OVERRIDE_PROP = "bmc.externalSatUnsafeTextOverride"

    /** The names that select the bundled fast solver (resolved via [BundledEngine.kissatPath]). */
    private val BUNDLED_FAST_NAMES = setOf("kissat", "fast")

    /** Names handled by the engine's built-in / SMT path — never external SAT, so never text-guarded. */
    private val BUILTIN_NAMES = setOf("", "minisat", "minisat2", "z3", "boolector", "cvc4", "cvc5")

    /**
     * The decision for one proof: whether to pass `--external-sat-solver <path>` (and thus run with
     * String reasoning OFF), or to fail loud, or to use the default/built-in path.
     */
    sealed class Decision {
        /** Run the engine with the external SAT solver at [path] (String reasoning OFF). Used only for
         *  a proof classified text-free, or under the expert unsafe override. */
        class ExternalSat(@JvmField val path: String) : Decision()

        /** Use the engine's default / built-in / SMT path (String reasoning ON for the default solver).
         *  [refinementOff] is always false here. [note] is a plain-language line to log when the user
         *  asked for the fast solver but we soundly declined (text proof + opt-out, or no bundled fast
         *  solver on this platform); null when nothing was overridden. */
        class Builtin(@JvmField val note: String?) : Decision()

        /** Fail the proof loud: the user asked for the fast solver on a text proof and did NOT opt out.
         *  [message] is plain language (no jargon). */
        class FailLoud(@JvmField val message: String) : Decision()
    }

    /** Inputs needed to resolve a solver for one proof. */
    class SolverRequest internal constructor(
            /** Already-resolved per-proof-else-project solver name (`@BmcProof(solver)` else
             *  `bmc{solver}` / `-Dbmc.solver`); blank means "no explicit choice". */
            @JvmField val solver: String,
            /** Lowest-precedence global external-SAT path/name (`bmc{externalSat}` / `-Dbmc.externalSat`);
             *  blank when unset. */
            @JvmField val globalExternalSat: String,
            @JvmField val entryClass: String,
            @JvmField val classpath: String?)

    /**
     * Resolve [req] into a [Decision], applying the text-use guard. Pure except for reading
     * [BundledEngine.kissatPath] and the two opt-out/override system properties; never throws.
     */
    @JvmStatic
    fun resolve(req: SolverRequest): Decision {
        // 1) Determine the requested EXTERNAL-SAT target, by precedence. A built-in/SMT name short-
        //    circuits: external SAT isn't in play, so the text guard never applies.
        val explicit = req.solver.trim()
        val effectiveName: String
        if (explicit.isNotEmpty()) {
            if (BUILTIN_NAMES.contains(explicit.lowercase())) {
                return Decision.Builtin(null) // a built-in/SMT solver: handled by Jbmc.addSolver, no guard
            }
            effectiveName = explicit
        } else {
            // No per-proof/project solver: fall back to the global external-SAT property (lowest prec).
            val global = req.globalExternalSat.trim()
            if (global.isEmpty()) {
                return Decision.Builtin(null) // nothing requested: plain default solver
            }
            effectiveName = global
        }

        // 2) Resolve the requested name to an external-SAT binary PATH (or decline gracefully).
        val path = resolveExternalSatPath(effectiveName)
                ?: return Decision.Builtin(
                        "the fast solver isn't available on this platform, using the default solver")

        // 3) The text guard. If the proof is text-free, the fast solver is sound -> use it.
        val classification = StringUseClassifier.classify(req.entryClass, req.classpath)
        if (!classification.usesText) {
            return Decision.ExternalSat(path)
        }

        // 4) Text-using proof + fast solver requested. Default = FAIL LOUD; opt-out = sound fallback;
        //    expert override = run unsafe (off by default, never silent).
        if (unsafeTextOverride()) {
            return Decision.ExternalSat(path) // expert: run String-reasoning-off on a text proof (UNSOUND)
        }
        if (stringFallback()) {
            return Decision.Builtin(
                    "this proof uses text/String operations the fast solver can't verify soundly," +
                            " so it ran on the default solver instead (sound, just not faster)")
        }
        return Decision.FailLoud(
                "this proof uses text/String operations that the fast solver can't verify soundly.\n" +
                        "    The fast solver only applies to numeric/boolean proofs with no text.\n" +
                        "    To get a sound result, either:\n" +
                        "      - let this proof use the default solver (don't set solver=\"$effectiveName\"" +
                        " on it); or\n" +
                        "      - allow an automatic fall-back to the default solver for text proofs:\n" +
                        "          bmc { externalSatStringFallback = true }  (or -D$STRING_FALLBACK_PROP=true)\n" +
                        "        — those proofs then run sound on the default solver (no speedup), and the\n" +
                        "        text-free proofs still get the fast solver.")
    }

    /**
     * Map a requested external-SAT solver name to an executable path, or `null` if it can't be provided
     * on this platform (caller falls back to the default solver):
     *  - a bundled-fast name (`"kissat"`/`"fast"`) -> [BundledEngine.kissatPath] (null when not bundled);
     *  - an existing filesystem path -> itself;
     *  - anything else -> null (treated as not-available; the default solver is used).
     */
    private fun resolveExternalSatPath(name: String): String? {
        val trimmed = name.trim()
        if (BUNDLED_FAST_NAMES.contains(trimmed.lowercase())) {
            return bundledFastSolverPath()
        }
        val asFile = File(trimmed)
        if (asFile.isFile) {
            return trimmed
        }
        // An unknown bare name we can't resolve to a binary: decline (default solver), never an error.
        return null
    }

    /**
     * The bundled fast solver binary path, or `null` when it isn't available on this platform (so the
     * caller falls back to the default solver). [BundledEngine.kissatPath] only returns a path AFTER the
     * engine jar has been extracted, and that extraction normally happens later (when the backend
     * resolves jbmc) — so trigger it here, exactly as the backend would. A custom `-Dbmc.jbmc` binary
     * means no bundled engine jar (hence no bundled fast solver): decline gracefully. Fail-safe: any
     * extraction error declines to the default solver rather than throwing out of solver resolution.
     */
    private fun bundledFastSolverPath(): String? {
        if (!System.getProperty("bmc.jbmc").isNullOrBlank()) {
            return null // a custom engine binary: no bundled engine jar, so no bundled fast solver
        }
        return try {
            BundledEngine.extract() // unpacks every files.txt entry (the fast solver included), once
            BundledEngine.kissatPath() // null on a platform whose jar bundles no fast solver (e.g. windows)
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun stringFallback(): Boolean =
            java.lang.Boolean.getBoolean(STRING_FALLBACK_PROP)

    private fun unsafeTextOverride(): Boolean =
            java.lang.Boolean.getBoolean(UNSAFE_TEXT_OVERRIDE_PROP)
}
