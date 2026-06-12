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
     * slicing-policy predecessor) must re-verify rather than be served to a sliced run;
     * r9 removes the per-proof `concurrent` flag (and its `--java-threading` emission) from the
     * request and the cache key — the key no longer hashes that component, so a key computed by an
     * r8 runtime (which folded `concurrent` in) must not be served to an r9 run;
     * r10 hoists the six environment-independent desugar passes (coroutine-LVT/String/lambda/switch/
     * residual-indy/Math) into an optional cacheable Gradle mirror task: the mirrored bytecode is
     * byte-for-byte identical, but when the plugin provides it the contract call-site rewrite now runs
     * on the already-desugared classpath (order-commutative for disjoint call sites) — re-mirror on
     * principle, the version-stamped key guarantees a pre-r10 mirror is never served to an r10 run;
     * r11 extends the cacheable Gradle mirror to hoist the FULL run-wide rewrite prefix — the Config bake,
     * KotlinParam and Reachability on top of the six desugars — and to cover the consumer's own compiled
     * output + bmcModel output. A covered entry now reaches the test JVM with `6-desugar + Config +
     * KotlinParam + Reachability` already applied; the worker bakes Config from the plugin-forwarded run
     * config and records the resolved config in the manifest, which the runtime re-validates (and falls
     * back to a full in-JVM rewrite on any mismatch) so a stale config bake is never served. Covered
     * entries are matched by CANONICAL path (the test worker can spell java.class.path entries differently
     * — e.g. doubled backslashes on Windows — than the task's file paths). Re-mirror on principle; the
     * version-stamped key guarantees a pre-r11 mirror is never served to an r11 run;
     * r12 adds the explicit USER-nondet witness tag (NondetTagBytecode) to the hoisted rewrite chain:
     * it injects a verification-neutral Bmc.recordNondet("name", value) after each user Bmc.any* store so
     * a refutation's counterexample carries the input robustly (boxed/helper/model nondets included). The
     * VERDICT is unchanged (the sink is empty-body, formula-neutral), but the change alters BOTH the
     * rewritten bytecode (re-mirror) AND the cached counterexample/WITNESS a REFUTED entry stores — a
     * pre-r12 cached refutation would replay the OLD blank/heuristic witness instead of the new tagged
     * one, so the verdict cache (which keys on this IDENTITY, component 1 of computeKey) must miss too.
     * Both caches invalidate on this bump.
     * r13 surfaces the genuine REFUTATION REASON in the rendered detail (JbmcOutputParser.toViolation):
     * a refuted property now names WHAT failed — the thrown exception type + source location + a
     * recoverable constant message (ArithmeticException / NPE / array-bounds / an explicit throw), or an
     * "assertion failed at <user line>" framing — instead of the old blanket "a checked property does not
     * hold". The VERDICT is unchanged (display-only), but the verdict cache STORES that rendered detail,
     * so a pre-r13 cached REFUTED would replay the OLD generic reason; the cache (which keys on this
     * IDENTITY) must miss and re-derive. The rewrite-mirror cache re-mirrors incidentally on the IDENTITY
     * bump even though no bytecode changed — harmless over-invalidation.
     * r14 makes automatic unwind discovery the DEFAULT (@BmcProof unwind = AUTO): a proof with no
     * explicit bound is now run at the smallest bound that yields a conclusive verdict, not the fixed
     * default 16. The VERDICT is unchanged (same VERIFIED/REFUTED/VACUOUS; --unwinding-assertions stays
     * on so under-unwinding fails closed to UNKNOWN) — but a default proof's verdict-cache key now carries
     * the DISCOVERED bound (e.g. 5) rather than 16, so a pre-r14 entry keyed at the old fixed bound must
     * not satisfy an r14 lookup. Bumping re-derives every cached verdict on the soundness-safe side
     * (over-invalidation). Explicit `unwind = N` proofs are unaffected (their key already carried N).
     * r15 plugs a coverage hole in the `new String(char[])` / `new String(char[],int,int)` construction
     * redirect: the deferred-replay buffer abandoned the rewrite whenever a LABEL + line number fell
     * between the array-arg load and the ctor (the LineNumberTable anchor kotlinc/javac place inside a
     * multi-line construction expression), because [StringBytecode]'s visitLabel flushed the recording.
     * Such a label has no stack effect, so it is now RECORDED (replayed in place) rather than flushed —
     * a real control-flow join is still excluded by the jump/switch flushes. Affected char[] constructions
     * now redirect to the sound BmcStrings.ofChars where they previously fell back to JBMC's nondet native
     * construction, so their rewritten bytecode CHANGES (re-mirror) and the verdict cache (keyed on this
     * IDENTITY) must miss and re-derive.
     * r16 extends the from-array construction redirect to the `new String(byte[], ...)` charset-decode
     * constructors — `(byte[])`, `(byte[],int,int)`, and the `(...,Charset)` / `(...,String charsetName)`
     * shapes (a charset-decoding library's `bytes -> String` accessor boils down to `new String(byte[],Charset)`). They now retarget
     * to the sound BmcStrings.ofBytes decoder (UTF-8 + ISO-8859-1/US-ASCII sound, other charsets nondet)
     * where they previously fell back to JBMC's nondet native byte[] decode, so their rewritten bytecode
     * CHANGES (re-mirror) and the verdict cache (keyed on this IDENTITY) must miss and re-derive.
     */
    private const val SEMANTICS_REVISION = "r16"

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
