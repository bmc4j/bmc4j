package org.bmc4j.engine

import kotlin.reflect.KClass

/**
 * One bytecode-transformation pass in the pre-engine pipeline [JbmcBackend.prepareClasspath] runs before
 * jbmc launches. A pass is a 1:1 classpath -> classpath rewrite: it inflates the analysis bytecode, applies
 * an ASM transform, and hands back the rewritten [ClassSet]. The set of passes and their order are NOT
 * hand-wired any more; the generic [PassOrchestrator] derives the run order from each pass's [dependsOn]
 * (deterministic topo-sort, stable tiebreak), so adding a pass is "register it + declare its deps".
 *
 * [transform] always receives the [BmcContext], whether or not it reads it: a pass that ignores the context
 * VALUES is hoistable+cacheable and marks itself [CacheablePass]; a pass that needs a value out of the
 * context (the request, a decoded marker an earlier pass deposited) stays unmarked and runs per-proof.
 * [shouldTransform] is the feature-flag / proof-param gate: a pass whose work this proof does not need
 * returns the [ClassSet] untouched (the orchestrator skips calling [transform], so it costs nothing).
 *
 * The interface is `sealed`: every pass lives in this module, so the orchestrator's two-group split
 * ([CacheablePass] vs unmarked) is exhaustive and the dependency graph is closed and checkable.
 */
sealed interface BmcPass {

    /**
     * Whether this pass does anything for this proof. A feature flag or a per-proof parameter (e.g. a
     * String-mode-only pass, or a pass that is a no-op without contracts) returns `false` so the
     * orchestrator skips it entirely. Default: always run.
     */
    fun shouldTransform(ctx: BmcContext): Boolean = true

    /**
     * Rewrite [classes] and return the result. Pure with respect to the bytecode for a [CacheablePass];
     * may read (and an upstream pass may have deposited) values on [ctx] for an unmarked pass. Must be
     * 1:1 and order-preserving over classpath entries so the surrounding plumbing (model splicing,
     * mirror substitution) lines up.
     */
    fun transform(classes: ClassSet, ctx: BmcContext): ClassSet

    /**
     * The passes that MUST run before this one, by type (refactor-safe vs a string name). Declared ONLY
     * for soundness-critical orderings (e.g. a contract/decode pass that needs the desugared form); an
     * incidental ordering is left unconstrained, and the orchestrator's stable tiebreak
     * ([BmcPass.simpleName]) keeps it deterministic anyway.
     */
    val dependsOn: List<KClass<out BmcPass>> get() = emptyList()
}

/**
 * Marker: a promise that [BmcPass.transform]'s output is a pure function of the input bytecode -- it
 * ignores the [BmcContext] VALUES (it may read run-wide config baked into the bytes, but nothing
 * proof-specific). That promise is what lets the orchestrator HOIST these passes into the cached prefix:
 * the run-wide rewrite is computed once (by the Gradle plugin's mirror task, cached by Gradle's build
 * cache) and reused across cold JVMs, or run once in-JVM and reused across this run's proofs. The cached
 * prefix is keyed by the bytecode plus the set of [CacheablePass]es that actually ran (so a flag toggling
 * one off is captured). The moment a pass needs a per-proof value out of [BmcContext], drop this marker
 * and it becomes a per-proof pass.
 */
interface CacheablePass : BmcPass

/** The simple class name used as the deterministic tiebreak in the topo-sort and as the [PipelineTiming]
 *  label for a pass. Stable across runs (object passes have a fixed class name). */
val BmcPass.simpleName: String
    get() = this::class.simpleName ?: this::class.java.simpleName
