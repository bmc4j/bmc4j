package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a named member of the real target class is deliberately <em>not</em> modeled because
 * it is <em>not worth it</em> — an exotic / rarely-reached surface whose absence is acceptable, not a
 * hard modeling impossibility. This is the "we could but it earns nothing" waiver, distinct from
 * {@link BmcNotModelled} ("it can't be modeled").
 *
 * <p>Like {@link BmcNotModelled} it is <b>class-level and repeatable</b> (you cannot annotate an
 * absent member) and uses the same erased member-signature format
 * ({@code name(paramType,...)} — see {@link BmcNotModelled} for the full grammar). The auditing gate
 * accounts for each declared member and fails on a dangling declaration (named member absent from the
 * real class). The loud-body synthesis pass gives any such member a body throwing {@code
 * AssertionError} naming the member and reason, so reaching it under JBMC fails loudly.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}; never needed at runtime.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Repeatable(BmcNotNeededList.class)
public @interface BmcNotNeeded {

    /** Erased member signature {@code name(paramType,...)} — see {@link BmcNotModelled} for the format. */
    String member();

    /** Why this member is not worth modeling. Surfaces in the synthesized loud-failure body. */
    String reason();
}
