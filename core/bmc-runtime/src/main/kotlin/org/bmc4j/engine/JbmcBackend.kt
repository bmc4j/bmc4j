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
        val classpath = prepareClasspath(request, jbmcPath)
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
                    request.classpath)
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
        return result
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

        /** All the JBMC-specific classpath preparation (contracts, bytecode rewrites, model jars). */
        fun prepareClasspath(request: BmcRequest, jbmcPath: String): String {
            // Method contracts: rewrite contracted call sites to their replace-stubs; a
            // generated enforce proof is excluded as a caller so it sees the real body (modular enforce).
            var classpath = applyContracts(request)
            // Fold the consumer's own src/bmcModel output into the SAME classpath the rewrite chain runs
            // over, so a user-authored model is desugared exactly like the proof/test classes: String
            // content ops -> BmcStrings, concat / record / typeSwitch / lambda invokedynamic desugared,
            // integer Math.* redirected to BmcMath. Without this a faithful model (real-looking Java) that
            // internally uses String.equals/concat, a lambda, a pattern switch, or Math.floorDiv would be
            // analysed unsound, silently — the more realistic the model, the worse. The ReachabilityBytecode
            // pass only touches @BmcProof methods, so it's a harmless no-op on models. A rewrite failure on
            // a user model throws (ClasspathMirror's fail-loud contract) -> UNKNOWN, never a false green.
            // It is prepended (kept first below) so the override still wins by classpath order over the
            // bundled models, the Kotlin models, and JBMC's core-models.
            val userModels = System.getProperty(USER_MODELS_PROP)
            // The plugin passes the bmcModel source set's output, which can be several class dirs (e.g. a
            // Java dir AND a Kotlin dir) joined by the path separator — count them so the Kotlin models can
            // later be inserted AFTER all of them (the user override must still win by classpath order).
            var userModelEntries = 0
            if (!userModels.isNullOrBlank()) {
                classpath = userModels + File.pathSeparator + classpath
                userModelEntries = userModels.split(File.pathSeparator).count { it.isNotEmpty() }
            }
            // Strip the LVT from coroutine methods (JBMC 6.9.0 aborts on multi-suspension state machines).
            classpath = CoroutineBytecode.strip(classpath)
            // Sound String content ops (JBMC's own String.equals is unsound).
            classpath = StringBytecode.rewrite(classpath)
            // Desugar lambda / method-reference invokedynamic to generated functional-interface classes.
            classpath = LambdaBytecode.rewrite(classpath)
            // Desugar pattern-matching switch invokedynamic (SwitchBootstraps.typeSwitch) to a sound
            // instanceof/equals chain (JBMC links the indy to an unconstrained result otherwise).
            classpath = SwitchBytecode.rewrite(classpath)
            // LAST indy pass: any invokedynamic still standing (enumSwitch, an unhandled typeSwitch
            // label shape, record toString with a reference component, future bootstraps) would be
            // SILENTLY linked to an unconstrained result by JBMC — no opaque-symbol message, invisible
            // to the stub policy. Replace each with a call to a deliberately-bodiless marker so the
            // same trust surfaces through the normal nondet-stub channel (footnote / strictStubs).
            classpath = ResidualIndyBytecode.rewrite(classpath)
            // Redirect the integer Math.* methods JBMC stubs to nondet (floorDiv/floorMod/*Exact/
            // toIntExact/absExact/abs) to the sound BmcMath; sqrt/pow/trig (modeled) pass through.
            classpath = MathBytecode.rewrite(classpath)
            // Pin Bmc.*FromEnv/*FromProperty("KEY") to this run's real value (baked in as a constant).
            classpath = ConfigBytecode.rewrite(classpath)
            // Kotlin symbolic parameters: inside @BmcProof methods only, turn the kotlinc
            // checkNotNullParameter prologue into assume(p != null) — the proof ranges over the inputs
            // the Kotlin type system admits instead of spuriously refuting on p = null. Interior calls
            // keep throwing; -Dbmc.kotlinNullableParams=true restores the honest-JVM prologue.
            classpath = KotlinParamBytecode.rewrite(classpath)
            // Domain split: when this request is ONE derived run of a domainSplit proof, rewrite the
            // entry method's domainSplit/slice markers for that run — a slice's injected assume, or the
            // cover obligation (overall => union of slices). Runs AFTER the desugar passes so a marker
            // condition that uses strings/concat/lambdas is already sound, and BEFORE the reachability
            // pass so the cover run's injected return gets a vacuity marker and the markers are gone
            // before vacuity injection. A no-op for an ordinary proof (domainSplitRun == null).
            val splitRun = request.domainSplitRun
            if (splitRun != null) {
                classpath = DomainSplitBytecode.rewrite(
                        classpath, request.entryClass, entryMethodName(request.entryFunction), splitRun)
            }
            // Vacuity guard: inject a reachability marker before every return of each
            // @BmcProof / enforce-proof. Runs LAST over the .class dirs so the marker lands in the final
            // proof bodies and no earlier desugar can strip it; the verdict logic flags a proof whose
            // every normal exit is unreachable (unsatisfiable assumptions) instead of passing it vacuously.
            classpath = ReachabilityBytecode.rewrite(classpath)
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
            ContractPurityAudit.auditRelevant(
                    manifest, request.entryClass, entryMethodName(request.entryFunction),
                    request.classpath, classpath)
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
            return ModelSlice.sliceForCone(classpath, request.entryClass)
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

        /** The method-name half of a `Class.method` entry-function string. */
        fun entryMethodName(entryFunction: String): String {
            val dot = entryFunction.lastIndexOf('.')
            return if (dot >= 0) entryFunction.substring(dot + 1) else entryFunction
        }

        /** Apply the contract rewriter to the request's classpath (no-op without a manifest). */
        fun applyContracts(request: BmcRequest): String {
            val contracts = ContractManifest.readFromClasspath(request.classpath)
            if (contracts.isEmpty) {
                return request.classpath
            }
            // A generated enforce proof is excluded as a caller so its direct call to the
            // method-under-test stays real; every other proof is a replace proof (rewrite fully).
            val entryInternal = request.entryClass.replace('.', '/')
            val excludeCaller =
                    if (contracts.enforceProofClasses().contains(entryInternal)) entryInternal else null
            return ContractRewriter.rewrite(request.classpath, contracts.redirects(), excludeCaller)
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
