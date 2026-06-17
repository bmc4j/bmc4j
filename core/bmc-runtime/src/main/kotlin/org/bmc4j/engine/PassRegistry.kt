package org.bmc4j.engine

/**
 * The registered set of [BmcPass]es and the single [PassOrchestrator] built over them. Registering a new
 * pass is a one-line edit here plus declaring its `dependsOn` on the pass itself; the orchestrator derives
 * the run order. The orchestrator validates the dependency graph eagerly (on first touch), so a cycle, a
 * dangling dependency, or a cacheable pass depending on a per-proof one fails loud at startup rather than
 * mid-proof.
 *
 * NB: fan-out ([DomainSplitBytecode]) is deliberately NOT registered here -- it derives N classpaths for
 * parallel runs, not a 1:1 classpath transform, so it stays the separate fan-out concept it is today and is
 * integrated at the [JbmcBackend.prepareClasspath] boundary.
 */
internal object PassRegistry {

    /** Every 1:1 pass, cacheable and per-proof together. The orchestrator partitions and orders them. */
    val ALL: List<BmcPass> = listOf(
            // Cacheable prefix (run-wide, env-independent; hoistable into the Gradle mirror).
            DesugarPass, AnyRefPass, ConfigPass, KotlinParamPass, ReachabilityPass, NondetTagPass,
            // Per-proof tail.
            ContractRewritePass, AssumeContractPass, StringLengthPass,
            PurityAuditPass, ExceptionMessageElisionPass, ConstructReceiverPass, ModelSlicePass)

    /** The generic orchestrator over [ALL]; validation runs in its constructor. */
    val ORCHESTRATOR: PassOrchestrator = PassOrchestrator(ALL)
}
