package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a member of the real target class needs <em>no</em> model: the real/inline bytecode the
 * member runs is <b>sound under JBMC</b>, so leaving it unmodeled is fine and reaching it is harmless.
 * This is the <b>green-if-reached</b> waiver — a documentary account, NOT a loud stub.
 *
 * <p>It is the OPPOSITE of {@link BmcUnmodelable} (loud-if-reached): {@code @BmcUnmodelable} marks a
 * member with no sound path, whose reach must FAIL LOUDLY; {@code @BmcNotNeeded} marks a member we
 * verified JBMC analyzes correctly without a model, whose reach is safe to let through.
 *
 * <h2>No loud stub required</h2>
 * Unlike {@link BmcUnmodelable}, a method-level {@code @BmcNotNeeded} stub's body is <b>not</b> required
 * to throw the recognized loud failure — it documents "reaching the real/inline path is sound" rather
 * than diverting to the sentinel. The auditing gate still accounts for each declared/annotated member
 * (so the class's surface stays complete) and verifies that class-level {@link #member()} declarations
 * name a real member (no dangling declaration), but it demands no loud body. A {@code @BmcNotNeeded}
 * member is NOT counted as conforming/modeled and still participates in classification mutual
 * exclusivity (it is exactly one of the four classifications).
 *
 * <h2>Forms</h2>
 * Method-level (annotate a real stub method) or class-level + {@link Repeatable @Repeatable} (via
 * {@link BmcNotNeededList}) with an explicit {@link #member()} string.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}; never needed at runtime.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Repeatable(BmcNotNeededList.class)
public @interface BmcNotNeeded {

    /**
     * Erased member signature {@code name(paramType,...)} — see {@link BmcUnmodelable} for the format.
     * REQUIRED for the class-level form; OMIT it on a method-level annotation.
     */
    String member() default "";

    /** Why this member needs no model (the unmodeled real/inline path is sound under JBMC). */
    String reason();
}
