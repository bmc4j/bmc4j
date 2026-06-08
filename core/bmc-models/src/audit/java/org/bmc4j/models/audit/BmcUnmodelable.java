package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a member of the real target class genuinely <em>cannot</em> be soundly/practically
 * modeled (a JBMC limitation, an unbounded/IO surface, a formatting/parsing concern, double arithmetic
 * this library avoids, an exotic surface whose faithful model earns nothing, etc.). This is the
 * <b>loud-if-reached</b> waiver: the member carries no usable model, so reaching it must FAIL LOUDLY
 * rather than proceed on a fiction.
 *
 * <p>It is the OPPOSITE of {@link BmcNotNeeded} (green-if-reached, documentary): {@code @BmcNotNeeded}
 * accounts for a member whose unmodeled real/inline bytecode is sound under JBMC — reaching it is fine.
 * {@code @BmcUnmodelable} accounts for a member that has no sound path at all — reaching it trips the
 * sentinel and demotes the verdict to a member-named UNKNOWN (a model gap, not a counterexample).
 *
 * <h2>Method-level loud stub (primary form)</h2>
 * The decision lives ON a real <b>stub method declaration</b> whose body throws the recognized loud
 * failure (via {@code org.bmc4j.analysis.BmcUnmodelledReached.fail(...)}):
 * <pre>{@code
 * @BmcUnmodelable(reason = "comparator-driven sort over the bounded array")
 * public void sort(Comparator<? super E> c) {
 *     throw fail("bmc4j: unmodelled member java.util.ArrayList.sort(java.util.Comparator) — comparator-driven sort over the bounded array");
 * }
 * }</pre>
 * javac validates the signature, the decision and reason live next to the surface they waive, and a
 * future change diffs as a method with its reason. The auditing gate verifies every such stub body
 * actually throws the recognized message (no real logic hides under an unmodelable annotation).
 *
 * <h2>Class-level form</h2>
 * It is <b>also class-level and repeatable</b> (via {@link BmcUnmodelableList}) with an explicit
 * {@link #member()} string, for the rare case where a stub declaration is impossible. The auditing gate
 * accounts for each declared member, fails on a dangling declaration (a named member absent from the
 * real class), and the build-time synthesis pass gives each such member a LOUD body so reaching it
 * fails loudly under JBMC.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS} so the gate and the synthesis pass can read it off the
 * model bytecode; it is never needed at runtime.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Repeatable(BmcUnmodelableList.class)
public @interface BmcUnmodelable {

    /**
     * Erased member signature {@code name(paramType,...)}. REQUIRED for the class-level form; OMIT it on
     * a method-level stub.
     */
    String member() default "";

    /** Why this member cannot be modeled. Surfaces in the loud-failure body. */
    String reason();
}
