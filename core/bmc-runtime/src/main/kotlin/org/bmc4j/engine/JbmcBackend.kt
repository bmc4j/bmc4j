package org.bmc4j.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * The default backend: JBMC (diffblue/cbmc's Java bytecode model checker).
 *
 * Owns all JBMC-specific preparation of the analysis input:
 * - resolve the engine binary — an explicit `-Dbmc.jbmc` path, else the
 *   bundled engine extracted from the classpath;
 * - strip the `LocalVariableTable` from coroutine methods (works around a
 *   JBMC 6.9.0 invariant on multi-suspension state machines);
 * - prepend the bundled Kotlin runtime models;
 * - append `core-models.jar` (JDK class hierarchy for dynamic-cast checks
 *   and thread analysis).
 */
class JbmcBackend : VerificationBackend {

    override fun id(): String = "jbmc"

    /**
     * Engine identity for the verdict cache. An explicit `-Dbmc.jbmc` binary is pinned
     * by a content hash of that file (so swapping it for a different binary invalidates cached verdicts);
     * otherwise the bundled engine's version string identifies it. Falls back to [id] if neither
     * is resolvable — fail-open: a coarser identity only over-invalidates.
     */
    override fun engineIdentity(): String {
        try {
            val override = System.getProperty(JBMC_PROP)
            if (!override.isNullOrBlank()) {
                val bin = Path.of(override)
                if (Files.isRegularFile(bin)) {
                    return "jbmc-bin:" + VerdictCache.fileDigest(bin)
                }
                return "jbmc-bin-path:$override" // can't read it: still distinguish by path
            }
            val version = BundledEngine.version()
            return if (version != null) "jbmc-bundled:$version" else id()
        } catch (e: RuntimeException) {
            return id()
        }
    }

    override fun verify(request: BmcRequest): JbmcResult {
        val jbmcPath = resolveJbmc()
        // @BmcProfile: time bmc4j's OWN pre-engine pipeline (the classpath/bytecode prep below) so the
        // breakdown reads as (our prep) + (engine), never just the engine. Allocated only when profiling
        // is on, so the normal path pays nothing; threaded into the engine run so the parsed profile can
        // surface these harness-measured timings alongside jbmc's engine-reported phases.
        val timing = if (request.profile) PipelineTiming() else null
        val classpath = prepareClasspath(request, jbmcPath, timing)
        // Hold one JVM-wide jbmc permit for the lifetime of the engine process. This is the single
        // chokepoint that bounds TOTAL concurrent jbmc processes to the configured parallelism, whether
        // they come from independent @BmcProof methods (already concurrency-limited by the JUnit pool)
        // or from one domainSplit proof fanning its N+1 derived runs across the same budget. Classpath
        // preparation (the bytecode rewrites above) is CPU/heap-light relative to the engine and runs
        // unpermitted so the gate covers exactly the heavy process, never the prep.
        val result = JbmcConcurrency.withPermit {
            Jbmc(jbmcPath).run(
                    request.entryClass, request.entryFunction, classpath,
                    request.unwind, request.unwindingAssertions,
                    request.maxStringLength, request.solver,
                    request.timeoutSeconds, request.externalSatPath,
                    // The ORIGINAL (un-rewritten) test classpath drives witness rendering: it carries the
                    // consumer's own class output dirs with full debug info, so the parser can tell the
                    // user's declared inputs (kept) from engine synthetics / library frames (dropped).
                    request.classpath,
                    // @BmcProfile: when on, the driver parses a per-stage performance breakdown from the
                    // verbose stream it already captures and attaches it to the result (additive; the
                    // verdict is unchanged).
                    request.profile,
                    // The bmc4j-pipeline timings collected above + the request, so the driver can fold our
                    // harness-measured prep phases (and the engine wall-clock it measures) into the profile.
                    timing?.snapshot())
        }
        // Positive floor for stub detection: a green with an EMPTY harvest is only trustworthy if
        // the opaque-symbol parse provably works against THIS engine — a format drift in a
        // -Dbmc.jbmc engine empties the harvest silently, which would strip honesty footnotes and
        // disarm strictStubs with nothing visible anywhere. A non-empty harvest proves the parse by
        // existing; the empty case is vouched for by a one-time canary (memoized + disk-marked),
        // which throws an engine-infrastructure UNKNOWN when it can't. Non-green verdicts don't
        // consult stub facts, so they pass through unfloored.
        if (result.isVerified && result.stubbedMethods.isEmpty()) {
            StubHarvestFloor.ensure(jbmcPath, engineIdentity())
        }
        // Attach the assumed output-contracts this proof installed as a parallel FACT (like
        // stubbedMethods): each "Owner.method" the verdict interpreter surfaces so a VERIFIED reached
        // under an assumeEvery/assumeStable is flagged NOT unconditional. Re-decoded from the original
        // classpath (cheap: one class read; install() itself is memoized).
        return result.withAssumedContracts(
                AssumeContractBytecode.displays(
                        request.entryClass, request.entryFunction, request.classpath))
    }

    private companion object {

        const val JBMC_PROP = "bmc.jbmc"

        /** Consumer model classes dir (src/bmcModel output), set by the Gradle plugin. */
        const val USER_MODELS_PROP = "bmc.userModels"

        fun resolveJbmc(): String {
            val jbmcPath = System.getProperty(JBMC_PROP)
            if (jbmcPath.isNullOrBlank()) {
                return BundledEngine.extract()
            }
            return jbmcPath
        }

        /** Gradle-provided pre-mirrored classpath directory, set by the plugin's mirror task. When
         *  present, the run-wide hoistable passes — the six desugars (coroutine-LVT/String/lambda/switch/
         *  residual-indy/Math) plus the Config bake, KotlinParam and Reachability — were already applied to
         *  the analysis classpath (including the consumer's own compiled output and bmcModel output) by a
         *  cacheable Gradle task, so the runtime substitutes the mirrored entries and skips those passes;
         *  only the per-proof tail (contracts, domain split, purity audit, model slice) runs in-JVM. */
        const val GRADLE_MIRROR_PROP = "bmc.gradleMirrorDir"

        /** All the JBMC-specific classpath preparation (contracts, bytecode rewrites, model jars). When
         *  [timing] is non-null (a `@BmcProfile` proof), each substantial pass is wrapped in a wall-clock
         *  so the profile can show where bmc4j's own prep time went, distinct from the engine's. The work
         *  itself is byte-for-byte identical with or without timing — the wrapper is transparent. */
        fun prepareClasspath(request: BmcRequest, jbmcPath: String,
                             timing: PipelineTiming? = null): String {
            // Time [body] under [label] when profiling, else run it directly (zero overhead off the
            // profiled path). Inline-ish helper so each pass is a one-line `t("label") { ... }` wrap.
            fun <T> t(label: String, body: () -> T): T =
                    if (timing != null) timing.time(label, body) else body()
            // If the Gradle plugin pre-mirrored the analysis classpath, substitute each covered entry for
            // its already-rewritten counterpart and run the HOISTABLE passes on the rest IN-JVM. The mirror
            // task takes the consumer's own compiled output AND the bmcModel output as inputs too, so on a
            // normal Gradle run every entry is covered; an entry the task did NOT cover (e.g. a non-standard
            // class dir, or a stale / identity- / config-mismatched mirror falling back) is rewritten here
            // so it is never analysed unsound. The work is split so every entry is rewritten exactly once
            // (plugin OR in-JVM). The hoisted set is `6-desugar + Config + KotlinParam + Reachability +
            // NondetTag`:
            // the desugars + Reachability are pure functions of the bytecode, KotlinParam reads one run-wide
            // flag (a task @Input), and the Config bake's resolved values are recorded in the manifest and
            // re-validated here, so a stale mirror is never trusted. request.classpath itself (the
            // verdict-cache key + witness source) is NEVER substituted — only this internal working
            // classpath is.
            val mirrorDir = System.getProperty(GRADLE_MIRROR_PROP)
            // The consumer's bmcModel output, passed by the plugin (may be several class dirs); needed both
            // for the config-match gate (the worker baked Config over the FULL union deps+project+bmcModel,
            // so the re-validation must resolve config over the SAME union = request.classpath + userModels)
            // and below to fold the models into the classpath.
            val userModels = System.getProperty(USER_MODELS_PROP)
            // Trust the plugin mirror only when it exists AND its baked Config matches what this run
            // resolves over the full union — a config flip since the mirror was built makes the baked
            // constants stale, so we fall back to a full in-JVM rewrite (sound; the task re-runs next time
            // since config is one of its @Inputs). The identity/Kotlin-param match is checked per entry in
            // coveredEntries/substitute; the config match is one classpath-wide gate here.
            val preMirrored = !mirrorDir.isNullOrBlank()
                    && GradleClasspathMirror.configMatches(unionClasspath(request.classpath, userModels),
                            Path.of(mirrorDir))
            // Loud guard against a SILENT mirror miss: when the mirror is trusted (config + identity match)
            // but matches NONE of the analysis classpath, that is almost certainly a path-format mismatch
            // (e.g. the Windows backslash spelling that silently disabled the mirror for several releases),
            // not a legitimately-uncovered classpath. Warn once per mirror; the run stays sound either way
            // (every uncovered entry is still rewritten in-JVM below), so this never fails a run -- it only
            // makes the degradation visible. Checked over the FULL union the worker baked over.
            if (preMirrored) {
                GradleClasspathMirror.warnIfMirrorMatchedNothing(
                        unionClasspath(request.classpath, userModels), Path.of(mirrorDir))
            }
            val analysisClasspath =
                    if (preMirrored) {
                        // Pre-mirrored: substitute each covered entry for its already-rewritten mirror
                        // (cheap, mostly path bookkeeping) — labelled `mirror`. Any uncovered entry is
                        // rewritten in-JVM inside here (the rare fallback).
                        t("mirror") { hoistableWithGradleMirror(request.classpath, Path.of(mirrorDir)) }
                    } else {
                        // No usable plugin mirror: the whole classpath gets the hoistable passes in-JVM below.
                        request.classpath
                    }
            // Method contracts: rewrite contracted call sites to their replace-stubs; a
            // generated enforce proof is excluded as a caller so it sees the real body (modular enforce).
            var classpath = t("contracts") { applyContracts(request, analysisClasspath) }
            // Per-proof ASSUMED output-contracts (Bmc.assumeEvery / assumeStable): read the proof's
            // marker call sites, decode each (reference + predicate) STATICALLY from its
            // LambdaMetafactory bootstrap handles on the ORIGINAL pre-rewrite classpath (the indys are
            // still present there), shadow each target with a constrained-nondet stub, and redirect its
            // call sites — including those in <clinit> and uncontrolled callees. A no-op when the proof
            // declares none. The decoded set is also surfaced on the verdict (below) and its predicates
            // are purity-audited (alongside the contract audit).
            val assumeContracts = t("assume-contracts") {
                AssumeContractBytecode.decode(
                        request.classpath, request.entryClass, entryMethodName(request.entryFunction))
            }
            if (assumeContracts.isNotEmpty()) {
                classpath = t("assume-contracts") {
                    AssumeContractBytecode.install(classpath, request.entryClass,
                            entryMethodName(request.entryFunction), assumeContracts)
                }
            }
            // Fold the consumer's own src/bmcModel output into the SAME classpath the rewrite chain runs
            // over, so a user-authored model is rewritten exactly like the proof/test classes (String
            // content ops -> BmcStrings, concat / record / typeSwitch / lambda invokedynamic desugared,
            // integer Math.* -> BmcMath, and — harmlessly, since models carry no @BmcProof — KotlinParam /
            // Reachability). Without this a faithful model (real-looking Java) that internally uses
            // String.equals/concat, a lambda, a pattern switch, or Math.floorDiv would be analysed unsound,
            // silently. A rewrite failure on a user model throws (ClasspathMirror's fail-loud contract) ->
            // UNKNOWN, never a false green. It is prepended (kept first below) so the override still wins by
            // classpath order over the bundled models, the Kotlin models, and JBMC's core-models. When the
            // plugin pre-mirrored, the bmcModel output is one of the task's covered inputs, so it is
            // SUBSTITUTED for its mirror here rather than rewritten in-JVM.
            // The plugin passes the bmcModel source set's output, which can be several class dirs (e.g. a
            // Java dir AND a Kotlin dir) joined by the path separator — count them so the Kotlin models can
            // later be inserted AFTER all of them (the user override must still win by classpath order).
            var userModelEntries = 0
            if (!userModels.isNullOrBlank()) {
                // With a plugin mirror the bmcModel output is a covered task input — substitute it for its
                // already-rewritten mirror; an uncovered entry (fallback) is rewritten in-JVM. With no
                // plugin mirror, run the hoistable passes on it directly. Either way it ends up
                // rewritten exactly once.
                val foldedUserModels = t("user-models") {
                    if (preMirrored) hoistableWithGradleMirror(userModels, Path.of(mirrorDir))
                    else applyHoistablePasses(userModels)
                }
                classpath = foldedUserModels + File.pathSeparator + classpath
                userModelEntries = foldedUserModels.split(File.pathSeparator).count { it.isNotEmpty() }
            }
            // The hoistable passes (6 desugars + Config bake + KotlinParam + Reachability). When the Gradle
            // plugin pre-mirrored the classpath, every entry was already rewritten — covered entries by the
            // plugin (Config baked there from the forwarded run config, re-validated by the manifest), the
            // uncovered ones by hoistableWithGradleMirror above, and the user models just above — so skip
            // the global block; otherwise run it here over the whole classpath. The Config bake pins
            // Bmc.*FromEnv/*FromProperty("KEY") to this run's real value as a constant; it is baked at the
            // SAME pipeline position whether by the plugin worker (with the forwarded properties + the
            // worker's env) or in-JVM (with the test JVM's properties + env), which match when the config is
            // unchanged. Its verdict-relevant values are also folded into the verdict-cache key
            // (VerdictCache.resolvedConfig), which over-invalidates on any config change.
            if (!preMirrored) {
                classpath = applyHoistablePasses(classpath, timing)
            }
            // Domain split: when this request is ONE derived run of a domainSplit proof, rewrite the
            // entry method's domainSplit/slice markers for that run — a slice's injected assume, or the
            // cover obligation (overall => union of slices). Runs AFTER the desugar passes so a marker
            // condition that uses strings/concat/lambdas is already sound. A no-op for an ordinary proof
            // (domainSplitRun == null).
            val splitRun = request.domainSplitRun
            if (splitRun != null) {
                classpath = t("domain-split") {
                    DomainSplitBytecode.rewrite(
                        classpath, request.entryClass, entryMethodName(request.entryFunction), splitRun)
                }
                // The cover run injects a NEW return into the entry method AFTER the (hoisted) Reachability
                // pass ran, so that injected return carries no vacuity marker yet. Re-run Reachability here
                // — AFTER the split rewrite — so the injected return gets its marker. Reachability is
                // idempotent on returns it already replaced (an existing marker throw is no longer a
                // `return`), so re-running over the already-marked entry only catches the freshly-injected
                // one. Scoped to the split case: an ordinary proof's returns were all marked by the hoisted
                // pass, so no in-JVM Reachability scan is paid for the common (non-split) proof.
                classpath = t("domain-split") { ReachabilityBytecode.rewrite(classpath) }
            }
            // Add the bundled Kotlin models (clean Intrinsics / coroutine runtime); harmless for Java.
            // It must sit AFTER the consumer's user models so a user model still shadows first: the user
            // models are the leading entries of the (now-rewritten) classpath, so splice the Kotlin models
            // in right after them; with no user models, simply prepend.
            val kotlinModels = BundledKotlinModels.extractRoot()
            if (kotlinModels != null) {
                classpath = spliceAfter(classpath, userModelEntries, kotlinModels)
            }
            // Append the JDK models (class hierarchy for dynamic-cast checks + thread analysis).
            val coreModels = coreModelsNextTo(jbmcPath)
            if (coreModels != null) {
                classpath = classpath + File.pathSeparator + coreModels
            }
            // Purity audit (soundness): a contract redirects every call site of its target to a stub
            // that summarizes only the RETURN value, so any caller-observable side effect of the body
            // is silently dropped — and the enforce-proof still passes (it checks @Ensures, not
            // purity). Certify each contract this proof CONSUMES provably pure-by-construction against
            // the fully prepared, model-bearing classpath (where JDK calls already resolve to our model
            // bytecode), or fail the build LOUD via ContractPurityError. Done after the desugar passes
            // so the walker sees the same sound bytecode JBMC will. Scoped to the redirects relevant to
            // THIS proof (an enforce-proof's own target; a replace-proof's reachable redirected call
            // sites) so an impure contract fails exactly the proofs that would unsoundly reuse it, not
            // every proof in the module. A no-op without contracts.
            val manifest = ContractManifest.readFromClasspath(request.classpath)
            t("purity-audit") {
                ContractPurityAudit.auditRelevant(
                        manifest, request.entryClass, entryMethodName(request.entryFunction),
                        request.classpath, classpath)
            }
            // NB: assumed-contract predicates (assumeEvery / assumeStable) are deliberately NOT
            // purity-audited. Unlike an annotation contract — whose predicate becomes a reusable summary
            // spliced into callers — an assumed contract is an explicit, per-proof, user-owned assertion
            // surfaced on the verdict ("VERIFIED under assumed contract X"). An effectful predicate is a
            // legitimate richer micro-model (model the dependency's side effects, not just its output),
            // and the over-approximation soundness (fresh-per-call nondet) holds regardless of the
            // predicate's purity. The user owns it; we don't gate it.
            // Exception-message elision: drop the construction of a thrown exception's message when the
            // proof's reachable cone observes NO exception message (AUTO's coarse soundness gate), or
            // when the proof asks for it explicitly (ON, a user-asserted override). This makes a proof
            // over a function that builds an expensive dynamic error message (e.g. a byte->String
            // materialization on a dead overflow branch) tractable: the message is never read, so dropping
            // its computation can't change the verdict, only removes the symbolic cost that poisoned it.
            // Runs AFTER the desugar passes (so a message-building concat/byte-decode is in the sound
            // StringBuilder/BmcStrings form we drop) and AFTER the purity audit (which walks the full
            // prepared classpath), but BEFORE the model slice so the engine analyses the elided bytecode.
            // OFF is a no-op; the gate fails toward NOT eliding (worst case: no speed-up, never a false
            // green). The forced-elision footnote (ON) is surfaced on the verdict by the proof extension.
            classpath = t("exception-message-elision") {
                ExceptionMessageElision
                        .apply(classpath, request.entryClass, entryMethodName(request.entryFunction),
                                request.removeExceptionMessages).classpath
            }
            // Per-proof model slicing (LAST): hand the engine only the classes in this proof's
            // reachable cone, so unrelated model growth no longer taxes every proof. Computes the cone
            // over the FULLY-REWRITTEN classpath (the bytecode JBMC actually analyses) and prunes its
            // directory entries to it — so every class the rewrite chain injects or redirects to (lambda
            // impls, contract stub/enforce classes, switch helpers, residual-indy markers) is in the
            // keep-set by construction, never pruned into a wrong verdict. (The verdict-cache cone digest
            // keys on the ORIGINAL pre-rewrite classpath; its keying semantics are unchanged — slicing
            // only needs its keep-set to cover the reachable-at-analysis classes, which the rewritten
            // cone guarantees.) A proof whose cone can't be bounded (reflection / unknown indy / entry
            // off classpath) is returned UNCHANGED: it still sees the whole surface, so slicing never
            // under-feeds a fallback proof. Done after the purity audit so the audit still walks the full
            // prepared classpath. A slice failure fails safe to the unsliced classpath.
            return t("model-slice") { ModelSlice.sliceForCone(classpath, request.entryClass) }
        }

        /**
         * Insert [insertion] into [classpath] after the first [afterEntries] entries
         * (each a path-separator-delimited element). With `afterEntries == 0` this prepends. Used to
         * place the bundled Kotlin models right after the consumer's user-model entries, so user models
         * keep their shadowing precedence while the Kotlin models still precede everything else.
         */
        fun spliceAfter(classpath: String, afterEntries: Int, insertion: String): String {
            if (afterEntries <= 0) {
                return insertion + File.pathSeparator + classpath
            }
            val entries = classpath.split(File.pathSeparator)
            val sb = StringBuilder()
            for (i in entries.indices) {
                if (i == afterEntries) {
                    sb.append(insertion).append(File.pathSeparator)
                }
                sb.append(entries[i])
                if (i < entries.size - 1) {
                    sb.append(File.pathSeparator)
                }
            }
            // Fewer entries than expected (defensive): append rather than drop the insertion.
            if (afterEntries >= entries.size) {
                sb.append(File.pathSeparator).append(insertion)
            }
            return sb.toString()
        }

        /** The union of the analysis classpath and the consumer's bmcModel output — the SAME set the
         *  Gradle mirror worker baked Config over (deps + project + bmcModel), so the config-match
         *  re-validation resolves config over identical scope. Mirrors VerdictCache.resolvedConfig's
         *  classpath+userModels union. A blank userModels yields just the classpath. */
        fun unionClasspath(classpath: String, userModels: String?): String =
                if (userModels.isNullOrBlank()) classpath
                else classpath + File.pathSeparator + userModels

        /** The method-name half of a `Class.method` entry-function string. */
        fun entryMethodName(entryFunction: String): String {
            val dot = entryFunction.lastIndexOf('.')
            return if (dot >= 0) entryFunction.substring(dot + 1) else entryFunction
        }

        /**
         * The six environment-INDEPENDENT desugar passes, in order: coroutine-LVT strip, String content
         * ops, lambda/method-ref indy, pattern-switch indy, residual indy marker, integer Math.*. These
         * are pure functions of the input bytes (no env / property / per-proof state), which is exactly
         * why the Gradle plugin can pre-compute them as a cacheable task. [GradleClasspathMirror.mirror]
         * runs THIS SAME chain in the plugin worker, so the pre-mirrored bytecode is byte-for-byte what
         * this produces.
         */
        fun applyDesugarPasses(classpath: String): String {
            // The six passes — coroutine-LVT strip, sound String content ops, lambda/method-ref indy
            // desugar, pattern-switch (typeSwitch) indy desugar, residual-indy marker, integer Math.*
            // redirect — run as ONE fused walk: each class is inflated once, run through every pass in
            // order in-memory (generated lambda classes threaded through their downstream passes), and
            // deflated once. The fused output is byte-for-byte what the old sequential per-pass mirrors
            // produced (DesugarFusionEquivalenceTest pins it), so soundness is unchanged; only the cold
            // codec/I-O cost collapses (six inflate/deflate round-trips per class become one).
            return ClasspathMirror.mirrorAll(classpath)
        }

        /**
         * The full ENVIRONMENT-INDEPENDENT prefix of the rewrite chain — `6-desugar -> Config ->
         * KotlinParam -> Reachability -> NondetTag` — in the SAME order, with the SAME pass entry points,
         * that [GradleClasspathMirror.mirror] runs in the plugin worker. So the bytecode this produces
         * in-JVM (for an uncovered entry, or with no plugin mirror at all) is byte-for-byte what the
         * cacheable task produces for a covered one. The six desugars + Reachability + NondetTag are pure
         * functions of the bytecode; KotlinParam additionally reads the run-wide `bmc.kotlinNullableParams`
         * flag; Config additionally bakes the run's env/property config — but all are run-wide (not
         * per-proof), which is why they can be hoisted into the cacheable task (Config keyed by the manifest
         * config re-validation).
         */
        fun applyHoistablePasses(classpath: String, timing: PipelineTiming? = null): String {
            fun <T> t(label: String, body: () -> T): T =
                    if (timing != null) timing.time(label, body) else body()
            var cp = t("desugar") { applyDesugarPasses(classpath) }
            cp = t("config") { ConfigBytecode.rewrite(cp) }
            cp = t("kotlin-param") { KotlinParamBytecode.rewrite(cp) }
            cp = t("reachability") { ReachabilityBytecode.rewrite(cp) }
            // Explicit USER-nondet witness tag: inject a verification-neutral Bmc.recordNondet("name",
            // value) after each user Bmc.any* store so a counterexample carries the input robustly. Pure
            // bytecode (no env/property/per-proof state), env-independent like the rest of this chain, so
            // it is hoisted into the cacheable Gradle mirror too (GradleClasspathMirror.mirror). It only
            // touches user-origin classes' Bmc.any* call sites — disjoint from Config (Bmc.*From* sites),
            // KotlinParam (the non-null prologue) and Reachability (returns) — so it commutes with them
            // and is byte-identical whether run here or in the mirror.
            return t("nondet-tag") { NondetTagBytecode.rewrite(cp) }
        }

        /**
         * Build the fully hoistable-rewritten working classpath when the Gradle plugin provided a
         * pre-mirror at [mirrorDir]: each ORIGINAL entry covered by the plugin's manifest (identity- AND
         * config-matched) is swapped for its mirrored counterpart (`6-desugar + Config + KotlinParam +
         * Reachability` already applied, owned + cached by Gradle), and every entry the plugin did NOT
         * cover is rewritten here in-JVM with the identical pass chain. Order is preserved. Every entry
         * ends up rewritten exactly once; skipping an uncovered one would analyse it unsound (the bug this
         * guards against).
         */
        fun hoistableWithGradleMirror(classpath: String, mirrorDir: Path): String {
            val covered = GradleClasspathMirror.coveredEntries(mirrorDir)
            val entries = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }
            // Membership is by CANONICAL path: the covered keys are canonical, but a java.class.path entry
            // can spell the same location differently (Gradle's test worker doubles backslashes on Windows),
            // so a raw compare would treat every entry as uncovered and pointlessly re-rewrite the whole
            // classpath in-JVM.
            fun isCovered(e: String) = GradleClasspathMirror.canonicalKey(e) in covered
            // Rewrite all the UNCOVERED entries together as one sub-classpath (1:1, order-preserving), so
            // they can be zipped back into their original positions below.
            val uncovered = entries.filter { !isCovered(it) }
            val rewrittenUncovered =
                    if (uncovered.isEmpty()) {
                        emptyList()
                    } else {
                        applyHoistablePasses(uncovered.joinToString(File.pathSeparator))
                                .split(File.pathSeparator).filter { it.isNotEmpty() }
                    }
            // Mismatch (an uncovered entry was dropped/added by the passes) would desync the zip-back —
            // fail loud rather than silently mis-map an entry to the wrong mirrored bytecode.
            if (rewrittenUncovered.size != uncovered.size) {
                throw IllegalStateException(
                        "bmc4j in-JVM hoistable rewrite produced ${rewrittenUncovered.size} entries " +
                                "for ${uncovered.size} uncovered inputs; refusing to mis-map the classpath.")
            }
            val substituted = GradleClasspathMirror.substitute(classpath, mirrorDir)
                    .split(File.pathSeparator).filter { it.isNotEmpty() }
            // substitute() preserves order and count over non-empty entries, so it lines up with [entries].
            if (substituted.size != entries.size) {
                throw IllegalStateException(
                        "bmc4j mirror substitution produced ${substituted.size} entries for " +
                                "${entries.size} inputs; refusing to mis-map the classpath.")
            }
            val out = StringBuilder()
            var u = 0
            for (i in entries.indices) {
                if (i > 0) {
                    out.append(File.pathSeparator)
                }
                if (isCovered(entries[i])) {
                    out.append(substituted[i]) // plugin-mirrored counterpart
                } else {
                    out.append(rewrittenUncovered[u++]) // rewritten in-JVM
                }
            }
            return out.toString()
        }

        /** Apply the contract rewriter to [analysisClasspath] (no-op without a manifest). The contract
         *  MANIFEST is always read from the ORIGINAL request classpath (a resource, never rewritten); the
         *  call-site rewrite is applied to [analysisClasspath], which may be the plugin's pre-mirrored
         *  classpath. */
        fun applyContracts(request: BmcRequest, analysisClasspath: String): String {
            val contracts = ContractManifest.readFromClasspath(request.classpath)
            if (contracts.isEmpty) {
                return analysisClasspath
            }
            // A generated enforce proof is excluded as a caller so its direct call to the
            // method-under-test stays real; every other proof is a replace proof (rewrite fully).
            val entryInternal = request.entryClass.replace('.', '/')
            val excludeCaller =
                    if (contracts.enforceProofClasses().contains(entryInternal)) entryInternal else null
            return ContractRewriter.rewrite(analysisClasspath, contracts.redirects(), excludeCaller)
        }

        /** core-models.jar sits next to the jbmc binary (`<home>/lib/core-models.jar`). */
        fun coreModelsNextTo(jbmcPath: String): String? {
            val bin = Path.of(jbmcPath).parent
            if (bin?.parent == null) {
                return null
            }
            val coreModels = bin.parent.resolve("lib").resolve("core-models.jar")
            return if (Files.isRegularFile(coreModels)) coreModels.toString() else null
        }
    }
}
