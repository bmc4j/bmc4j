package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The generic [PassOrchestrator] over the production [PassRegistry]: the two-group split, the deterministic
 * topo-order, and the stable tiebreak. Pure structural checks (no engine), so they run in the fast unit
 * lane. (The orchestrator's loud-failure paths -- cycle, dangling dep, cacheable-depends-on-per-proof --
 * cannot be exercised from the test source set because [BmcPass] is `sealed`: no out-of-module pass can be
 * defined to provoke them. That sealing is itself the guarantee that every pass lives in the product
 * module; the validation runs over the real registry in [PassRegistry.ORCHESTRATOR]'s constructor, so a
 * malformed production graph would fail every test in the module at class-load.)
 */
class PassOrchestratorTest {

    @Test
    fun `cacheable prefix runs Desugar AnyRef Config KotlinParam Reachability NondetTag in order`() {
        // This is the order GradleClasspathMirror.mirror runs and applyHoistablePasses reproduces; the
        // in-JVM cacheable run must match it byte-for-byte, which starts with matching the pass order.
        assertEquals(
                listOf(DesugarPass, AnyRefPass, ConfigPass, KotlinParamPass, ReachabilityPass, NondetTagPass),
                PassRegistry.ORCHESTRATOR.cacheablePasses())
    }

    @Test
    fun `cacheable and per-proof groups partition the registry with no overlap`() {
        val cacheable = PassRegistry.ORCHESTRATOR.cacheablePasses()
        val perProof = PassRegistry.ORCHESTRATOR.perProofPasses()
        // Every cacheable pass carries the marker; no per-proof pass does.
        assertTrue(cacheable.all { it is CacheablePass })
        assertTrue(perProof.none { it is CacheablePass })
        // Together they cover the whole registry exactly once.
        assertEquals(PassRegistry.ALL.toSet(), (cacheable + perProof).toSet())
        assertEquals(PassRegistry.ALL.size, cacheable.size + perProof.size)
    }

    @Test
    fun `contract and purity passes sort after the desugar prefix and model slice sorts last`() {
        val perProof = PassRegistry.ORCHESTRATOR.perProofPasses()
        // ModelSlice depends on the whole tail, so it is last.
        assertEquals(ModelSlicePass, perProof.last())
        // Order is a deterministic topo-sort, so it is identical across constructions.
        assertEquals(perProof, PassOrchestrator(PassRegistry.ALL).perProofPasses())
    }
}
