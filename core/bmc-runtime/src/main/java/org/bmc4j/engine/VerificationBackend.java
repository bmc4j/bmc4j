package org.bmc4j.engine;

/**
 * A bounded-model-checking engine behind {@code @BmcProof}. The JUnit extension is
 * engine-agnostic: it builds a {@link BmcRequest} and asks a backend to verify it.
 * Each backend owns its own toolchain — locating/launching the checker, preparing the
 * analysis input (model classpaths, IR conversion, bytecode rewrites), and parsing
 * the result into a {@link JbmcResult}.
 *
 * <p>The one backend today is JBMC ({@link JbmcBackend}); the interface is kept so
 * another engine could plug in (see {@link VerificationBackends}). For concurrency
 * correctness, use Lincheck rather than a BMC backend — see the README.
 */
public interface VerificationBackend {

    /** Stable identifier, e.g. {@code "jbmc"} or {@code "esbmc"}; matched by {@code -Dbmc.backend}. */
    String id();

    /** Verify the proof. Returns whether it holds plus any counterexample detail. */
    JbmcResult verify(BmcRequest request);

    /**
     * A stable identity of the engine binary that would run a proof, for the verdict cache:
     * a new engine version can change a verdict, so its identity must be part of the cache key. Default
     * is just {@link #id()}; a backend that can pin its exact binary (version or content hash) should
     * override to be more precise so swapping the binary invalidates cached verdicts.
     */
    default String engineIdentity() {
        return id();
    }
}
