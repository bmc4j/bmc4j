package org.bmc4j.engine;

/**
 * Selects the {@link VerificationBackend} for a proof. Today there is one: JBMC
 * (symbolic, all-inputs bounded model checking with the JDK operational models).
 *
 * <p>The abstraction is kept so another engine can plug in later, but ESBMC was
 * removed: as a library we can't predict users' proof surface, and JBMC's mature
 * frontend + JDK models are the better default. For <em>concurrency</em> correctness
 * (interleavings, races, lock-freedom) point users at <b>Lincheck</b> — see the README;
 * {@code @BmcProof} answers "is my logic sound", Lincheck answers "is my concurrent
 * code correct".
 */
public final class VerificationBackends {

    private static final String BACKEND_PROP = "bmc.backend";

    private VerificationBackends() {
    }

    /** Backend for this proof. (The request is accepted for future per-kind routing.) */
    public static VerificationBackend select(BmcRequest request) {
        String id = System.getProperty(BACKEND_PROP, "jbmc").trim().toLowerCase();
        switch (id) {
            case "jbmc":
                return new JbmcBackend();
            case "esbmc":
                throw new IllegalArgumentException(
                        "The ESBMC backend was removed. For concurrency use Lincheck (see README); "
                                + "for logic proofs use -Dbmc.backend=jbmc (the default).");
            default:
                throw new IllegalArgumentException(
                        "Unknown -Dbmc.backend='" + id + "'. Known backends: jbmc.");
        }
    }
}
