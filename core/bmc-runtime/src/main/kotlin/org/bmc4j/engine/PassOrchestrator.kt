package org.bmc4j.engine

import kotlin.reflect.KClass

/**
 * The generic pipeline driver. It knows NO specific pass: given a registered set of [BmcPass]es it
 *  - partitions them into the [CacheablePass] prefix (hoistable + cached, run once and reused) and the
 *    unmarked per-proof tail;
 *  - derives the run order WITHIN each group by a deterministic topological sort over [BmcPass.dependsOn],
 *    breaking ties by [BmcPass.simpleName] so passes with no ordering constraint never reorder run-to-run;
 *  - times each pass into a [PipelineTiming], labelled by the pass's [BmcPass.simpleName].
 *
 * Adding a pass is: construct it, register it, declare its `dependsOn`. The orchestrator is untouched.
 *
 * It FAILS LOUD on a malformed graph -- a dependency cycle, an edge to an unregistered pass, or a
 * [CacheablePass] that depends on an unmarked pass (which would make the cached prefix depend on per-proof
 * state, breaking the hoisting promise).
 *
 * The orchestrator runs the two groups SEPARATELY ([runCacheable] / [runPerProof]) rather than as one
 * sweep, because the surrounding [JbmcBackend.prepareClasspath] plumbing has to act BETWEEN them: the
 * cacheable prefix may be substituted wholesale by the Gradle plugin's pre-built mirror (in which case the
 * orchestrator's cacheable group is not run in-JVM at all), and model jars / fan-out are spliced at the
 * boundary. Splitting the run is what keeps that existing cache mechanism intact.
 */
class PassOrchestrator(passes: List<BmcPass>) {

    /** The cacheable prefix, in deterministic topo order. */
    private val cacheablePlan: List<BmcPass>

    /** The per-proof tail, in deterministic topo order. */
    private val perProofPlan: List<BmcPass>

    init {
        validate(passes)
        val cacheable = passes.filterIsInstance<CacheablePass>()
        val perProof = passes.filter { it !is CacheablePass }
        // Topo-sort each GROUP independently. A cross-group dependency can only point from a per-proof pass
        // to a cacheable one (the reverse is rejected by validate), and the cacheable group always runs
        // first as a whole, so a per-proof pass's cacheable dependency is already satisfied -- the
        // intra-group sort need only order within the group.
        cacheablePlan = topoSort(cacheable)
        perProofPlan = topoSort(perProof)
    }

    /** The cacheable passes in run order (for the byte-equivalence assertion against the Gradle mirror). */
    fun cacheablePasses(): List<BmcPass> = cacheablePlan

    /** The per-proof passes in run order. */
    fun perProofPasses(): List<BmcPass> = perProofPlan

    /**
     * Run the cacheable prefix in-JVM over [classes] (the path taken when the Gradle plugin did NOT
     * pre-mirror the classpath). Each enabled pass is timed into [timing] under its simpleName. When the
     * plugin pre-mirrored, the caller substitutes the mirror instead and does NOT call this -- preserving
     * the existing run-once + cache mechanism.
     */
    fun runCacheable(classes: ClassSet, ctx: BmcContext, timing: PipelineTiming?): ClassSet =
            run(cacheablePlan, classes, ctx, timing)

    /** Run the per-proof tail in-JVM over [classes], timing each enabled pass into [timing]. */
    fun runPerProof(classes: ClassSet, ctx: BmcContext, timing: PipelineTiming?): ClassSet =
            run(perProofPlan, classes, ctx, timing)

    private fun run(plan: List<BmcPass>, classes: ClassSet, ctx: BmcContext,
                    timing: PipelineTiming?): ClassSet {
        var cur = classes
        for (pass in plan) {
            if (!pass.shouldTransform(ctx)) {
                continue
            }
            val label = pass.simpleName
            cur = if (timing != null) timing.time(label) { pass.transform(cur, ctx) }
            else pass.transform(cur, ctx)
        }
        return cur
    }

    private companion object {

        /** Reject a malformed graph BEFORE any pass runs: an edge to a pass not in the registered set, a
         *  cycle (detected by the topo-sort), or a [CacheablePass] depending on an unmarked one. */
        fun validate(passes: List<BmcPass>) {
            val byType: Map<KClass<out BmcPass>, BmcPass> = passes.associateBy { it::class }
            for (pass in passes) {
                for (dep in pass.dependsOn) {
                    val target = byType[dep]
                            ?: throw IllegalStateException(
                                    "bmc4j pipeline: ${pass.simpleName} depends on ${dep.simpleName}, " +
                                            "which is not registered.")
                    if (pass is CacheablePass && target !is CacheablePass) {
                        throw IllegalStateException(
                                "bmc4j pipeline: cacheable pass ${pass.simpleName} depends on per-proof " +
                                        "pass ${target.simpleName}; a cached prefix may not depend on " +
                                        "per-proof state.")
                    }
                }
            }
        }

        /**
         * Deterministic topological sort of [group] over its [BmcPass.dependsOn] edges restricted to the
         * group, breaking ties by [BmcPass.simpleName]. Kahn's algorithm with a sorted ready-frontier:
         * among passes whose dependencies are all already emitted, always take the simpleName-smallest, so
         * unconstrained passes keep a stable, run-to-run-identical order. Throws on a cycle.
         */
        fun topoSort(group: List<BmcPass>): List<BmcPass> {
            val inGroup: Set<KClass<out BmcPass>> = group.map { it::class }.toSet()
            val byType = group.associateBy { it::class }
            // Edges restricted to this group: a cross-group dep (per-proof -> cacheable) is satisfied by
            // construction (cacheable group runs first), so it does not constrain the intra-group order.
            val remainingDeps: MutableMap<KClass<out BmcPass>, MutableSet<KClass<out BmcPass>>> =
                    group.associate { pass ->
                        pass::class to pass.dependsOn.filter { it in inGroup }.toMutableSet()
                    }.toMutableMap()
            val emitted = ArrayList<BmcPass>(group.size)
            val done = HashSet<KClass<out BmcPass>>()
            while (emitted.size < group.size) {
                // Ready = every remaining dep already emitted; pick the simpleName-smallest for stability.
                val next = remainingDeps.entries
                        .filter { it.key !in done && it.value.all { d -> d in done } }
                        .map { byType.getValue(it.key) }
                        .minByOrNull { it.simpleName }
                        ?: throw IllegalStateException(
                                "bmc4j pipeline: dependency cycle among passes " +
                                        remainingDeps.keys.filter { it !in done }
                                                .map { byType.getValue(it).simpleName }.sorted())
                emitted.add(next)
                done.add(next::class)
            }
            return emitted
        }
    }
}
