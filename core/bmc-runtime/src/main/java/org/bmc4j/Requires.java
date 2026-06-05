package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A precondition contract on a <b>pure</b> method, used for modular (assume-guarantee)
 * proofs. Names a {@code static boolean} predicate over the method's parameters that must
 * hold whenever the method is called.
 *
 * <p>The predicate is referenced by name rather than written as a string expression, so
 * it is ordinary, type-checked, IDE-navigable Java that JBMC can analyze directly:
 *
 * <pre>{@code
 * @Requires("nonNegative")
 * @Ensures("resultNonNegative")
 * static int isqrt(int n) { ... }
 *
 * static boolean nonNegative(int n) { return n >= 0; }
 * static boolean resultNonNegative(int result, int n) { return result >= 0; }
 * }</pre>
 *
 * <p>Two directions are derived from the contract:
 * <ul>
 *   <li><b>enforce</b> (discharge once): {@code assume(requires); <body>; assert(ensures)}.</li>
 *   <li><b>replace</b> (reuse everywhere): at a call site, {@code assert(requires);
 *       r = nondet(); assume(ensures(r, args))} — the caller relies on the promise instead
 *       of re-analyzing the body.</li>
 * </ul>
 *
 * <p><b>Soundness:</b> contracts are only sound for pure (side-effect-free, value-returning)
 * methods, and a contract is only trustworthy once its enforce-proof is green — which the
 * tooling discharges automatically. See {@link Ensures}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Requires {

    /**
     * Name of a {@code static boolean} predicate method, declared in the same class, whose
     * parameters match the annotated method's parameters (by position and type).
     */
    String value();
}
