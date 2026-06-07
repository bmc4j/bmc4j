package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a member of the real target class is deliberately <em>not</em> modeled because
 * it is <em>not worth it</em> — an exotic / rarely-reached surface whose absence is acceptable, not a
 * hard modeling impossibility. This is the "we could but it earns nothing" waiver, distinct from
 * {@link BmcNotModelled} ("it can't be modeled").
 *
 * <p>Like {@link BmcNotModelled}, prefer the <b>method-level stub</b> form (annotate a real stub
 * method whose body throws the recognized loud failure via {@code BmcUnmodelledReached.fail(...)}) —
 * see {@link BmcNotModelled} for the example and the rules. It is <b>also class-level and
 * repeatable</b> with an explicit {@link #member()} string for the rare case a stub declaration is
 * impossible. The auditing gate accounts for each declared member, fails on a dangling declaration
 * (named member absent from the real class), and verifies every method-level stub body actually
 * throws the recognized message. Reaching any such member fails loudly under JBMC (demoted to UNKNOWN
 * — a model gap, not a counterexample).
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}; never needed at runtime.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Repeatable(BmcNotNeededList.class)
public @interface BmcNotNeeded {

    /**
     * Erased member signature {@code name(paramType,...)} — see {@link BmcNotModelled} for the format.
     * REQUIRED for the class-level form; OMIT it on a method-level stub.
     */
    String member() default "";

    /** Why this member is not worth modeling. Surfaces in the loud-failure body. */
    String reason();
}
