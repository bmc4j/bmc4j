package org.bmc4j.engine

/**
 * Thrown when JBMC refutes a proof. Extends [AssertionError] so JUnit
 * reports it as an ordinary assertion failure; its stack trace is synthesized to
 * point at the offending source line and call chain.
 */
open class BmcVerificationError(message: String?) : AssertionError(message)
