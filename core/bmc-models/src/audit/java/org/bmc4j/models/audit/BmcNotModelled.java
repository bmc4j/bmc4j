package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a member of the real target class is deliberately <em>not</em> modeled because
 * it <em>cannot</em> be soundly/practically modeled (a JBMC limitation, an unbounded/IO surface, a
 * formatting/parsing concern, double arithmetic this library avoids, etc.). This is the "we tried and
 * it can't be done well" waiver, distinct from {@link BmcNotNeeded} ("it's not worth it").
 *
 * <h2>Method-level only</h2>
 * This annotation goes on a real <b>stub method declaration</b> whose body throws the recognized loud
 * failure (via {@code org.bmc4j.analysis.BmcUnmodelledReached.fail(...)}):
 * <pre>{@code
 * @BmcNotModelled(reason = "comparator-driven sort over the bounded array")
 * public void sort(Comparator<? super E> c) {
 *     throw fail("bmc4j: unmodelled member java.util.ArrayList.sort(java.util.Comparator) — comparator-driven sort over the bounded array");
 * }
 * }</pre>
 * javac validates the signature, the decision and reason live next to the surface they waive, and a
 * future change diffs as a method with its reason. The auditing gate verifies every such stub body
 * actually throws the recognized message (no real logic hides under a not-modeled annotation).
 *
 * <p>The target is {@link ElementType#METHOD} only — there is deliberately <b>no class-level form</b>.
 * It once also targeted {@code TYPE} (with an explicit {@code member()} string) as a fallback for the
 * rare case where a stub declaration is impossible, but that escape hatch was misused to
 * blanket-exempt whole classes, hiding members that should be audited — so it was removed. A waiver
 * must name a real method. If a stub declaration is genuinely impossible to write, absorb the member
 * into the class-level {@link BmcModelTail} remainder instead, which keeps it under the no-growth
 * ratchet.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS} so the gate and the synthesis pass can read it off the
 * model bytecode; it is never needed at runtime.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface BmcNotModelled {

    /** Why this member cannot be modeled. Surfaces in the loud-failure body. */
    String reason();
}
