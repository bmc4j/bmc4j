package org.bmc4j.engine

/**
 * Thrown when a contracted method's body is not provably **pure** (see [ContractPurityAudit]).
 *
 * This is categorically different from a verification verdict. It is neither a refutation (no
 * counterexample — the body may compute the right value, it just also has a caller-observable side
 * effect a contract would silently drop) nor an UNKNOWN (we *did* look and found a disqualifying
 * effect). It is a **contract-configuration error**: the method is not a legal contract target.
 *
 * It must therefore fail the build **unconditionally** — a `@BmcProof(expect = …)` or
 * `@BmcContractsFor(expectEnforce = …)` declaration can never make an impure contract acceptable
 * the way it can pin a deliberately-false `@Ensures` to REFUTED. The runner recognizes this type
 * and rethrows it without judging it against any expectation (unlike a plain
 * [BmcVerificationError], which is treated as a refutation and may match `expect = REFUTED`).
 *
 * It extends [BmcVerificationError] (hence `AssertionError`) only so existing top-level
 * catch/reporting that handles any bmc failure keeps working; the runner's type check is what makes
 * it un-swallowable.
 */
class ContractPurityError(message: String) : BmcVerificationError(message)
