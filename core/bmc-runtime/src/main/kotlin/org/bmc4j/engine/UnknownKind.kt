package org.bmc4j.engine

/**
 * The TYPED cause of a [Verdict.UNKNOWN][JbmcResult.Verdict.UNKNOWN] result. Every UNKNOWN bmc4j
 * produces carries exactly one of these (plus a non-empty human message), so an undecided proof is
 * classifiable after the fact — a transient flake can be told apart from a genuine model gap
 * mechanically, on a PR gate or in the proof-results comment, instead of from free text.
 *
 * Each kind declares whether it is [retryable]. A retryable kind is one where re-running the engine
 * in-process ONCE can plausibly recover a real verdict — the three known nondeterministic flake
 * shapes (a non-verdict engine abort, unparseable output, a transient link-failure stub). A
 * non-retryable kind is DETERMINISTIC: the same inputs reproduce the same undecided result, so a
 * re-run pays the same cost for the same answer and is skipped. The flag DRIVES the bounded
 * one-extra-run retry in [Jbmc.exec]; the retry is sound because a recurring retryable kind stays
 * UNKNOWN (it never masks a real model hole into VERIFIED), and an engine-infrastructure UNKNOWN of
 * any kind still fails `@BmcProof(expect = UNKNOWN)`.
 */
enum class UnknownKind(
        /** True iff re-running the engine once in-process can plausibly turn this into a real
         *  verdict (a transient/nondeterministic shape); false for a deterministic cause where a
         *  re-run is wasted effort. See the class doc for how this drives [Jbmc.exec]'s retry. */
        @JvmField val retryable: Boolean) {

    /** Non-verdict engine exit (crash / internal-invariant abort / OOM-kill). jbmc 6.9.0 has rare
     *  NONDETERMINISTIC internal aborts, so a re-run can recover a real verdict. */
    ENGINE_CRASH(true),

    /** The engine exited with a verdict code but its `--json-ui` stdout was unparseable (truncated
     *  JSON, interleaved stderr bleed, an OOM-kill mid-write). Nondeterministic in practice, so a
     *  re-run can recover a clean parse — carries a bounded raw-output tail for self-diagnosis. */
    PARSE_FAILURE(true),

    /** A refutation that ran through a nondet stub of a method whose owning class IS on the analysis
     *  classpath — a transient engine LINK FAILURE (the body was there but got havoc'd anyway), not a
     *  real counterexample. Self-clears on a re-run. */
    LINK_FAILURE_STUB(true),

    /** The per-proof wall-clock budget expired and the process tree was force-killed. Deterministic
     *  under the budget: a re-run pays the same wall-clock for the same expiry. */
    TIMEOUT(false),

    /** The proof reached a member whose class is genuinely absent from the models — bmc4j
     *  deliberately does not model it. Deterministic and actionable: model it. */
    UNMODELLED_MEMBER(false),

    /** An `--unwinding-assertions` firing: the loop/recursion bound is too small to cover the proof,
     *  so exploration was truncated. Deterministic — raise the bound. */
    UNWINDING_ASSERTION(false),

    /** The engine produced output but no trustworthy verdict signal could be extracted (the solver
     *  returned undecided / the injected reachability markers are absent). Deterministic. */
    SOLVER_GAVE_UP(false),

    /** A classpath mirror / bytecode-rewrite preparation failure — a deterministic config/code issue
     *  in bmc4j's analysis-input preparation, not a property of the user's code. */
    MIRROR_FAILURE(false),

    /** A contracted target's body is not provably pure: the contract would silently drop a
     *  caller-observable side effect. A deterministic contract-CONFIGURATION error. */
    PURITY_AUDIT(false);

    companion object {
        /** Parse a kind from its [name], or null if [s] names none (fail-open for the summary/CI side). */
        @JvmStatic
        fun fromNameOrNull(s: String?): UnknownKind? =
                if (s.isNullOrBlank()) null else entries.firstOrNull { it.name == s.trim() }
    }
}
