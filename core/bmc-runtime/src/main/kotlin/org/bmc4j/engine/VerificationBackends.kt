package org.bmc4j.engine

/**
 * Selects the [VerificationBackend] for a proof. Today there is one: JBMC
 * (symbolic, all-inputs bounded model checking with the JDK operational models).
 *
 * The abstraction is kept so another engine can plug in later, but ESBMC was
 * removed: as a library we can't predict users' proof surface, and JBMC's mature
 * frontend + JDK models are the better default. For *concurrency* correctness
 * (interleavings, races, lock-freedom) point users at **Lincheck** — see the README;
 * `@BmcProof` answers "is my logic sound", Lincheck answers "is my concurrent
 * code correct".
 */
object VerificationBackends {

    private const val BACKEND_PROP = "bmc.backend"

    /** Backend for this proof. (The request is accepted for future per-kind routing.) */
    @JvmStatic
    fun select(request: BmcRequest): VerificationBackend {
        val id = System.getProperty(BACKEND_PROP, "jbmc").trim().lowercase()
        return when (id) {
            "jbmc" -> JbmcBackend()
            "esbmc" -> throw IllegalArgumentException(
                    "The ESBMC backend was removed. For concurrency use Lincheck (see README); " +
                            "for logic proofs use -Dbmc.backend=jbmc (the default).")
            else -> throw IllegalArgumentException(
                    "Unknown -Dbmc.backend='$id'. Known backends: jbmc.")
        }
    }
}
