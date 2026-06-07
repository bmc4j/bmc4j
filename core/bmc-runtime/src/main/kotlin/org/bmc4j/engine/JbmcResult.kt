package org.bmc4j.engine

/** Parsed outcome of a JBMC run. (`@get:JvmName` keeps the original record-style accessor
 *  names — `verdict()`, `violations()`, … — for the Java call sites.) */
class JbmcResult private constructor(
        /**
         * The three-way verdict of a proof run. A proof is either `VERIFIED` (holds for
         * all inputs in the bound), `REFUTED` (a counterexample exists), or `UNKNOWN`
         * (undecided within budget — timeout, engine gave up / crashed, or unparseable output).
         * VACUOUS is carried as a flavour of `REFUTED` via [isVacuous] so existing
         * callers are unaffected.
         */
        @get:JvmName("verdict") val verdict: Verdict,
        @get:JvmName("violations") val violations: List<Violation>,
        @get:JvmName("rawOutput") val rawOutput: String?,
        /**
         * True if the proof failed because its assumptions are *unsatisfiable* — every normal exit
         * was unreachable, so it verified over an empty input domain and checked nothing. When
         * set, [isVerified] is `false` and [violations] carries the dedicated
         * vacuity message ([BmcReachability.VACUOUS_MESSAGE]).
         */
        val isVacuous: Boolean,
        /** Short cause of an UNKNOWN result (null otherwise). */
        @get:JvmName("undecidedReason") val undecidedReason: String?,
        /** True if this UNKNOWN was caused by the per-proof wall-clock budget expiring. */
        val isTimeout: Boolean,
        /** True if this UNKNOWN was caused by a non-verdict engine exit (crash/abort), not a timeout. */
        val isEngineCrash: Boolean,
        stubbedMethods: List<String>?,
        unmodelledMembers: List<String>?) {

    enum class Verdict {
        VERIFIED,
        REFUTED,
        UNKNOWN
    }

    /**
     * Methods JBMC analyzed as *nondet stubs* in this run: callees it had no body
     * for in the reachable slice and therefore replaced with an unconstrained nondet result. Harvested
     * from the engine's "opaque symbol" messages and filtered to *signal* — bmc4j/core models and
     * JBMC-internal synthetics are dropped (see [StubFilter]). This is the raw *fact*; the
     * *policy* (footnote / acknowledge / strict-UNKNOWN) is applied later, so it is harvested on
     * every run regardless of verdict. Each entry is a fully-qualified `pkg.Class.method` name.
     */
    @get:JvmName("stubbedMethods")
    val stubbedMethods: List<String> = stubbedMethods?.toList() ?: emptyList()

    /**
     * The unmodelled members this run REACHED: real JDK members the model deliberately does not
     * implement (a per-member `@BmcNotModelled` / `@BmcNotNeeded` stub, or a `@BmcModelTail` member),
     * given a build-synthesized loud body that routes through the
     * [org.bmc4j.analysis.BmcUnmodelledReached] sentinel. JBMC reports reaching one as an assertion
     * failure (a would-be REFUTED), but a model gap is bmc4j's own limitation, NOT a counterexample in
     * the user's code — so the verdict interpreter DEMOTES such a refutation to UNKNOWN, naming the
     * member(s) here. Each entry is the offending `Class.member(params)` (dot form). Empty on a normal
     * run. Like [stubbedMethods] this is a parallel FACT harvested at parse time; the demotion POLICY
     * is applied by [org.bmc4j.junit.BmcProofExtension].
     */
    @get:JvmName("unmodelledMembers")
    val unmodelledMembers: List<String> = unmodelledMembers?.toList() ?: emptyList()

    @JvmOverloads
    constructor(verified: Boolean, violations: List<Violation>, rawOutput: String?,
                vacuous: Boolean = false) : this(
            if (verified) Verdict.VERIFIED else Verdict.REFUTED, violations, rawOutput, vacuous,
            null, false, false, emptyList(), emptyList())

    /** True if JBMC found no property violation within the bound. */
    val isVerified: Boolean
        get() = verdict == Verdict.VERIFIED

    /** True if the run was undecided within budget (timeout / engine gave up / unparseable). */
    val isUnknown: Boolean
        get() = verdict == Verdict.UNKNOWN

    /**
     * Return a copy of this result carrying the harvested nondet-stub list. The verdict,
     * violations, and other fields are unchanged — stubs are a parallel *fact* attached after the
     * verdict is computed, judged later by policy. Returns `this` when the list is empty/unchanged.
     */
    fun withStubbedMethods(stubs: List<String>?): JbmcResult {
        if (stubs.isNullOrEmpty()) {
            return this
        }
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, isTimeout,
                isEngineCrash, stubs, unmodelledMembers)
    }

    /**
     * Return a copy carrying the reached-unmodelled-member list (a parallel fact, like
     * [withStubbedMethods]). The verdict and violations are unchanged here — the demotion to UNKNOWN
     * is a POLICY applied later by [org.bmc4j.junit.BmcProofExtension]. Returns `this` when empty.
     */
    fun withUnmodelledMembers(members: List<String>?): JbmcResult {
        if (members.isNullOrEmpty()) {
            return this
        }
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, isTimeout,
                isEngineCrash, stubbedMethods, members)
    }

    companion object {

        /**
         * An UNKNOWN result: the run was undecided within budget — JBMC neither verified nor
         * refuted the proof. [reason] is a short human-readable cause (e.g. `"engine exited 6"`,
         * unparseable output) folded into the failure message; [violations] is empty
         * (there is no counterexample) and [isVerified] is `false`. For a wall-clock
         * expiry use [unknownTimeout] instead, so the structured fact survives — the
         * expected-verdict assertion distinguishes TIMEOUT from other unknowns.
         */
        @JvmStatic
        fun unknown(reason: String?, rawOutput: String?): JbmcResult =
                JbmcResult(Verdict.UNKNOWN, emptyList(), rawOutput, false, reason, false, false,
                        emptyList(), emptyList())

        /**
         * An UNKNOWN result caused specifically by the per-proof wall-clock budget expiring (the
         * engine process tree was force-killed). Structurally flagged — not inferred from the reason
         * string — so `@BmcProof(expect = TIMEOUT)` can assert this exact outcome.
         */
        @JvmStatic
        fun unknownTimeout(reason: String?, rawOutput: String?): JbmcResult =
                JbmcResult(Verdict.UNKNOWN, emptyList(), rawOutput, false, reason, true, false,
                        emptyList(), emptyList())

        /**
         * An UNKNOWN result caused by the engine process exiting with a non-verdict code (it crashed,
         * aborted on an internal invariant, or was OOM-killed — anything but the verified/violation
         * exits). Structurally flagged — not inferred from the reason string — so [Jbmc.exec] can
         * retry a crash exactly once: a crash is not a verdict, and jbmc 6.9.0 has rare
         * nondeterministic internal aborts.
         */
        @JvmStatic
        fun unknownEngineCrash(reason: String?, rawOutput: String?): JbmcResult =
                JbmcResult(Verdict.UNKNOWN, emptyList(), rawOutput, false, reason, false, true,
                        emptyList(), emptyList())
    }

    /** A single refuted property, with enough detail to build a stack trace. */
    class Violation @JvmOverloads constructor(
            @get:JvmName("description") val description: String?,
            @get:JvmName("file") val file: String?,
            @get:JvmName("line") val line: Int,
            /** Synthesized stack trace, innermost (violation site) first. */
            @get:JvmName("stack") val stack: List<StackTraceElement>,
            /** Human-readable input assignments that trigger the violation, e.g. `score = 100`. */
            @get:JvmName("counterexample") val counterexample: List<String>,
            /**
             * Structured form of the counterexample inputs: each proof-local symbolic input
             * with its JBMC value `kind` and raw `data`, used to render a replayable concrete
             * test. Empty when there are no reconstructible bindings. Parallel to [counterexample]
             * (which is the human-readable `name = value` display form).
             */
            @get:JvmName("bindings") val bindings: List<Binding> = emptyList())

    /**
     * One structured counterexample input: a proof-local symbolic variable, its JBMC
     * value `kind` (e.g. `"integer"`, `"boolean"`), and the raw `data` string
     * JBMC assigned it. The replay renderer maps this back to concrete Java.
     */
    class Binding(
            /** The proof-local variable name (e.g. `"score"`). */
            @get:JvmName("name") val name: String,
            /** The JBMC value kind (e.g. `"integer"`, `"boolean"`, `"float"`). */
            @get:JvmName("kind") val kind: String?,
            /** The raw JBMC value data (e.g. `"100"`, `"true"`). */
            @get:JvmName("data") val data: String?)
}
