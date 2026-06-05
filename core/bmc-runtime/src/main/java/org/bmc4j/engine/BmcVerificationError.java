package org.bmc4j.engine;

/**
 * Thrown when JBMC refutes a proof. Extends {@link AssertionError} so JUnit
 * reports it as an ordinary assertion failure; its stack trace is synthesized to
 * point at the offending source line and call chain.
 */
public class BmcVerificationError extends AssertionError {

    public BmcVerificationError(String message) {
        super(message);
    }
}
