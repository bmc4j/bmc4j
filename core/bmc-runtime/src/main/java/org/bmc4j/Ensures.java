package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A postcondition contract on a <b>pure</b> method, used for modular (assume-guarantee)
 * proofs. Names a {@code static boolean} predicate over the method's <em>result</em> and
 * parameters that the method promises to satisfy.
 *
 * <p>The predicate's first parameter is the method's return value; the remaining
 * parameters match the method's parameters (by position and type):
 *
 * <pre>{@code
 * @Ensures("resultNonNegative")
 * static int isqrt(int n) { ... }
 *
 * static boolean resultNonNegative(int result, int n) { return result >= 0; }
 * }</pre>
 *
 * <p>Once discharged (its enforce-proof is green, which the tooling generates and runs
 * automatically), the contract may <b>replace</b> calls to the method in other proofs —
 * the caller assumes the postcondition instead of re-analyzing the body. This makes proofs
 * modular (cost additive, not multiplicative with call depth) and lets a recursive call be
 * replaced by its own contract (the inductive step).
 *
 * <p><b>Soundness invariants</b> (enforced/encouraged by the tooling, see the README):
 * <ul>
 *   <li>contracts apply to pure, value-returning methods only;</li>
 *   <li>a contract is reusable only after its enforce-proof passes (auto-generated, so the
 *       build is red if a contract is false);</li>
 *   <li>a postcondition must be tight enough not to admit defects the real body lacks
 *       (e.g. an unbounded result range can introduce caller-side overflow);</li>
 *   <li>recursion-via-contract proves <em>partial</em> correctness; termination is separate.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Ensures {

    /**
     * Name of a {@code static boolean} predicate method, declared in the same class, taking
     * {@code (returnType result, parameters...)} of the annotated method.
     */
    String value();
}
