package org.bmc4j.junit

import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.engine.BmcReachability
import org.bmc4j.engine.BmcRequest
import org.bmc4j.engine.BmcUndecidedError
import org.bmc4j.engine.BmcVerificationError
import org.bmc4j.engine.JbmcConcurrency
import org.bmc4j.engine.JbmcResult
import org.bmc4j.engine.ModelManifest
import org.bmc4j.engine.ModelPolicy
import org.bmc4j.engine.ReplayRenderer
import org.bmc4j.engine.ReplayTestWriter
import org.bmc4j.engine.ResidualIndyBytecode
import org.bmc4j.engine.StubPolicy
import org.bmc4j.engine.UnknownKind
import org.bmc4j.engine.UserModel
import org.bmc4j.engine.VerdictCache
import org.bmc4j.engine.VerificationBackends
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

/**
 * Runs a [BmcProof] method through a model-checking `VerificationBackend`
 * instead of executing its body. The extension is engine-agnostic: it builds a
 * `BmcRequest` from the proof and the test JVM's classpath
 * (`java.class.path`) and hands it to the selected backend (default JBMC;
 * `-Dbmc.backend=esbmc` to switch). On a violation, the proof fails with a
 * [BmcVerificationError] carrying a synthesized stack trace.
 */
class BmcProofExtension : InvocationInterceptor, ParameterResolver {

    // --- Symbolic parameters --------------------------------------------------
    // A @BmcProof method may declare parameters; JBMC treats the entry function's
    // parameters as nondeterministic inputs (objects, strings, arrays included).
    // We never actually execute the body, so the values returned here are unused
    // placeholders that only satisfy JUnit's invocation machinery.

    override fun supportsParameter(parameterContext: ParameterContext,
                                   extensionContext: ExtensionContext): Boolean {
        // Defer JUnit's own injected types (TestInfo, etc.) to their resolvers.
        val typeName = parameterContext.parameter.type.name
        return !typeName.startsWith("org.junit.")
    }

    override fun resolveParameter(parameterContext: ParameterContext,
                                  extensionContext: ExtensionContext): Any? {
        return when (parameterContext.parameter.type) {
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Boolean.TYPE -> false
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0.0f
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            Character.TYPE -> '\u0000'
            else -> null // reference types: placeholder; JBMC supplies the real nondet value
        }
    }

    @Throws(Throwable::class)
    override fun interceptTestMethod(invocation: InvocationInterceptor.Invocation<Void>,
                                     invocationContext: ReflectiveInvocationContext<Method>,
                                     extensionContext: ExtensionContext) {
        // We verify the method rather than execute it.
        invocation.skip()

        val method = invocationContext.executable
        val entryClass = method.declaringClass.name
        val entryFunction = entryClass + "." + method.name
        val config: BmcProof? = method.getAnnotation(BmcProof::class.java)
        val expectedV = config?.expect ?: Verdict.VERIFIED

        // Time + emit ONE machine-readable summary record per proof, regardless of how it exits
        // (cache hit, live pass, or a thrown failure). The summary sink is a no-op unless
        // -Dbmc.summaryDir is set (CI), so local/IDE runs are unaffected. runProof reports its
        // PASS outcome via [outcome]; on a failure it throws a BmcVerificationError whose message
        // IS the human-readable counterexample/issue, captured here for the record. Emission is
        // fail-open and never alters the verdict.
        if (!ProofSummary.enabled) {
            runProof(method, entryClass, entryFunction, config, ProofOutcome())
            return
        }
        val outcome = ProofOutcome()
        val started = System.nanoTime()
        try {
            runProof(method, entryClass, entryFunction, config, outcome)
        } catch (t: Throwable) {
            val ms = (System.nanoTime() - started) / 1_000_000
            // The typed UNKNOWN kind (telemetry): prefer the one the run recorded, else recover it
            // from the thrown failure (a strict-stub/-model BmcUndecidedError on the VERIFIED path, or
            // a ContractPurityError, never set the outcome field).
            val kind = outcome.unknownKind ?: kindOf(t)
            ProofSummary.record(entryFunction, entryClass, expectedV,
                    outcome.verdict, outcome.cached, false, ms, t.message, kind)
            throw t
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        ProofSummary.record(entryFunction, entryClass, expectedV,
                outcome.verdict, outcome.cached, true, ms, null, outcome.unknownKind)
    }

    /** The typed UNKNOWN/disqualification kind a thrown bmc failure carries, if any — used as a
     *  fallback for the summary when the outcome field wasn't set (strict-stub/-model UNKNOWN on the
     *  VERIFIED path, a ContractPurityError). Null for an ordinary refutation. */
    private fun kindOf(t: Throwable): UnknownKind? = when (t) {
        is BmcUndecidedError -> t.kind
        is org.bmc4j.engine.ContractPurityError -> t.kind
        else -> null
    }

    /** Mutable carrier so [runProof] can report the resolved verdict + cache provenance back to
     *  [interceptTestMethod] for the summary record, on both the pass and (pre-throw) fail paths. */
    private class ProofOutcome {
        @JvmField var verdict: Verdict = Verdict.VERIFIED
        @JvmField var cached: Boolean = false
        /** The typed UNKNOWN cause, when this proof resolved (or was demoted) to UNKNOWN; null
         *  otherwise. Surfaced in the proof-results comment so an undecided proof is classifiable. */
        @JvmField var unknownKind: UnknownKind? = null
    }

    @Throws(Throwable::class)
    private fun runProof(method: Method, entryClass: String, entryFunction: String,
                         config: BmcProof?, outcome: ProofOutcome) {
        // Resolve the SAFE solver plan FIRST: the fast external SAT solver runs with text/String
        // reasoning off, so it engages ONLY for a proof proven text-free. This bakes the resolved
        // external-SAT binary path (+ refinement-off mode) into the request so BOTH the verdict cache
        // key and the engine run see it — and FAILS LOUD (plain language) when the fast solver was asked
        // for on a text proof without the opt-out. (A no-op for the common no-external-SAT case.)
        val request = applySolverPlan(requestFor(entryClass, entryFunction, config))

        // JBMC backend (symbolic, all-inputs). For concurrency correctness, see the
        // README's Lincheck guidance — @BmcProof proves logic soundness.
        val backend = VerificationBackends.select(request)

        // domainSplit: if this proof partitions its domain (a domainSplit/slice marker pair), expand
        // it into N slice runs + 1 cover run and aggregate. Analysed off the ORIGINAL classpath; a
        // malformed split (two domainSplit, an orphan slice) throws a DomainSplitError that fails the
        // proof loud, like any processing error. An ordinary proof (no markers) falls straight through.
        val splitPlan = org.bmc4j.engine.DomainSplitBytecode.analyze(
                request.classpath, entryClass, method.name)
        if (splitPlan.isSplit) {
            runSplitProof(method, entryFunction, config, request, backend, splitPlan, outcome)
            return
        }

        // Verdict cache: a proof's deterministic verdict is a pure function of its inputs, so a PASS
        // (VERIFIED for a normal proof; REFUTED/VACUOUS for a fail-on-purpose demo expecting exactly
        // that) whose inputs haven't changed need not be re-verified. Consult the cache on the per-proof
        // path; the cache stores the verdict FACT, and the expectation is judged fresh HERE at read
        // time: only a hit whose stored verdict equals the expectation short-circuits the engine. A
        // non-satisfying hit falls through to a LIVE run — a mismatch (the dangerous drift) must always
        // come from a fresh engine run, never be judged off a cached fact. Failures and TIMEOUT/UNKNOWN
        // are never stored, so this can only ever short-circuit a pass. Fail-open: any cache error just
        // falls through to a run. The cache key uses the EFFECTIVE solver (per-proof override else
        // -Dbmc.solver) so a -Dbmc.solver change invalidates even with unchanged bytecode (cf.
        // unwind/maxStringLength, which are already effective on the request).
        val engineIdentity = backend.engineIdentity() + solverEnvSuffix()
        val cacheRequest = cacheKeyRequest(request, config)
        val expected = config?.expect ?: Verdict.VERIFIED
        val hit = VerdictCache.lookup(cacheRequest, engineIdentity)
        if (hit != null && hit.verdict == expected) {
            outcome.verdict = hit.verdict
            outcome.cached = true
            if (expected == Verdict.VERIFIED) {
                // Report passed without an engine run. (Progress line annotates "cached".)
                // The model + stub policies are RE-JUDGED here from the stored facts, so flipping
                // strictStubs / editing allowStubs re-decides without an engine re-run. A strict-mode
                // unacknowledged stub still turns a cached green into UNKNOWN.
                println("  bmc4j: $entryFunction -> VERIFIED (cached)")
                applyModelPolicy(entryFunction)
                applyStubPolicy(entryFunction, config, hit.stubbedMethods)
            } else {
                // A cached expectation-matching REFUTED/VACUOUS demo pass. Note the replay scratch
                // file is NOT regenerated on a cached pass — it was written by the live run that
                // stored this entry (delete the cache entry or run -Dbmc.noCache=true to re-render).
                println("  bmc4j: $entryFunction -> ${hit.verdict} (cached, as expected)")
            }
            return
        }

        val result: JbmcResult
        try {
            result = backend.verify(request)
        } catch (e: org.bmc4j.engine.ContractPurityError) {
            // A contracted target's body is not provably pure (a side effect a contract would
            // silently drop at every redirected call site). This is a contract-CONFIGURATION error,
            // not a verdict: it is neither a refutation nor an UNKNOWN, and must fail the build
            // UNCONDITIONALLY — never judged against @BmcProof(expect=…) the way a deliberately-false
            // contract's refutation is. Rethrow as-is, before the BmcVerificationError branch below
            // (which would otherwise treat it as a REFUTED that expect=REFUTED could swallow).
            throw e
        } catch (e: BmcUndecidedError) {
            // A pre-framed UNKNOWN out of the backend. Judged against the expectation like any
            // other verdict (an engine-INFRASTRUCTURE unknown never satisfies expected-UNKNOWN).
            outcome.verdict = Verdict.UNKNOWN
            outcome.unknownKind = e.kind
            enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, e)
            return
        } catch (e: BmcVerificationError) {
            // A genuine pre-framed refutation — never reclassify; judge against the expectation.
            outcome.verdict = Verdict.REFUTED
            enforceExpectation(entryFunction, expected, Verdict.REFUTED, e)
            return
        } catch (e: RuntimeException) {
            // Engine-INFRASTRUCTURE failure: the engine couldn't run / produce a verdict (e.g.
            // BundledEngine.extract() IOException, process start failure, a non-verdict
            // IllegalStateException out of Jbmc.exec). That is NOT a refutation — there is no
            // counterexample, nothing was proven wrong. Per the three-way verdict it
            // must surface as UNKNOWN (BmcUndecidedError), reserving REFUTED for an actual JBMC
            // counterexample. Reclassify it here, preserving the original cause for diagnosis.
            // (enforceExpectation rejects it for expected-UNKNOWN: a broken engine must never
            // masquerade as an undecidability demo.)
            outcome.verdict = Verdict.UNKNOWN
            val infra = engineInfraUndecided(backend.id(), entryFunction, e)
            outcome.unknownKind = infra.kind
            enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, infra)
            return
        } catch (e: Error) {
            outcome.verdict = Verdict.UNKNOWN
            val infra = engineInfraUndecided(backend.id(), entryFunction, e)
            outcome.unknownKind = infra.kind
            enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, infra)
            return
        }

        val actual = actualVerdict(result)
        outcome.verdict = actual

        // Out-of-scope (DECLARED package) demotion (verdict HONESTY + the package-waiver loudness
        // invariant): a class under a `bmc { notModeledPackages { … } }` glob has no model body, so JBMC
        // nondet-stubs it. A package waiver may CLASSIFY and DOCUMENT only — it must NEVER suppress: such
        // a reach must NOT be allowed to ride a silent nondet stub to a green (false VERIFIED) or to a
        // refutation resting on havoc. So — on ANY verdict, before the cache store and before the green
        // stub-footnote path — any reached nondet stub whose owning class matches a declared package
        // forces a LOUD, member-named OUT-OF-SCOPE (DECLARED) UNKNOWN (kind OUT_OF_SCOPE, retryable=false:
        // a declared decline is deterministic, never a transient flake), distinct from the generic
        // unmodelled-member text so a reviewer can tell "deliberately declined" from "gap not yet filled".
        //
        // PRECEDENCE (intentional): this is judged FIRST — before the residual-indy / link-failure /
        // unmodelled-member demotions in the REFUTED block below. A reached member that is BOTH on a
        // declared out-of-scope package AND a present-class link stub is classified OUT_OF_SCOPE, not
        // LINK_FAILURE_STUB: a declared waiver is the author's deliberate, deterministic classification
        // and must win over the transient-flake reading (which would mislead with a retry that can never
        // recover a verdict for an area we've declared we won't model). Only an OTHERWISE-UNMODELED class
        // is ever stubbed (a modeled class has a body and never appears here), so the registry wins
        // automatically. An acknowledgment (the same acknowledgeUnmodelled opt-out as the per-member tail)
        // degrades a member to footnoted-nondet instead of UNKNOWN — never silent. Judged before the cache
        // store so an expect=UNKNOWN demo caches no false verdict and the declaration list re-judges.
        run {
            val declaredGlobs = notModeledPackageGlobs()
            if (declaredGlobs.isNotEmpty()) {
                val outOfScope = outOfScopeStubsToDemote(
                        result.stubbedMethods, declaredGlobs, acknowledgedUnmodelled(config))
                if (outOfScope.isNotEmpty()) {
                    outcome.verdict = Verdict.UNKNOWN
                    val err = outOfScopePackageUndecided(backend.id(), entryFunction, outOfScope)
                    outcome.unknownKind = err.kind
                    enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, err)
                    return
                }
            }
        }

        // Residual-invokedynamic demotion: a REFUTED whose reachable slice includes a havoc'd
        // residual-indy marker (see ResidualIndyBytecode) cannot promise a REAL counterexample —
        // the "refutation" may be the havoc itself reaching an artifact path (e.g. a pattern
        // switch's MatchException default arm, unreachable under the real bootstrap). REFUTED is
        // reserved for genuine counterexamples, so this fails toward UNKNOWN, naming the residual
        // sites. Deliberately NOT bypassable via allowStubs (acknowledging a stub silences the
        // footnote on greens; it cannot make a fake counterexample real), and judged BEFORE the
        // cache store so an expect=REFUTED demo can never cache an undecided artifact as a pass.
        if (actual == Verdict.REFUTED) {
            val residual = residualIndyMarkers(result)
            if (residual.isNotEmpty()) {
                outcome.verdict = Verdict.UNKNOWN
                val err = residualIndyUndecided(backend.id(), entryFunction, residual)
                outcome.unknownKind = err.kind
                enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, err)
                return
            }
            // Link-failure demotion (verdict HONESTY): a "refutation" that ran through a nondet stub
            // (stub_ignored_arg* in the trace) for a method whose owning class IS PRESENT on the
            // analysis classpath is a TRANSIENT ENGINE LINK FAILURE, not a counterexample — JBMC failed
            // to link a body it had (a jar entry, never sliced) and havoc'd it, and the "counterexample"
            // rests on that havoc. REFUTED is reserved for genuine counterexamples, so this fails toward
            // an engine-infrastructure UNKNOWN naming the member(s). A stub whose class is genuinely
            // ABSENT (sliced away / a missing dependency) is NOT demoted here — it stays a
            // refutation/footnote on the ordinary nondet-stub path; presence is what separates the two.
            //
            // ONLY demoted when the refutation would otherwise fail the test as an UNEXPECTED REFUTED —
            // the transient-flake case this guards. A proof that PINS expect = REFUTED and matches is
            // getting its intended verdict, so a stub in the trace must NOT steal that pass: a lateinit
            // pre-init read, for one, legitimately refutes THROUGH the property getter's nondet stub.
            // For such a demo the match STANDS as REFUTED; the demotion is reserved for the case it was
            // built for — a refutation surfacing where none was expected, which must self-clear to a
            // re-runnable UNKNOWN rather than turn a clean proof red on a transient link failure.
            run {
                val linkFailures = linkFailuresToDemote(expected, result, request.classpath)
                if (linkFailures.isNotEmpty()) {
                    outcome.verdict = Verdict.UNKNOWN
                    val err = linkFailureUndecided(backend.id(), entryFunction, linkFailures)
                    outcome.unknownKind = err.kind
                    enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, err)
                    return
                }
            }
            // Unmodelled-member demotion (verdict HONESTY): a "refutation" whose violated property is
            // the loud body of a real JDK member the model deliberately doesn't implement
            // (@BmcNotModelled / @BmcNotNeeded / @BmcModelTail, routed through the BmcUnmodelledReached
            // sentinel) is NOT a counterexample in the user's code — it's bmc4j's own MODEL GAP. REFUTED
            // is reserved for genuine counterexamples, so this fails toward UNKNOWN, naming the member
            // and the three ways forward. Judged BEFORE the cache store so an expect=REFUTED demo can
            // never cache a model gap as a real refutation; unmodelledMembers also participates in the
            // cache key (see cacheKeyRequest / acknowledgment below) so an acknowledgment flip re-decides.
            // Honoring an acknowledgment is the revision-6 opt-out: an acknowledged member degrades to the
            // classic nondet-stub-WITH-FOOTNOTE behavior instead of UNKNOWN — never silent.
            val unmodelled = result.unmodelledMembers
            if (unmodelled.isNotEmpty()) {
                val acked = acknowledgedUnmodelled(config)
                val unacked = unmodelled.filter { m -> !isAcknowledged(m, acked) }
                if (unacked.isNotEmpty()) {
                    outcome.verdict = Verdict.UNKNOWN
                    val err = unmodelledMemberUndecided(backend.id(), entryFunction, unacked)
                    outcome.unknownKind = err.kind
                    enforceExpectation(entryFunction, expected, Verdict.UNKNOWN, err)
                    return
                }
                // Every reached member is explicitly acknowledged: degrade to a nondet-stub footnote
                // (the acknowledged-stub path) rather than a hard UNKNOWN — but ONLY if every FAILURE
                // was a sentinel reach. If a genuine (non-sentinel) counterexample also fired, that
                // refutation stands: acknowledging a model gap can never hide a real counterexample.
                if (result.violations.size <= unmodelled.size) {
                    outcome.verdict = Verdict.VERIFIED
                    println(acknowledgedUnmodelledFootnote(entryFunction, unmodelled))
                    if (expected != Verdict.VERIFIED) {
                        // A demo that pinned a non-VERIFIED outcome but now degrades to a green-ish pass
                        // has drifted: name it like any expectation mismatch.
                        throw expectationMismatch(entryFunction, expected, Verdict.VERIFIED, null)
                    }
                    return
                }
                // else: a real counterexample is present alongside the acknowledged reach — fall
                // through to the normal refutation path below, which frames and judges it.
            }
        }

        // Store the verdict iff it's an expectation-matching PASS with a deterministic verdict
        // (VERIFIED for a normal proof; REFUTED/VACUOUS for a demo expecting exactly that), so a later
        // unchanged run can skip the engine. Failures are never stored — a mismatch always re-runs live
        // — and TIMEOUT/UNKNOWN are never stored even when expected (machine-dependent). Fail-open: a
        // write error never affects the verdict.
        VerdictCache.storeIfExpectedMatch(cacheRequest, engineIdentity, result, expected)

        if (actual != Verdict.VERIFIED) {
            if (actual == Verdict.UNKNOWN || actual == Verdict.TIMEOUT) {
                outcome.unknownKind = result.undecidedKind
            }
            enforceExpectation(entryFunction, expected, actual,
                    toError(backend.id(), entryFunction, result, method))
            return
        }
        if (expected != Verdict.VERIFIED) {
            // The dangerous drift: a fail-on-purpose proof came back green — the false claim has
            // stopped being refutable. Loud, named failure.
            throw expectationMismatch(entryFunction, expected, Verdict.VERIFIED, null)
        }
        // Verified as expected: apply the user-model trust policy FIRST (provenance footnote for
        // declared models, loud warning for an override, UNKNOWN under strictModels for an undeclared
        // override), then the nondet-stub policy. Default lenient mode keeps the proof green and prints
        // a one-line footnote for any unacknowledged stub (loud for a user-package stub); strictStubs
        // turns an unacknowledged stub into UNKNOWN (BmcUndecidedError). Acknowledged (allowStubs)
        // stubs are silent. A fully-modeled proof with no user models prints nothing.
        applyModelPolicy(entryFunction)
        applyStubPolicy(entryFunction, config, result.stubbedMethods)
    }

    /**
     * Run a `domainSplit` proof: one COVER run (the soundness gate, `overall => union(slices)`) plus N
     * SLICE runs (the body under each `assume(slice_i)`), fanned out CONCURRENTLY and aggregated.
     *
     * Verdict rule (the issue's semantics, UNCHANGED from the sequential version): the proof PASSES iff
     * the cover VERIFIED **and** every slice VERIFIED. A GAP (a point in the declared domain no slice
     * covers) makes the cover REFUTE, and the whole proof fails loud, because the slices are meaningless
     * if they don't cover the domain. A REFUTED slice surfaces its counterexample; any UNKNOWN run
     * yields an UNKNOWN proof.
     *
     * Parallelism: the N+1 derived runs are independent `BmcRequest`s, so they are submitted to the
     * shared [JbmcConcurrency.fanOutPool] and execute together. The total concurrent jbmc PROCESSES are
     * bounded by [JbmcConcurrency] (the same JVM-wide budget that already gates normal proofs), so a
     * 50-slice split on a 4-wide machine still runs 4 jbmc at a time — never N unbounded processes. The
     * coordinator thread holds no jbmc permit while it waits, so it never starves the budget.
     *
     * EARLY-EXIT under parallelism: the moment any run resolves to a decisive (non-VERIFIED) verdict the
     * coordinator stops waiting and cancels the still-running runs (interrupt → their jbmc processes are
     * killed), so a refutation surfaces in minutes rather than waiting on the slowest slice. Because the
     * cover and the slices run together, a cover refute and a slice refute can RACE; the aggregate
     * verdict is deterministic regardless of finish order — among decisive runs the COVER is reported
     * FIRST (a domain gap is the more fundamental failure: the slices wouldn't have meant anything), and
     * among slices the lowest index wins. So the surfaced counterexample/verdict does not depend on
     * timing.
     *
     * The result carries the fact that this is a domain-SCOPED green — a pass means "P holds for every
     * input the split's overall condition admits", NOT for the full type domain — so a reviewer never
     * mis-reads it as a full-domain proof. (The scope reported is the declared `overall` condition; note
     * that a pre-split `assume` in the body further narrows the real proven domain to `(prior assumes) ∧
     * overall`. A vacuous narrowing still fails loud via the existing vacuity detection, so this is
     * reporting honesty, not soundness.)
     *
     * The per-proof verdict cache is bypassed for splits (caching the aggregate of a slow split is a
     * future optimisation).
     */
    @Throws(Throwable::class)
    private fun runSplitProof(method: Method, entryFunction: String, config: BmcProof?,
                              request: BmcRequest,
                              backend: org.bmc4j.engine.VerificationBackend,
                              plan: org.bmc4j.engine.DomainSplitBytecode.Plan,
                              outcome: ProofOutcome) {
        val expected = config?.expect ?: Verdict.VERIFIED
        val scope = "the split's overall condition (a domain-SCOPED proof, NOT the full type domain)"
        println("  bmc4j: $entryFunction -> domainSplit: 1 cover + ${plan.sliceCount} slice run(s)" +
                " (fan-out, jbmc cap=${JbmcConcurrency.permits})")

        // Build the N+1 derived runs: the cover (the soundness gate) and one per slice. Cover is index
        // 0 in the dispatch list so its result is preferred when several runs are decisive (see below);
        // slices keep their declared order.
        data class DerivedSpec(val req: BmcRequest, val label: String, val isCover: Boolean,
                               val sliceIndex: Int)
        val specs = ArrayList<DerivedSpec>(plan.sliceCount + 1)
        specs.add(DerivedSpec(
                splitRequest(request, org.bmc4j.engine.DomainSplitBytecode.RunPlan.Cover),
                "cover (overall => union of slices)", true, -1))
        for (i in 0 until plan.sliceCount) {
            specs.add(DerivedSpec(
                    splitRequest(request, org.bmc4j.engine.DomainSplitBytecode.RunPlan.Slice(i)),
                    "slice #$i", false, i))
        }

        // Fan out: submit every derived run to the shared pool and collect results as they finish.
        // [evaluateDerived] never throws an expectation error — it returns a classified [DerivedRun],
        // including its framed counterexample/error — so the coordinator owns the single aggregate
        // decision and the parallel tasks stay side-effect-free (no shared `outcome` mutation, no
        // throwing). The jbmc-process budget is enforced inside the backend, not by this pool's size.
        val pool = JbmcConcurrency.fanOutPool
        val futures = HashMap<java.util.concurrent.Future<DerivedRun>, DerivedSpec>(specs.size)
        val completion = java.util.concurrent.ExecutorCompletionService<DerivedRun>(pool)
        for (spec in specs) {
            val f = completion.submit(java.util.concurrent.Callable {
                evaluateDerived(method, entryFunction, backend, spec.req, spec.label)
            })
            futures[f] = spec
        }

        // Drain the runs as they finish. Each run is ranked by a deterministic PRIORITY (cover = -1,
        // slice i = i; lower wins), so the surfaced verdict never depends on finish order: a cover/slice
        // REFUTE race always reports the cover's gap, and racing slice refutes always report the lowest
        // index. We keep the best (lowest-priority) decisive run seen so far. EARLY-EXIT: the instant a
        // run is decisive we cancel every still-pending run that CANNOT outrank it (priority >= the best
        // so far) — those can only return a worse-or-equal decisive result or a VERIFIED, neither of
        // which changes the outcome — and stop as soon as no pending run could still outrank the best.
        // A cover failure (priority -1) is unbeatable, so it cancels everything and ends the wait at
        // once.
        var decisive: DerivedRun? = null
        var decisivePriority = Int.MAX_VALUE
        val pending = HashSet(futures.keys)
        fun priorityOf(spec: DerivedSpec) = if (spec.isCover) -1 else spec.sliceIndex
        try {
            while (pending.isNotEmpty()) {
                // If nothing still pending could outrank the current decisive run, we are done.
                if (decisive != null && pending.none { priorityOf(futures[it]!!) < decisivePriority }) {
                    break
                }
                val f = completion.take() // blocks until the next run finishes
                pending.remove(f)
                val run = try {
                    f.get()
                } catch (e: java.util.concurrent.CancellationException) {
                    continue // we cancelled it; its outcome is irrelevant
                } catch (e: java.util.concurrent.ExecutionException) {
                    // A non-engine failure inside a fan-out task (e.g. a ContractPurityError, which
                    // evaluateDerived deliberately rethrows): cancel the rest and propagate, like the
                    // sequential path let it escape.
                    cancelAll(pending)
                    throw e.cause ?: e
                }
                if (run.verdict == Verdict.VERIFIED) {
                    continue
                }
                // Decisive run. Keep it if it outranks the best so far, then cancel every pending run
                // that can no longer matter (priority >= the new best).
                val priority = priorityOf(futures[f]!!)
                if (priority < decisivePriority) {
                    decisivePriority = priority
                    decisive = run
                }
                val outranked = pending.filter { priorityOf(futures[it]!!) >= decisivePriority }
                cancelAll(outranked)
                pending.removeAll(outranked.toSet())
            }
        } finally {
            // Never leak a jbmc process: cancel anything still in flight on any exit path.
            cancelAll(pending)
        }

        if (decisive == null) {
            // Cover + every slice VERIFIED. A domain-scoped green.
            if (expected != Verdict.VERIFIED) {
                // A fail-on-purpose split that came back fully green: loud, named drift.
                throw expectationMismatch(entryFunction, expected, Verdict.VERIFIED, null)
            }
            outcome.verdict = Verdict.VERIFIED
            println("  bmc4j: $entryFunction -> VERIFIED over $scope")
            return
        }

        // A derived run decided the outcome. Surface it exactly as the sequential path did: set the
        // aggregate verdict/kind and enforce the proof's expectation (which throws for an
        // expect=VERIFIED mismatch, or passes for a matching fail-on-purpose expectation).
        outcome.verdict = decisive.verdict
        if (decisive.verdict == Verdict.UNKNOWN || decisive.verdict == Verdict.TIMEOUT) {
            outcome.unknownKind = decisive.unknownKind
        }
        enforceExpectation(entryFunction, expected, decisive.verdict, decisive.framed!!)
    }

    /** A classified result of one derived split run: its [verdict], the framed error/counterexample to
     *  surface if this run decides the proof (null for a VERIFIED run), and the typed UNKNOWN cause. */
    private class DerivedRun(val verdict: Verdict, val framed: BmcVerificationError?,
                             val unknownKind: UnknownKind?)

    /** Cancel every given fan-out future (interrupt → kills the run's jbmc process if still alive). */
    private fun cancelAll(futures: Collection<java.util.concurrent.Future<DerivedRun>>) {
        for (f in futures) {
            f.cancel(true)
        }
    }

    /**
     * Verify one derived run of a split and CLASSIFY it, without throwing an expectation error or
     * mutating shared state — so it is safe to run concurrently. Returns a [DerivedRun] carrying the
     * run's verdict, its framed counterexample/error (for a decisive run) and its UNKNOWN kind. The
     * coordinator ([runSplitProof]) owns the single aggregate decision.
     *
     * The classification reuses the single-proof verdict mapping ([actualVerdict] / [toError]) so a
     * split run is judged exactly like an ordinary proof — same demotions, same messages — just scoped
     * to its slice/cover via [labelDerived]. A [ContractPurityError] is rethrown unchanged (it is a
     * build-config soundness failure, not a per-run verdict).
     */
    @Throws(Throwable::class)
    private fun evaluateDerived(method: Method, entryFunction: String,
                                backend: org.bmc4j.engine.VerificationBackend, req: BmcRequest,
                                label: String): DerivedRun {
        val result: JbmcResult
        try {
            result = backend.verify(req)
        } catch (e: org.bmc4j.engine.ContractPurityError) {
            throw e
        } catch (e: BmcUndecidedError) {
            return DerivedRun(Verdict.UNKNOWN, labelDerived(e, label), e.kind)
        } catch (e: BmcVerificationError) {
            return DerivedRun(Verdict.REFUTED, labelDerived(e, label), null)
        } catch (e: RuntimeException) {
            val infra = engineInfraUndecided(backend.id(), entryFunction, e)
            return DerivedRun(Verdict.UNKNOWN, labelDerived(infra, label), infra.kind)
        }

        val actual = actualVerdict(result)
        if (actual == Verdict.VERIFIED) {
            println("  bmc4j: $entryFunction -> $label VERIFIED")
            return DerivedRun(Verdict.VERIFIED, null, null)
        }
        val kind = if (actual == Verdict.UNKNOWN || actual == Verdict.TIMEOUT) result.undecidedKind else null
        return DerivedRun(actual,
                labelDerived(toError(backend.id(), entryFunction, result, method), label), kind)
    }

    /** Prefix a derived run's framed failure with which slice/cover produced it, so an aggregated
     *  refutation/unknown names its origin. Preserves the error's type (BmcUndecidedError stays one). */
    private fun labelDerived(err: BmcVerificationError, label: String): BmcVerificationError {
        val msg = "domainSplit $label: " + (err.message ?: "")
        val out = if (err is BmcUndecidedError) {
            BmcUndecidedError(msg, err.isEngineInfrastructure(), err.kind)
        } else {
            BmcVerificationError(msg)
        }
        out.stackTrace = err.stackTrace
        err.cause?.let { out.initCause(it) }
        return out
    }

    /** A [BmcRequest] for one derived [run] of a split: same entry/classpath/budget, with the run set. */
    private fun splitRequest(request: BmcRequest,
                             run: org.bmc4j.engine.DomainSplitBytecode.RunPlan): BmcRequest =
            BmcRequest(request.entryClass, request.entryFunction, request.classpath, request.unwind,
                    request.unwindingAssertions, request.maxStringLength, request.solver,
                    request.timeoutSeconds, run, request.externalSatPath, request.stringRefinementOff)

    companion object {

        private const val UNWIND_PROP = "bmc.unwind"
        private const val MAX_STRING_PROP = "bmc.maxStringLength"
        private const val TIMEOUT_PROP = "bmc.timeoutSeconds"
        private const val DEFAULT_UNWIND = 16
        private const val DEFAULT_MAX_STRING = 16
        private const val DEFAULT_TIMEOUT = 0 // 0 = no timeout (run to completion)

        private const val SOLVER_PROP = "bmc.solver"
        private const val STRICT_STUBS_PROP = "bmc.strictStubs"
        private const val ALLOW_STUBS_PROP = "bmc.allowStubs"
        private const val USER_PACKAGES_PROP = "bmc.userPackages"
        private const val STRICT_MODELS_PROP = "bmc.strictModels"
        private const val ACK_UNMODELLED_PROP = "bmc.acknowledgeUnmodelled"

        /** The residual-invokedynamic marker stubs harvested for this result (dot-form FQNs), deduped. */
        internal fun residualIndyMarkers(result: JbmcResult): List<String> =
                result.stubbedMethods
                        .filter { it.startsWith(ResidualIndyBytecode.MARKER_FQN_PREFIX) }
                        .distinct()

        /**
         * The UNKNOWN framing for a refutation demoted because its slice includes residual
         * `invokedynamic` havoc. Non-infrastructure: this is a genuine analysis limit, so it
         * satisfies `expect = UNKNOWN` (the supported way to pin a proof that deliberately
         * exercises a residual site).
         */
        internal fun residualIndyUndecided(engineId: String, entryFunction: String,
                                           markers: List<String>): BmcUndecidedError {
            val sb = StringBuilder()
            sb.append(engineId.uppercase()).append(" could not decide ").append(entryFunction)
                    .append(" (UNKNOWN)\n")
            sb.append("  ? the refutation reached un-desugared invokedynamic, whose result is havoc'd:\n")
            for (m in markers) {
                sb.append("      ")
                        .append(m.substring(ResidualIndyBytecode.MARKER_FQN_PREFIX.length))
                        .append("  (indyName__bootstrapOwner)\n")
            }
            sb.append("    The counterexample may be an artifact of that havoc (e.g. a pattern switch's\n")
                    .append("    MatchException default arm, unreachable under the real bootstrap), so this is\n")
                    .append("    NOT reported as a refutation. To get a decision:\n")
                    .append("      - restructure to an indy-free form (e.g. a classic enum switch without\n")
                    .append("        'case null' compiles to the analyzable \$SwitchMap form)\n")
                    .append("      - or keep the construct and pin the proof with @BmcProof(expect = UNKNOWN)\n")
                    .append("        to document the analysis limit deliberately.")
            return BmcUndecidedError(sb.toString().trimEnd())
        }

        /**
         * The subset of [result]'s harvested link-failure stub members whose OWNING CLASS is present on
         * [classpath] — the ones that demote the refutation to UNKNOWN. A stub member is
         * `pkg.Class.method(params)`; its owning class is the dotted prefix before the last `.method`.
         * Presence is checked by looking for the class's `.class` resource on any classpath entry (a
         * directory entry's file, or an entry inside a jar). Deduped, first-seen order. A member whose
         * class can't be found (genuinely absent — sliced away / a missing dependency) is dropped, so it
         * stays on the ordinary nondet-stub path rather than being demoted. Fail-safe: any IO error
         * resolving an entry is treated as "not present" for that entry (never throws).
         */
        internal fun linkFailuresPresentOnClasspath(result: JbmcResult, classpath: String?): List<String> {
            val members = result.linkFailureStubs
            if (members.isEmpty() || classpath.isNullOrBlank()) {
                return emptyList()
            }
            val out = LinkedHashSet<String>()
            for (member in members) {
                val owner = ownerClassOf(member) ?: continue
                if (classIsPresentOnClasspath(owner, classpath)) {
                    out.add(member)
                }
            }
            return out.toList()
        }

        /**
         * The present-on-classpath link-failure stub members that should DEMOTE this refutation to
         * UNKNOWN — i.e. [linkFailuresPresentOnClasspath], but ONLY when [expected] is not REFUTED.
         *
         * The demotion exists to keep a TRANSIENT engine link failure from turning a clean proof red as
         * an unexpected refutation; it self-clears on a re-run. A proof that PINS `expect = REFUTED` and
         * matches is getting its intended verdict, so a stub in the trace must not steal that pass — a
         * lateinit pre-init read, for one, legitimately refutes THROUGH the property getter's nondet
         * stub. For an expect-REFUTED demo the match STANDS (empty list); the demotion is reserved for
         * a refutation surfacing where none was expected. Pure; never throws.
         */
        internal fun linkFailuresToDemote(expected: Verdict, result: JbmcResult,
                                          classpath: String?): List<String> {
            if (expected == Verdict.REFUTED) {
                return emptyList()
            }
            return linkFailuresPresentOnClasspath(result, classpath)
        }

        /** The owning class of a `pkg.Class.method(params)` member: everything before the last `.` that
         *  precedes the `method(` — i.e. drop the `(params)` then the trailing `.method`. Null if it has
         *  no dot-separated method part. */
        internal fun ownerClassOf(member: String): String? {
            val name = member.substringBefore('(')
            val lastDot = name.lastIndexOf('.')
            return if (lastDot > 0) name.substring(0, lastDot) else null
        }

        /** True if [owner] (a dotted class name, e.g. `kotlin.ranges.RangesKt`) has a `.class` resource
         *  on [classpath] — present as a file under a directory entry, or as an entry inside a jar. */
        internal fun classIsPresentOnClasspath(owner: String, classpath: String): Boolean {
            val resource = owner.replace('.', '/') + ".class"
            for (entry in classpath.split(java.io.File.pathSeparatorChar)) {
                if (entry.isEmpty()) {
                    continue
                }
                try {
                    val p = java.nio.file.Path.of(entry)
                    if (java.nio.file.Files.isDirectory(p)) {
                        if (java.nio.file.Files.isRegularFile(p.resolve(resource))) {
                            return true
                        }
                    } else if (java.nio.file.Files.isRegularFile(p)
                            && (entry.endsWith(".jar", true) || entry.endsWith(".zip", true))) {
                        java.util.zip.ZipFile(p.toFile()).use { zf ->
                            if (zf.getEntry(resource) != null) {
                                return true
                            }
                        }
                    }
                } catch (e: Exception) {
                    // A bad/locked entry is treated as "not present here" — never let a classpath probe
                    // throw out of the verdict path; fall through to the next entry.
                }
            }
            return false
        }

        /**
         * The UNKNOWN framing for a refutation demoted because it ran through a nondet stub of a method
         * whose owning class IS present on the analysis classpath — NOT a counterexample. Two shapes feed
         * this, both present-on-classpath link failures: a TRANSIENT engine link failure (engine had a body
         * but havoc'd it; self-clears on re-run), and an UNRESOLVED DEVIRTUALIZATION (a "no body for callee"
         * property on an interface/abstract call it could not bind to its present concrete override — e.g.
         * a modelled-abstract java.util.List/Set/Map held over an UNMODELLED concrete subtype; this needs the
         * abstract base modelled). Engine-INFRASTRUCTURE (does NOT satisfy `expect = UNKNOWN`); either way
         * honestly UNKNOWN — never a false REFUTED. Names the member(s).
         */
        internal fun linkFailureUndecided(engineId: String, entryFunction: String,
                                          members: List<String>): BmcUndecidedError {
            val sb = StringBuilder()
            sb.append(engineId.uppercase()).append(" could not decide ").append(entryFunction)
                    .append(" (UNKNOWN)\n")
            sb.append("  ? the refutation ran through a nondet stub of ").append(members.size)
                    .append(if (members.size == 1) " method whose class IS on the classpath:\n"
                            else " methods whose classes ARE on the classpath:\n")
            for (m in members) {
                sb.append("      ").append(m).append('\n')
            }
            sb.append("    The engine nondet-stubbed a method whose class IS present (not sliced away) — either\n")
                    .append("    a transient link failure, or an interface/abstract call it could not devirtualize\n")
                    .append("    to its present concrete override — so the \"counterexample\" rests on that havoc and\n")
                    .append("    is NOT a real refutation. This is engine infrastructure, not a verdict: it does not\n")
                    .append("    satisfy expect = UNKNOWN. A transient link failure self-clears on a re-run; an\n")
                    .append("    unresolved devirtualization needs the (abstract) declaring type modelled, or the\n")
                    .append("    member acknowledged via acknowledgeUnmodelled. If it persists, file it with the trace.")
            return BmcUndecidedError(sb.toString().trimEnd(), true, UnknownKind.LINK_FAILURE_STUB)
        }

        /**
         * The UNKNOWN framing for a refutation demoted because the proof REACHED a member bmc4j does
         * not model (a loud body routed through [org.bmc4j.engine] BmcUnmodelledReached). Verdict
         * honesty: a model gap is OUR limitation, never the user's counterexample. Non-infrastructure
         * (a genuine, acknowledged-able analysis limit), so it satisfies `expect = UNKNOWN`. Names the
         * member(s) and the three ways forward: model it, acknowledge it, or avoid it.
         */
        internal fun unmodelledMemberUndecided(engineId: String, entryFunction: String,
                                               members: List<String>): BmcUndecidedError {
            val sb = StringBuilder()
            sb.append(engineId.uppercase()).append(" could not decide ").append(entryFunction)
                    .append(" (UNKNOWN)\n")
            sb.append("  ? the proof reached ").append(members.size)
                    .append(if (members.size == 1) " member bmc4j does not model:\n"
                            else " members bmc4j does not model:\n")
            for (m in members) {
                sb.append("      ").append(m).append('\n')
            }
            sb.append("    bmc4j deliberately does not model this member (@BmcNotModelled /")
                    .append(" @BmcNotNeeded / @BmcModelTail), so its loud stub fired. This is bmc4j's own\n")
                    .append("    MODELING GAP, not a counterexample in your code — so the verdict is UNKNOWN,\n")
                    .append("    never REFUTED. To get a decision:\n")
                    .append("      - model it: add a bounded stand-in in bmc-models / src/bmcModel; or\n")
                    .append("      - acknowledge it: @BmcProof(acknowledgeUnmodelled = {\"")
                    .append(members[0]).append("\"}) or bmc { acknowledgeUnmodelled = [...] }")
                    .append(" (treats it as a nondet stub, footnoted); or\n")
                    .append("      - restructure the proof so the member isn't reached.")
            return BmcUndecidedError(sb.toString().trimEnd(), false, UnknownKind.UNMODELLED_MEMBER)
        }

        /**
         * The declared deliberately-out-of-scope package globs (`bmc { notModeledPackages { … } }`,
         * forwarded as `-Dbmc.notModeledPackages`), comma-separated. Empty when none are declared.
         */
        internal fun notModeledPackageGlobs(): List<String> =
                org.bmc4j.analysis.PackageReach.declaredGlobs()

        /**
         * True if [stubFqn] (a harvested nondet-stub `pkg.Class.method` member name) is under one of the
         * declared out-of-scope package [globs]. Glob semantics are RECURSIVE: `java.nio.*` (or the bare
         * `java.nio`) matches `java.nio.ByteBuffer.get` AND `java.nio.file.Path.resolve` — a subpackage
         * of an out-of-scope area is itself out of scope. An exact glob with no wildcard still matches
         * recursively on the dotted boundary, so `java.sql` covers `java.sql.Date.toString` and below.
         */
        internal fun matchesNotModeledPackage(stubFqn: String, globs: List<String>): Boolean =
                org.bmc4j.analysis.PackageReach.matchesPackage(stubFqn, globs)

        /**
         * The harvested nondet-stub members ([stubbed]) that must DEMOTE this proof to a loud
         * out-of-scope (declared) UNKNOWN: those under a declared package [globs] that are NOT
         * acknowledged. Pure (fact → policy), deduped, first-seen order — so it runs identically on a
         * fresh result and a cache hit.
         *
         * PRECEDENCE (registry wins): only an OTHERWISE-UNMODELED class is ever nondet-stubbed — a class
         * bmc4j MODELS has a body and never appears in [stubbed] — so a modeled class inside an otherwise
         * waived package is simply absent here and keeps its model verdict, with no special casing. The
         * waiver only ever bites the genuinely-unmodeled remainder.
         */
        internal fun outOfScopeStubsToDemote(stubbed: List<String>, globs: List<String>,
                                             acked: List<String>): List<String> {
            if (globs.isEmpty() || stubbed.isEmpty()) {
                return emptyList()
            }
            return stubbed
                    .filter { matchesNotModeledPackage(it, globs) }
                    .filter { !isAcknowledged("$it()", acked) }
                    .distinct()
        }

        /**
         * The UNKNOWN framing for a proof that REACHED a class under a DECLARED out-of-scope package
         * (`bmc { notModeledPackages { … } }`). DISTINCT from [unmodelledMemberUndecided]: that is a
         * model gap not yet filled; THIS is a deliberately-declined area. Carries the OUT_OF_SCOPE kind
         * (retryable=false: a declared decline is deterministic, a re-run gives the same answer).
         * Non-infrastructure (a declared, acknowledged-able analysis boundary), so it satisfies
         * `expect = UNKNOWN`. Loud and member-named — a package waiver classifies and documents, it never
         * suppresses.
         */
        internal fun outOfScopePackageUndecided(engineId: String, entryFunction: String,
                                                members: List<String>): BmcUndecidedError {
            val sb = StringBuilder()
            sb.append(engineId.uppercase()).append(" could not decide ").append(entryFunction)
                    .append(" (UNKNOWN)\n")
            sb.append("  ? the proof reached ").append(members.size)
                    .append(if (members.size == 1) " member in a DECLARED out-of-scope package:\n"
                            else " members in DECLARED out-of-scope packages:\n")
            for (m in members) {
                sb.append("      ").append(m).append("  (out-of-scope (declared))\n")
            }
            sb.append("    This package is declared OUT OF SCOPE for modeling via")
                    .append(" bmc { notModeledPackages { … } }, so bmc4j has no model for it. A declared\n")
                    .append("    waiver CLASSIFIES the reach — it never silently trusts a nondet stub — so the\n")
                    .append("    verdict is UNKNOWN (deliberately declined), NOT a false VERIFIED and NOT a\n")
                    .append("    refutation. To get a decision:\n")
                    .append("      - model the class (add a bounded stand-in in bmc-models / src/bmcModel); or\n")
                    .append("      - remove the package from notModeledPackages and model what the proof reaches; or\n")
                    .append("      - acknowledge it: @BmcProof(acknowledgeUnmodelled = {\"")
                    .append(members[0]).append("\"})")
                    .append(" (treats it as a nondet stub, footnoted); or\n")
                    .append("      - restructure the proof so the out-of-scope class isn't reached.")
            return BmcUndecidedError(sb.toString().trimEnd(), false, UnknownKind.OUT_OF_SCOPE)
        }

        /** The footnote printed when an acknowledged unmodelled member was reached (degraded to a
         *  nondet stub — green-ish, never silent). Sibling of the [footnote] nondet-stub note. */
        internal fun acknowledgedUnmodelledFootnote(entryFunction: String,
                                                    members: List<String>): String {
            val sb = StringBuilder()
            sb.append("  bmc4j: ").append(entryFunction).append(" -> reached ")
                    .append(members.size)
                    .append(if (members.size == 1) " ACKNOWLEDGED unmodelled member"
                            else " ACKNOWLEDGED unmodelled members")
                    .append(" (treated as nondet stub; verdict assumes it's pure / never throws):\n")
            sb.append("      ").append(members.joinToString(", "))
            return sb.toString()
        }

        /** Per-proof `@BmcProof(acknowledgeUnmodelled=…)` merged with `-Dbmc.acknowledgeUnmodelled`. */
        internal fun acknowledgedUnmodelled(config: BmcProof?): List<String> {
            val out = mutableListOf<String>()
            config?.acknowledgeUnmodelled?.forEach { s ->
                if (!s.isNullOrBlank()) {
                    out.add(s.trim())
                }
            }
            System.getProperty(ACK_UNMODELLED_PROP, "").split(",").forEach { s ->
                if (s.isNotBlank()) {
                    out.add(s.trim())
                }
            }
            return out
        }

        /**
         * Whether `member` (rendered `pkg.Class.method(params)`) is acknowledged by `acked`. Each
         * pattern matches the member's NAME part (`pkg.Class.method`, params ignored) exactly, or with
         * a trailing `.*` wildcard for "any method of the class / package". Mirrors [StubPolicy] match.
         */
        internal fun isAcknowledged(member: String, acked: List<String>): Boolean {
            val name = member.substringBefore('(')
            for (pat in acked) {
                if (pat == name) {
                    return true
                }
                if (pat.endsWith(".*")) {
                    val prefix = pat.dropLast(1) // keep the trailing '.'
                    if (name.startsWith(prefix)) {
                        return true
                    }
                }
            }
            return false
        }

        /** Map an engine result onto the user-facing four-way verdict (vacuity is carried as a
         *  flavour of REFUTED internally, but is its own expectation externally). */
        internal fun actualVerdict(result: JbmcResult): Verdict = when {
            result.isVerified -> Verdict.VERIFIED
            result.isVacuous -> Verdict.VACUOUS
            // A wall-clock expiry is the structured TIMEOUT subtype; other undecided causes
            // (solver crash, unparseable output) stay plain UNKNOWN.
            result.isUnknown -> if (result.isTimeout) Verdict.TIMEOUT else Verdict.UNKNOWN
            else -> Verdict.REFUTED
        }

        /**
         * Judge an actual non-VERIFIED verdict against the proof's expectation.
         *
         * - **Match** → the proof PASSES (the framed error is swallowed; a confirmation line is
         *   printed so the run log still shows the real verdict). Exception: an engine-INFRASTRUCTURE
         *   UNKNOWN never satisfies expected-UNKNOWN — a broken engine isn't an undecidability demo.
         * - **Expectation is VERIFIED** (the default) → rethrow the framed error unchanged: exactly
         *   the pre-`expect` behavior.
         * - **Mismatch between two non-VERIFIED verdicts** → fail naming both, with the framed
         *   error attached as the cause.
         */
        internal fun enforceExpectation(entryFunction: String, expected: Verdict,
                                        actual: Verdict, framed: BmcVerificationError) {
            if (expected == Verdict.VERIFIED) {
                throw framed // normal proof: any non-verified verdict fails as before
            }
            val infra = framed is BmcUndecidedError && framed.isEngineInfrastructure()
            // TIMEOUT is the structured subtype of UNKNOWN: expect=UNKNOWN accepts a timeout too;
            // expect=TIMEOUT requires the budget to have actually fired.
            val match = expected == actual
                    || (expected == Verdict.UNKNOWN && actual == Verdict.TIMEOUT)
            if (match && !infra) {
                println("  bmc4j: $entryFunction -> $actual (as expected)")
                return // the declared verdict arrived: the fail-on-purpose proof passes
            }
            if (match) {
                // infra-UNKNOWN offered for expected-UNKNOWN/TIMEOUT: reject with the infra framing intact.
                val err = BmcVerificationError(
                        "$entryFunction expected $expected, but the engine infrastructure failed before" +
                                " producing a verdict - that is not a real $expected (fix the engine, then re-run)")
                err.initCause(framed)
                throw err
            }
            throw expectationMismatch(entryFunction, expected, actual, framed)
        }

        /** A loud, both-verdicts-named expectation failure. */
        internal fun expectationMismatch(entryFunction: String, expected: Verdict,
                                         actual: Verdict, cause: BmcVerificationError?): BmcVerificationError {
            val sb = StringBuilder()
            sb.append(entryFunction).append(" expected ").append(expected)
                    .append(", got ").append(actual).append('\n')
            if (actual == Verdict.VERIFIED) {
                sb.append("  ! a fail-on-purpose proof came back green: the false claim has stopped being\n")
                        .append("    refutable - the desugar/guard this demo protects may have regressed.")
            } else {
                sb.append("  ! the proof still fails, but not the way it declares - inspect the attached\n")
                        .append("    cause and either fix the regression or update expect() if the new verdict\n")
                        .append("    is genuinely intended.")
            }
            val err = BmcVerificationError(sb.toString())
            if (cause != null) {
                err.initCause(cause)
            }
            return err
        }

        /**
         * Apply the stub policy to a VERIFIED proof's harvested stub list. Pure split of
         * fact-vs-policy: the same logic runs on a fresh result and on a cache hit (so re-judging is free).
         *
         * - **lenient (default):** print a footnote listing the unacknowledged stubs; the proof stays
         *   green. A stub from the user's own package prints a louder config-bug warning.
         * - **strict (`-Dbmc.strictStubs=true`):** any unacknowledged stub throws UNKNOWN
         *   (a [BmcUndecidedError]) with the stub list and the three remedies.
         * - Acknowledged stubs (per-proof `allowStubs` + build-wide `-Dbmc.allowStubs`) are
         *   silent in both modes.
         */
        internal fun applyStubPolicy(entryFunction: String, config: BmcProof?, stubbed: List<String>?) {
            if (stubbed.isNullOrEmpty()) {
                return
            }
            val policy = StubPolicy.judge(stubbed, effectiveAllowStubs(config),
                    System.getProperty(USER_PACKAGES_PROP, ""))
            if (!policy.hasUnacknowledged()) {
                return // every reached stub is acknowledged
            }
            if (strictStubs()) {
                throw BmcUndecidedError(strictStubMessage(entryFunction, policy))
            }
            // Lenient: footnote (loud for user-package stubs), green either way.
            println(footnote(entryFunction, policy))
        }

        /**
         * Apply the user-model TRUST policy to a VERIFIED proof. Sibling of [applyStubPolicy]: same
         * fact-vs-policy split (the facts — declared intent + the models present under `src/bmcModel` —
         * come from [ModelManifest], read identically on a fresh result and a cache hit), and the same
         * footnote → warn → strict ladder.
         *
         * - **provenance footnote (lenient + strict):** name every declared user model on this
         *   proof's analysis classpath; for `domain` models append the declared rationale, so a
         *   green proof that rests on an intentional divergence says so.
         * - **override warning (lenient + strict):** a present user model shadowing a bundled/JDK
         *   verified model is warned loudly — you've replaced a checked stand-in with an unchecked one.
         * - **strict (`-Dbmc.strictModels=true`):** a present user model with no intent
         *   declaration turns the verdict into UNKNOWN — no proof silently rests on an undeclared
         *   override.
         *
         * Granularity note: relevance is "the user model was on this proof's analysis classpath", not
         * "provably called by this proof" — JBMC emits no per-proof which-model-was-linked report (only the
         * nondet-stub messages the stub policy uses), so this can over-attribute a model to a proof in the
         * same module that didn't actually touch it. The footnote wording is classpath-scoped accordingly.
         */
        internal fun applyModelPolicy(entryFunction: String) {
            val manifest = ModelManifest.fromSystemProperties()
            if (manifest.isEmpty) {
                return // no user models registered or present — nothing to surface
            }
            val policy = ModelPolicy.judge(manifest)
            if (!policy.hasAnyPresent()) {
                return
            }
            if (strictModels() && policy.hasUndeclared()) {
                throw BmcUndecidedError(strictModelMessage(entryFunction, policy))
            }
            println(modelFootnote(entryFunction, policy))
        }

        /** Whether strict-model mode is on: `-Dbmc.strictModels=true` (forwarded from `bmc {}`). */
        private fun strictModels(): Boolean =
                System.getProperty(STRICT_MODELS_PROP, "false").toBoolean()

        /** The lenient/strict provenance footnote: names the user models a green proof rested on. */
        private fun modelFootnote(entryFunction: String, policy: ModelPolicy): String {
            val sb = StringBuilder()
            sb.append("  bmc4j: ").append(entryFunction)
                    .append(" -> VERIFIED under user model(s) on the analysis classpath:")
            for (m: UserModel in policy.declaredPresent()) {
                sb.append("\n      ")
                if (m.isDomain) {
                    sb.append("domain model ").append(m.className)
                            .append(" (assumes ").append(m.rationale).append(')')
                } else {
                    sb.append("conformant model ").append(m.className)
                            .append(" (claims JDK fidelity)")
                }
            }
            for (undeclared in policy.undeclaredPresent()) {
                sb.append("\n      UNDECLARED model ").append(undeclared)
                        .append(" — no bmc { models { … } } intent; declare it conformant(...) or" +
                                " domain(\"why\"), or run -Dbmc.strictModels=true to fail on it.")
            }
            if (policy.hasOverriding()) {
                sb.append("\n  bmc4j: WARNING ").append(entryFunction)
                        .append(" shadows a bundled/verified model with an UNCHECKED user model: ")
                        .append(policy.overriding().joinToString(", "))
                        .append(" — your stand-in replaces bmc4j's verified one; verify it" +
                                " (conformant models can run the same conformance harness as bundled models)" +
                                " or mark it domain(\"why\") if the divergence is intentional.")
            }
            return sb.toString()
        }

        /** The strict-mode UNKNOWN message for an undeclared user-model override. */
        private fun strictModelMessage(entryFunction: String, policy: ModelPolicy): String {
            val sb = StringBuilder()
            sb.append("JBMC could not trust ").append(entryFunction).append(" (UNKNOWN)\n")
            sb.append("  ? strictModels is on and this proof's classpath includes ")
                    .append(policy.undeclaredPresent().size)
                    .append(" undeclared user model(s):\n")
            sb.append("      ").append(policy.undeclaredPresent().joinToString(", ")).append('\n')
            sb.append("    A user model is Bmc.assume() at classpath altitude — it can silently change what\n")
                    .append("    the proof means. Without a declared intent the verdict isn't trustworthy.\n")
                    .append("    No counterexample: this is NOT a refutation. Declare the model's intent:\n")
                    .append("      - bmc { models { conformant(\"")
                    .append(policy.undeclaredPresent()[0])
                    .append("\") } }  (it claims JDK fidelity — verifiable by the conformance harness); or\n")
                    .append("      - bmc { models { domain(\"")
                    .append(policy.undeclaredPresent()[0])
                    .append("\", \"why it diverges\") } }  (intentional divergence, footnoted on green proofs); or\n")
                    .append("      - remove it from src/bmcModel if it shouldn't be shadowing.")
            return sb.toString()
        }

        /** Whether strict-stub mode is on: `-Dbmc.strictStubs=true` (forwarded from `bmc {}`). */
        private fun strictStubs(): Boolean =
                System.getProperty(STRICT_STUBS_PROP, "false").toBoolean()

        /** Per-proof `@BmcProof(allowStubs=…)` merged with build-wide `-Dbmc.allowStubs` (CSV). */
        internal fun effectiveAllowStubs(config: BmcProof?): List<String> {
            val out = mutableListOf<String>()
            config?.allowStubs?.forEach { s ->
                if (!s.isNullOrBlank()) {
                    out.add(s.trim())
                }
            }
            System.getProperty(ALLOW_STUBS_PROP, "").split(",").forEach { s ->
                if (s.isNotBlank()) {
                    out.add(s.trim())
                }
            }
            return out
        }

        /** The lenient-mode footnote: a one-line deprecation-style warning under the (passed) proof. */
        private fun footnote(entryFunction: String, policy: StubPolicy): String {
            val unack = policy.unacknowledged
            val sb = StringBuilder()
            sb.append("  bmc4j: ").append(entryFunction).append(" -> VERIFIED with ")
                    .append(unack.size).append(if (unack.size == 1) " nondet stub" else " nondet stubs")
                    .append(" (verdict assumes they're pure / never throw):\n")
            sb.append("      ").append(unack.joinToString(", "))
            if (policy.hasUserOwned()) {
                // A stub from the user's OWN classpath is almost always a missing dependency — warn loud
                // even in lenient mode (it's a config bug, not a JDK modeling gap).
                sb.append("\n  bmc4j: WARNING ").append(entryFunction)
                        .append(" stubbed a method from YOUR OWN classpath — likely a missing dependency," +
                                " not a modeling gap: ")
                        .append(policy.userOwned.joinToString(", "))
            }
            sb.append("\n      acknowledge with @BmcProof(allowStubs = {\"")
                    .append(unack[0]).append("\"}) or bmc { allowStubs = [...] }; model it in bmc-models;" +
                            " or run -Dbmc.strictStubs=true to fail on it.")
            return sb.toString()
        }

        /** The strict-mode UNKNOWN message: stub list + the three remedies (model / allowStubs / restructure). */
        private fun strictStubMessage(entryFunction: String, policy: StubPolicy): String {
            val sb = StringBuilder()
            sb.append("JBMC could not decide ").append(entryFunction).append(" (UNKNOWN)\n")
            sb.append("  ? strictStubs is on and this proof reached ").append(policy.unacknowledged.size)
                    .append(" unacknowledged nondet stub(s):\n")
            sb.append("      ").append(policy.unacknowledged.joinToString(", ")).append('\n')
            if (policy.hasUserOwned()) {
                sb.append("      (").append(policy.userOwned.joinToString(", "))
                        .append(" is from YOUR OWN classpath — likely a missing dependency)\n")
            }
            sb.append("    The verdict rests on havoc'd stand-ins for those methods, so it isn't trustworthy.\n")
                    .append("    No counterexample: this is NOT a refutation. To get a sound decision, either:\n")
                    .append("      - model it: add a bounded stand-in in bmc-models / src/bmcModel; or\n")
                    .append("      - acknowledge it: @BmcProof(allowStubs = {\"")
                    .append(policy.unacknowledged[0])
                    .append("\"}) or bmc { allowStubs = [...] } (if nondet is sound for what you prove); or\n")
                    .append("      - restructure the proof so the method isn't reached.")
            return sb.toString()
        }

        /**
         * Resolve the SAFE solver plan for a proof and bake its outcome into the request.
         *
         * The fast external SAT solver runs the engine with text/String reasoning OFF, so it is only
         * sound for a proof that touches no text. This:
         *  - resolves the requested solver by precedence (per-proof `@BmcProof(solver)` > project
         *    `bmc{solver}`/`-Dbmc.solver` > global `bmc{externalSat}`/`-Dbmc.externalSat`);
         *  - resolves a named fast solver (`"kissat"`) to its bundled binary, gracefully declining to
         *    the default solver (with a plain-language log) when none is bundled on this platform;
         *  - applies the text-use guard ([StringUseClassifier]): the fast solver engages ONLY for a
         *    proof proven text-free; a text proof that asked for it FAILS LOUD by default, or (with the
         *    opt-out) falls back to the SOUND default solver — never runs text-reasoning-off and passes.
         *
         * Returns the request with [BmcRequest.externalSatPath] / [BmcRequest.stringRefinementOff] set
         * when (and only when) the fast solver was soundly selected; a no-op (returns [request]
         * unchanged) when no external SAT is in play. Throws [BmcVerificationError] for the fail-loud
         * case (a configuration error the user must resolve), so it fails the proof with a clear message.
         */
        @Throws(BmcVerificationError::class)
        internal fun applySolverPlan(request: BmcRequest): BmcRequest {
            val decision = org.bmc4j.engine.SolverPlan.resolve(
                    org.bmc4j.engine.SolverPlan.SolverRequest(
                            request.solver,
                            System.getProperty("bmc.externalSat", ""),
                            request.entryClass,
                            request.classpath))
            return when (decision) {
                is org.bmc4j.engine.SolverPlan.Decision.ExternalSat ->
                    BmcRequest(request.entryClass, request.entryFunction, request.classpath,
                            request.unwind, request.unwindingAssertions, request.maxStringLength,
                            request.solver, request.timeoutSeconds, request.domainSplitRun,
                            decision.path, true)
                is org.bmc4j.engine.SolverPlan.Decision.Builtin -> {
                    if (decision.note != null) {
                        println("  bmc4j: ${request.entryFunction} -> ${decision.note}")
                    }
                    // Built-in/default solver: ensure no stale external-SAT identity rides on the request.
                    if (request.externalSatPath.isEmpty() && !request.stringRefinementOff) {
                        request
                    } else {
                        BmcRequest(request.entryClass, request.entryFunction, request.classpath,
                                request.unwind, request.unwindingAssertions, request.maxStringLength,
                                request.solver, request.timeoutSeconds, request.domainSplitRun, "", false)
                    }
                }
                is org.bmc4j.engine.SolverPlan.Decision.FailLoud -> {
                    // Surface the plain-language refusal (it names the text/String cause) to stdout, so the
                    // build log shows WHY the fast solver was refused — a thrown error alone may not reach
                    // the console the operator (and the soundness smoke) reads. Then fail loud.
                    println("  bmc4j: ${request.entryFunction} can't use the fast solver — ${decision.message}")
                    throw BmcVerificationError(
                            "${request.entryFunction} can't use the fast solver:\n    ${decision.message}")
                }
            }
        }

        /**
         * The request used for the verdict-cache key: identical to [request] but with the EFFECTIVE
         * solver baked into the solver field (per-proof `@BmcProof(solver=…)` else `-Dbmc.solver`,
         * else ""). The plain [request] carries only the per-proof override, so without this a change to
         * the build-wide `-Dbmc.solver` default would not invalidate cached verdicts. unwind
         * and maxStringLength are already effective on [request], so they need no adjustment here. The
         * resolved external-SAT path + refinement-off mode (set by [applySolverPlan]) are PRESERVED, so
         * a verdict proven with text reasoning off is never served for a text-reasoning-on request.
         */
        internal fun cacheKeyRequest(request: BmcRequest, config: BmcProof?): BmcRequest {
            val effSolver = effectiveSolver(config)
            if (effSolver == request.solver) {
                return request
            }
            return BmcRequest(request.entryClass, request.entryFunction, request.classpath,
                    request.unwind, request.unwindingAssertions, request.maxStringLength,
                    effSolver, request.timeoutSeconds, request.domainSplitRun,
                    request.externalSatPath, request.stringRefinementOff)
        }

        /**
         * Solver-related sysprops that change what the engine actually does but aren't on the request — the
         * external-SAT / external-SMT2 solver and the solver PATH dir. Folded into the cache's engine
         * identity so e.g. enabling an external SAT solver invalidates cached verdicts. Empty
         * when none are set (the common case), so it doesn't perturb the default key.
         */
        internal fun solverEnvSuffix(): String {
            val sb = StringBuilder()
            appendProp(sb, "bmc.externalSat")
            appendProp(sb, "bmc.solverCmd")
            appendProp(sb, "bmc.solverPath")
            // The build-wide unmodelled-member acknowledgment changes outcomes (an acknowledged reach
            // degrades from UNKNOWN to a footnoted pass), so it participates in the cache identity: a
            // change to the build-wide ack set invalidates cached verdicts. (Degraded passes aren't
            // stored today, but keying it keeps the invariant true if that ever changes — and a normal
            // VERIFIED proof's identity simply gains a stable suffix.)
            appendProp(sb, ACK_UNMODELLED_PROP)
            return if (sb.isEmpty()) "" else "|$sb"
        }

        private fun appendProp(sb: StringBuilder, key: String) {
            val v = System.getProperty(key)
            if (!v.isNullOrBlank()) {
                sb.append(key).append('=').append(v.trim()).append(';')
            }
        }

        internal fun requestFor(entryClass: String, entryFunction: String, config: BmcProof?): BmcRequest =
                BmcRequest(
                        entryClass,
                        entryFunction,
                        System.getProperty("java.class.path"),
                        resolveUnwind(config),
                        config == null || config.unwindingAssertions,
                        resolveMaxStringLength(config),
                        config?.solver ?: "",
                        resolveTimeoutSeconds(config))

        /**
         * The solver a proof will actually run under: its per-proof `@BmcProof(solver=...)` override
         * if set, otherwise the build/`-Dbmc.solver` default ("" = jbmc's built-in MiniSat).
         */
        internal fun effectiveSolver(config: BmcProof?): String {
            val perProof = config?.solver
            if (!perProof.isNullOrBlank()) {
                return perProof.trim()
            }
            val prop = System.getProperty(SOLVER_PROP)
            return if (prop.isNullOrBlank()) "" else prop.trim()
        }

        internal fun resolveUnwind(config: BmcProof?): Int {
            if (config != null && config.unwind > 0) {
                return config.unwind
            }
            return intProp(UNWIND_PROP, DEFAULT_UNWIND)
        }

        /**
         * The symbolic-string length bound a proof will actually run under: its per-proof
         * `@BmcProof(maxStringLength=...)` override if `> 0`, otherwise the
         * build/`-Dbmc.maxStringLength` default (else [DEFAULT_MAX_STRING]).
         */
        internal fun resolveMaxStringLength(config: BmcProof?): Int {
            if (config != null && config.maxStringLength > 0) {
                return config.maxStringLength
            }
            return intProp(MAX_STRING_PROP, DEFAULT_MAX_STRING)
        }

        /**
         * The wall-clock budget a proof will actually run under: its per-proof
         * `@BmcProof(timeoutSeconds=...)` override if `> 0`, otherwise the
         * build/`-Dbmc.timeoutSeconds` default (else [DEFAULT_TIMEOUT] = no timeout).
         */
        internal fun resolveTimeoutSeconds(config: BmcProof?): Int {
            if (config != null && config.timeoutSeconds > 0) {
                return config.timeoutSeconds
            }
            return intProp(TIMEOUT_PROP, DEFAULT_TIMEOUT)
        }

        /**
         * Parse an int-valued `bmc.*` system property, or [fallback] when unset. A malformed
         * value FAILS LOUDLY — this tool's ethos is visible-over-silent, so a typo'd verification config
         * (e.g. `-Dbmc.unwind=1o`) must break the build, not silently run at the default.
         */
        private fun intProp(key: String, fallback: Int): Int {
            val v = System.getProperty(key)
            if (v.isNullOrBlank()) {
                return fallback
            }
            return v.trim().toIntOrNull()
                    ?: throw IllegalArgumentException(
                            "Invalid value for -D$key: \"$v\" is not an integer")
        }

        /**
         * Reclassify an engine-INFRASTRUCTURE failure as UNKNOWN. When the engine-run path
         * throws a non-verdict exception (the bundled engine couldn't be extracted, the jbmc process
         * couldn't be started, output couldn't be obtained, etc.) there is no counterexample — nothing was
         * proven wrong, the engine just couldn't run. Per the three-way verdict that is UNKNOWN, not
         * a refutation: we wrap it in a [BmcUndecidedError] whose message carries the `(UNKNOWN)`
         * tag (so the runner line prints `UNKNOWN`, not `REFUTED`) and the undecided framing,
         * keeping the original throwable as the cause for diagnosis. REFUTED stays reserved for a real
         * parsed JBMC counterexample (which never reaches here — it comes back as a `JbmcResult` and
         * is framed by [toError]).
         */
        internal fun engineInfraUndecided(engineId: String, entryFunction: String,
                                          cause: Throwable?): BmcUndecidedError {
            val sb = StringBuilder()
            sb.append(engineId.uppercase()).append(" could not decide ").append(entryFunction)
                    .append(" (UNKNOWN)\n")
            val detail = cause?.message ?: cause?.javaClass?.name
            sb.append("  ? engine infrastructure failed before a verdict: ")
                    .append(detail ?: "unknown error").append('\n')
            sb.append("    No counterexample: this is NOT a refutation — the engine could not be run (couldn't\n")
                    .append("    start / extract / produce output), so nothing was proven wrong. To get a\n")
                    .append("    decision, fix the infrastructure cause above (e.g. the bundled engine could not\n")
                    .append("    extract, or the jbmc process could not start) and re-run.")
            // A classpath-mirror / bytecode-rewrite preparation failure is the deterministic
            // MIRROR_FAILURE kind (a config/code issue in bmc4j's analysis-input prep); any other infra
            // failure has no table kind, so it stays kindless (still engine-infrastructure, still
            // rejected for expect = UNKNOWN).
            val kind = if (isMirrorFailure(cause)) UnknownKind.MIRROR_FAILURE else null
            val err = BmcUndecidedError(sb.toString().trimEnd(), true, kind)
            if (cause != null) {
                err.initCause(cause)
            }
            return err
        }

        /** True if [cause] (or anything in its cause chain) is a [ClasspathMirror.MirrorException] —
         *  the fail-loud signal of a mirror/rewrite preparation failure. Bounded, cycle-guarded. */
        private fun isMirrorFailure(cause: Throwable?): Boolean {
            var c = cause
            var hops = 0
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            while (c != null && hops < 16 && seen.add(c)) {
                if (c is org.bmc4j.engine.ClasspathMirror.MirrorException) {
                    return true
                }
                c = c.cause
                hops++
            }
            return false
        }

        internal fun toError(engineId: String, entryFunction: String,
                             result: JbmcResult, proofMethod: Method?): BmcVerificationError {
            val sb = StringBuilder()
            if (result.isUnknown) {
                // UNKNOWN: undecided within budget — NOT a refutation, so no counterexample.
                // Distinct exception type + message so a resource-exhaustion in CI is never mistaken for
                // "your code is wrong". Still fails the test: absence of a verdict is not a proof.
                sb.append(engineId.uppercase()).append(" could not decide ").append(entryFunction)
                        .append(" (UNKNOWN")
                val kind = result.undecidedKind
                if (kind != null) {
                    sb.append(": ").append(kind.name)
                            .append(if (kind.retryable) ", retryable" else ", not retryable")
                    if (persistedAcrossRetry(result.undecidedReason)) {
                        sb.append("; persisted across a retry")
                    }
                }
                sb.append(")\n")
                val reason = result.undecidedReason
                sb.append("  ? ").append(reason ?: "undecided within budget").append('\n')
                sb.append("    No counterexample: this is NOT a refutation — the engine ran out of budget or\n")
                        .append("    fell over before reaching a verdict. To get a decision, try one of:\n")
                        .append("      - raise unwind (the loop bound may be too high to solve in time):" +
                                " @BmcProof(unwind = ...)\n")
                        .append("      - give it more time: @BmcProof(timeoutSeconds = ...) or" +
                                " bmc { timeoutSeconds = N } / -Dbmc.timeoutSeconds\n")
                        .append("      - shrink the symbolic range with assume(...) (tighter bit-vector" +
                                " circuits solve far faster)\n")
                        .append("      - split the proof into smaller independent ones\n")
                        .append("      - add a method contract (@Requires/@Ensures) for the heavy callee so" +
                                " it's summarized, not re-explored\n")
                        .append("      - swap to an external SAT solver for string-free numeric proofs" +
                                " (bmc { externalSat = \"<dimacs-solver>\" }); note JBMC's SMT/z3 path is" +
                                " inert on this engine.")
                return BmcUndecidedError(sb.toString().trimEnd())
            }
            if (result.isVacuous) {
                // Vacuity: the proof's assumptions are unsatisfiable, so it verified over an
                // empty input domain and checked nothing. Surface that as its own verdict, not a "refuted".
                sb.append(engineId.uppercase()).append(" found ").append(entryFunction)
                        .append(" VACUOUS\n")
                sb.append("  ✗ ").append(BmcReachability.VACUOUS_MESSAGE).append('\n')
                sb.append("    no input satisfies every assume(...) — tighten or fix the assumptions ")
                        .append("(a contradictory pair, or a bound too small for the literals).")
                return BmcVerificationError(sb.toString().trimEnd())
            }
            sb.append(engineId.uppercase()).append(" refuted ").append(entryFunction).append('\n')
            for (v in result.violations) {
                sb.append("  ✗ ").append(v.description)
                if (v.file != null) {
                    sb.append("  (").append(shortFile(v.file!!)).append(':').append(v.line).append(')')
                }
                sb.append('\n')
                if (v.counterexample.isNotEmpty()) {
                    sb.append("    counterexample: ").append(v.counterexample.joinToString(", ")).append('\n')
                }
            }
            // Replay block: render the first violation's counterexample as concrete Java the
            // developer can paste into a scratch test and debug. Verified/UNKNOWN/vacuous never reach here.
            if (result.violations.isNotEmpty()) {
                val first = result.violations[0]
                val replay = ReplayRenderer.render(entryFunction, proofMethod, first)
                if (replay != null) {
                    sb.append(replay).append('\n')
                    // v2: also write a runnable @Test scratch file, and point at it.
                    val file = ReplayTestWriter.write(entryFunction, proofMethod, first)
                    if (file != null) {
                        sb.append("    replay test written to: ").append(file).append('\n')
                    }
                }
            }
            val error = BmcVerificationError(sb.toString().trimEnd())

            // Attach the synthesized stack trace of the first violation so IDEs and
            // reports point straight at the offending line.
            if (result.violations.isNotEmpty()) {
                val stack = result.violations[0].stack
                if (stack.isNotEmpty()) {
                    error.stackTrace = stack.toTypedArray()
                }
            }
            return error
        }

        /** True when an UNKNOWN's reason carries the engine-retry's persisted-flake annotation. */
        private fun persistedAcrossRetry(reason: String?): Boolean =
                reason != null && reason.contains("persisted across a retry")

        private fun shortFile(file: String): String {
            val slash = maxOf(file.lastIndexOf('/'), file.lastIndexOf('\\'))
            return if (slash >= 0) file.substring(slash + 1) else file
        }
    }
}
