package org.bmc4j.engine

/**
 * Identity of the bmc4j runtime *analysis semantics*, for the verdict cache.
 *
 * A cached green is only sound to replay if the bmc4j layer that would re-derive it is byte-for-byte
 * the same in what it tells the engine. bmc4j is not a thin launcher: before handing bytecode to JBMC it
 * **rewrites** it — String content ops, lambda/method-ref and pattern-switch `invokedynamic`
 * desugaring, config-constant baking, contract redirects, and the vacuity reachability marker (see
 * `JbmcBackend.prepareClasspath`). Those rewrites *are* the proof's semantics. If a release
 * changes any of them, a verdict computed under the old layer may no longer hold under the new one — so a
 * cache produced by an older runtime MUST NOT be trusted by a newer one.
 *
 * [IDENTITY] is therefore part of the cache key. It is the published artifact version (which
 * advances on every release) joined with a [SEMANTICS_REVISION] that a developer bumps whenever a
 * rewriter / model / verdict-derivation change lands *without* a version bump (e.g. mid-development
 * on a branch). Bumping either one invalidates every cached verdict — the soundness-safe direction
 * (over-invalidation is always acceptable; a stale green is a soundness bug).
 */
object Bmc4jVersion {

    /**
     * The published runtime artifact version. Read from the runtime jar's manifest
     * (`Implementation-Version`, stamped by the build from the release tag) when present, else
     * this fallback — the build's dev default, used when running straight from `build/classes`
     * with no manifest. Dev-build cache identity then leans on [SEMANTICS_REVISION] and the
     * cache key's classpath-content digest (which hashes the runtime's own classes when they're on
     * the analysis classpath), so a constant fallback here cannot serve a stale green.
     */
    private val ARTIFACT_VERSION = resolveArtifactVersion("0.0.1-local")

    /**
     * A monotonically-bumped tag for analysis-semantics changes that ship without an artifact-version
     * bump. Increment this whenever a change to the bytecode rewriters, the bundled models, or the
     * verdict-derivation logic could change a proof's verdict — or its harvested FACTS: r2 added the
     * residual-invokedynamic surfacing pass (stub-list change); r3 desugars enumSwitch, turning
     * previously-undecided enum pattern switches into real verdicts (pre-r3 entries must not be
     * served — the cache key hashes the ORIGINAL classpath, not the rewrite output); r4 relaxes
     * Kotlin proof-parameter prologues to assume(p != null) (KotlinParamBytecode) and starts
     * keying the rewrite-mirror cache by this identity too (ClasspathMirror), so a revision bump
     * now re-mirrors as well as re-verifying; r5 ports the rewrite passes + contract/model plumbing
     * to Kotlin (behavior-identical by review/tests, but a rewriter-code change re-mirrors on
     * principle); r6 extends the coroutine-LVT strip to drop the LocalVariableTable of ANY method
     * with a duplicate parameter-slot entry (not just invokeSuspend / Continuation-param methods),
     * so a previously-cached mirror that still carried a crash-triggering synthetic bridge
     * ($default) is re-stripped instead of re-served; r7 ports the four big bytecode-rewrite
     * engines (string/concat/record, switch, lambda, config) to Kotlin (identical behavior,
     * pinned by the unchanged Java test suite - but a rewriter-code change re-mirrors on
     * principle); r8 introduces per-proof class-level model slicing (ModelSlice): the analysis
     * classpath a verdict was computed over changes shape, so pre-slicing entries (and any
     * slicing-policy predecessor) must re-verify rather than be served to a sliced run.
     */
    private const val SEMANTICS_REVISION = "r8"

    /** The runtime semantics identity baked into every verdict-cache key. */
    @JvmField
    val IDENTITY: String = "$ARTIFACT_VERSION+$SEMANTICS_REVISION"

    private fun resolveArtifactVersion(fallback: String): String {
        try {
            val v = Bmc4jVersion::class.java.`package`.implementationVersion
            if (!v.isNullOrBlank()) {
                return v.trim()
            }
        } catch (ignored: RuntimeException) {
            // Fail-open to the fallback — the cache key just uses the hardcoded version.
        }
        return fallback
    }
}
