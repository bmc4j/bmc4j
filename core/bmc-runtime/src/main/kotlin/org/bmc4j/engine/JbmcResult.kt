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
        /**
         * Short, human-readable cause of an UNKNOWN result; null on a non-UNKNOWN verdict. Guaranteed
         * NON-BLANK whenever [verdict] is UNKNOWN (asserted at construction): there is no bare,
         * reasonless UNKNOWN — every undecided result is self-diagnosing.
         */
        @get:JvmName("undecidedReason") val undecidedReason: String?,
        /**
         * The TYPED cause of an UNKNOWN result; null on a non-UNKNOWN verdict. Guaranteed non-null
         * whenever [verdict] is UNKNOWN (asserted at construction). Its [UnknownKind.retryable] flag
         * drives [Jbmc.exec]'s bounded one-extra-run retry, and the kind classifies the UNKNOWN in the
         * test-failure message and the proof-results comment.
         */
        @get:JvmName("undecidedKind") val undecidedKind: UnknownKind?,
        stubbedMethods: List<String>?,
        unmodelledMembers: List<String>?,
        linkFailureStubs: List<String>?,
        assumedContracts: List<String>?,
        /**
         * The per-stage PERFORMANCE BREAKDOWN of this run (phase timings, loop-unwinding offenders,
         * formula size, whether SAT was reached), or null when the run was not profiled. Purely
         * diagnostic — produced only for a proof annotated [org.bmc4j.BmcProfile] and never consulted by
         * the verdict logic. Parsed from the same verbose stream the verdict comes from (see
         * [JbmcProfile]); on a timeout it carries whatever was captured up to the kill.
         */
        @get:JvmName("profile") val profile: JbmcProfile? = null) {

    init {
        if (verdict == Verdict.UNKNOWN) {
            require(!undecidedReason.isNullOrBlank()) {
                "an UNKNOWN result must carry a non-empty undecidedReason (no bare UNKNOWN)"
            }
            require(undecidedKind != null) {
                "an UNKNOWN result must carry an UnknownKind (no kindless UNKNOWN)"
            }
        }
    }

    /** True if this UNKNOWN was caused by the per-proof wall-clock budget expiring. */
    @get:JvmName("isTimeout")
    val isTimeout: Boolean get() = undecidedKind == UnknownKind.TIMEOUT

    /** True if this UNKNOWN was caused by a non-verdict engine exit (crash/abort), not a timeout. */
    @get:JvmName("isEngineCrash")
    val isEngineCrash: Boolean get() = undecidedKind == UnknownKind.ENGINE_CRASH

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
     * The unmodelled members this run REACHED: real JDK members the model cannot model (a per-member
     * `@BmcUnmodelable` loud stub, or a `@BmcModelTail` member), given a loud body that routes through the
     * [org.bmc4j.analysis.BmcUnmodelledReached] sentinel. JBMC reports reaching one as an assertion
     * failure (a would-be REFUTED), but a model gap is bmc4j's own limitation, NOT a counterexample in
     * the user's code — so the verdict interpreter DEMOTES such a refutation to UNKNOWN, naming the
     * member(s) here. Each entry is the offending `Class.member(params)` (dot form). Empty on a normal
     * run. Like [stubbedMethods] this is a parallel FACT harvested at parse time; the demotion POLICY
     * is applied by [org.bmc4j.junit.BmcProofExtension].
     */
    @get:JvmName("unmodelledMembers")
    val unmodelledMembers: List<String> = unmodelledMembers?.toList() ?: emptyList()

    /**
     * The stubbed MEMBERS whose nondet body a REFUTED counterexample ran through this run: methods
     * JBMC had no body for in the reachable slice and replaced with an argument-ignoring nondet stub
     * (fingerprinted by `stub_ignored_arg*` assignments in the failure trace; see
     * [JbmcOutputParser.harvestLinkFailureStubMembers]). Each entry is the offending
     * `Class.method(params)` (dot form). Empty on a clean run, and only ever populated alongside a
     * refutation.
     *
     * Like [stubbedMethods] / [unmodelledMembers] this is a parallel FACT harvested at parse time. The
     * verdict interpreter [org.bmc4j.junit.BmcProofExtension] applies the POLICY: when the stub's owning
     * class is nonetheless PRESENT on the analysis classpath, the refutation is a transient engine
     * link failure (the class was there but got nondet-stubbed anyway), NOT a real counterexample, so
     * the would-be REFUTED is DEMOTED to a member-named UNKNOWN. A genuinely absent class is the
     * ordinary nondet-stub path instead.
     */
    @get:JvmName("linkFailureStubs")
    val linkFailureStubs: List<String> = linkFailureStubs?.toList() ?: emptyList()

    /**
     * The per-proof ASSUMED output-contracts (`Bmc.assumeEvery` / `Bmc.assumeStable`) this run installed
     * — each a `"Owner.method"` display (a `(stable)` suffix marks an `assumeStable`). A parallel FACT
     * harvested by [JbmcBackend], like [stubbedMethods]: the POLICY (flagging a VERIFIED as NOT
     * unconditional) is applied by [org.bmc4j.junit.BmcProofExtension]. Empty when the proof declares
     * none.
     */
    @get:JvmName("assumedContracts")
    val assumedContracts: List<String> = assumedContracts?.toList() ?: emptyList()

    @JvmOverloads
    constructor(verified: Boolean, violations: List<Violation>, rawOutput: String?,
                vacuous: Boolean = false) : this(
            if (verified) Verdict.VERIFIED else Verdict.REFUTED, violations, rawOutput, vacuous,
            null, null, emptyList(), emptyList(), emptyList(), emptyList())

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
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, undecidedKind,
                stubs, unmodelledMembers, linkFailureStubs, assumedContracts, profile)
    }

    /**
     * Return a copy carrying the assumed-output-contract list (a parallel fact, like
     * [withStubbedMethods]). The verdict and violations are unchanged — the verdict FLAG ("VERIFIED
     * under assumed contract … — NOT unconditional") is a presentation POLICY applied later by
     * [org.bmc4j.junit.BmcProofExtension]. Returns `this` when empty/unchanged.
     */
    fun withAssumedContracts(contracts: List<String>?): JbmcResult {
        if (contracts.isNullOrEmpty()) {
            return this
        }
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, undecidedKind,
                stubbedMethods, unmodelledMembers, linkFailureStubs, contracts, profile)
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
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, undecidedKind,
                stubbedMethods, members, linkFailureStubs, assumedContracts, profile)
    }

    /**
     * Return a copy carrying the link-failure-stub member list (a parallel fact, like
     * [withStubbedMethods] / [withUnmodelledMembers]). The verdict and violations are unchanged here —
     * the demotion of a present-on-classpath link failure to UNKNOWN is a POLICY applied later by
     * [org.bmc4j.junit.BmcProofExtension]. Returns `this` when empty/unchanged.
     */
    fun withLinkFailureStubs(members: List<String>?): JbmcResult {
        if (members.isNullOrEmpty()) {
            return this
        }
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, undecidedKind,
                stubbedMethods, unmodelledMembers, members, assumedContracts, profile)
    }

    /**
     * Return a copy carrying the parsed performance [profile] (a parallel diagnostic FACT, like
     * [withStubbedMethods]). The verdict and every other field are unchanged — the profile never
     * influences a verdict; it is rendered to the report by [org.bmc4j.junit.BmcProofExtension] only
     * for a `@BmcProfile`-annotated proof. Returns `this` when [profile] is null.
     */
    fun withProfile(profile: JbmcProfile?): JbmcResult {
        if (profile == null) {
            return this
        }
        return JbmcResult(verdict, violations, rawOutput, isVacuous, undecidedReason, undecidedKind,
                stubbedMethods, unmodelledMembers, linkFailureStubs, assumedContracts, profile)
    }

    companion object {

        /**
         * An UNKNOWN result of the given typed [kind]: the run was undecided — JBMC neither verified
         * nor refuted the proof. [reason] is a short, non-empty human-readable cause folded into the
         * failure message; [violations] is empty (there is no counterexample) and [isVerified] is
         * `false`. The [kind] makes the UNKNOWN classifiable and its [UnknownKind.retryable] flag
         * drives [Jbmc.exec]'s bounded retry. A null/blank [reason] is rejected at construction —
         * there is no bare UNKNOWN. Prefer [unknownTimeout] / [unknownEngineCrash] / [unknownParse]
         * at their dedicated sites.
         */
        @JvmStatic
        fun unknown(kind: UnknownKind, reason: String?, rawOutput: String?): JbmcResult =
                JbmcResult(Verdict.UNKNOWN, emptyList(), rawOutput, false, reason, kind,
                        emptyList(), emptyList(), emptyList(), emptyList())

        /**
         * An UNKNOWN result caused specifically by the per-proof wall-clock budget expiring (the
         * engine process tree was force-killed) — [UnknownKind.TIMEOUT], not retryable (the budget is
         * the budget). The typed kind — not an inferred reason string — is what `@BmcProof(expect =
         * TIMEOUT)` asserts.
         */
        @JvmStatic
        fun unknownTimeout(reason: String?, rawOutput: String?): JbmcResult =
                unknown(UnknownKind.TIMEOUT, reason, rawOutput)

        /**
         * An UNKNOWN result caused by the engine process exiting with a non-verdict code (it crashed,
         * aborted on an internal invariant, or was OOM-killed — anything but the verified/violation
         * exits) — [UnknownKind.ENGINE_CRASH], which is RETRYABLE: a crash is not a verdict, and jbmc
         * 6.9.0 has rare nondeterministic internal aborts, so [Jbmc.exec] re-runs it once.
         */
        @JvmStatic
        fun unknownEngineCrash(reason: String?, rawOutput: String?): JbmcResult =
                unknown(UnknownKind.ENGINE_CRASH, reason, rawOutput)

        /**
         * An UNKNOWN result caused by the engine exiting with a VERDICT code but emitting
         * `--json-ui` stdout bmc4j could not parse into a verdict — [UnknownKind.PARSE_FAILURE],
         * which is RETRYABLE (truncated/interleaved output is nondeterministic in practice). The
         * [reason] should already carry the bounded raw-output tail + length + empty/truncated/garbage
         * classification this kind exists to self-diagnose.
         */
        @JvmStatic
        fun unknownParse(reason: String?, rawOutput: String?): JbmcResult =
                unknown(UnknownKind.PARSE_FAILURE, reason, rawOutput)
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
