package org.bmc4j.engine

/**
 * A bounded-model-checking engine behind `@BmcProof`. The JUnit extension is
 * engine-agnostic: it builds a [BmcRequest] and asks a backend to verify it.
 * Each backend owns its own toolchain — locating/launching the checker, preparing the
 * analysis input (model classpaths, IR conversion, bytecode rewrites), and parsing
 * the result into a [JbmcResult].
 *
 * The one backend today is JBMC ([JbmcBackend]); the interface is kept so
 * another engine could plug in (see [VerificationBackends]). For concurrency
 * correctness, use Lincheck rather than a BMC backend — see the README.
 */
interface VerificationBackend {

    /** Stable identifier, e.g. `"jbmc"` or `"esbmc"`; matched by `-Dbmc.backend`. */
    fun id(): String

    /** Verify the proof. Returns whether it holds plus any counterexample detail. */
    fun verify(request: BmcRequest): JbmcResult

    /**
     * A stable identity of the engine binary that would run a proof, for the verdict cache:
     * a new engine version can change a verdict, so its identity must be part of the cache key. Default
     * is just [id]; a backend that can pin its exact binary (version or content hash) should
     * override to be more precise so swapping the binary invalidates cached verdicts.
     */
    fun engineIdentity(): String = id()
}
