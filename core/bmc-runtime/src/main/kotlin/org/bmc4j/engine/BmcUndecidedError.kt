package org.bmc4j.engine

/**
 * Thrown when a proof could not be *decided* within its budget — the engine
 * neither verified nor refuted it. Causes: the per-proof timeout expired, the solver gave up or
 * crashed (engine error exit), or JBMC produced output we couldn't parse into a verdict.
 *
 * This is a distinct verdict from a refutation: there is no counterexample, because nothing was
 * proven wrong — we simply ran out of budget or the engine fell over. It still **fails** the test
 * (soundness: the absence of a verdict is not a proof), but with an unmistakably different message
 * and exception type so a resource-exhaustion in CI is never mistaken for "your code is wrong".
 *
 * Extends [BmcVerificationError] so existing `catch`/reporting that treats any bmc
 * failure uniformly keeps working, while callers that care can distinguish UNKNOWN from REFUTED on
 * the type.
 */
open class BmcUndecidedError @JvmOverloads constructor(
        message: String?,
        /** True when the engine couldn't run at all (infrastructure failure), as opposed to a
         *  genuine undecided verdict (timeout / solver gave up). An infrastructure UNKNOWN does NOT
         *  satisfy `@BmcProof(expect = UNKNOWN)` — a broken engine must never masquerade as an
         *  undecidability demo. */
        private val engineInfrastructure: Boolean = false,
        /** The TYPED cause of this UNKNOWN (null only for legacy/unclassified framings). Surfaced in
         *  the test-failure message and the proof-results comment so an undecided proof is
         *  classifiable. Does NOT itself drive retry at this layer — the engine-level retry keys off
         *  [JbmcResult.undecidedKind]; here the kind is telemetry/diagnosis. */
        val kind: UnknownKind? = null) : BmcVerificationError(message) {

    fun isEngineInfrastructure(): Boolean = engineInfrastructure
}
