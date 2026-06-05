package org.bmc4j.engine;

/**
 * Thrown when a proof could not be <em>decided</em> within its budget — the engine
 * neither verified nor refuted it. Causes: the per-proof timeout expired, the solver gave up or
 * crashed (engine error exit), or JBMC produced output we couldn't parse into a verdict.
 *
 * <p>This is a distinct verdict from a refutation: there is no counterexample, because nothing was
 * proven wrong — we simply ran out of budget or the engine fell over. It still <b>fails</b> the test
 * (soundness: the absence of a verdict is not a proof), but with an unmistakably different message
 * and exception type so a resource-exhaustion in CI is never mistaken for "your code is wrong".
 *
 * <p>Extends {@link BmcVerificationError} so existing {@code catch}/reporting that treats any bmc
 * failure uniformly keeps working, while callers that care can distinguish UNKNOWN from REFUTED on
 * the type.
 */
public class BmcUndecidedError extends BmcVerificationError {

    /** True when the engine couldn't run at all (infrastructure failure), as opposed to a
     *  genuine undecided verdict (timeout / solver gave up). An infrastructure UNKNOWN does NOT
     *  satisfy {@code @BmcProof(expect = UNKNOWN)} — a broken engine must never masquerade as an
     *  undecidability demo. */
    private final boolean engineInfrastructure;

    public BmcUndecidedError(String message) {
        this(message, false);
    }

    public BmcUndecidedError(String message, boolean engineInfrastructure) {
        super(message);
        this.engineInfrastructure = engineInfrastructure;
    }

    public boolean isEngineInfrastructure() {
        return engineInfrastructure;
    }
}
