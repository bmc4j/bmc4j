package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a named member of the real target class is deliberately <em>not</em> modeled because
 * it <em>cannot</em> be soundly/practically modeled (a JBMC limitation, an unbounded/IO surface, a
 * formatting/parsing concern, double arithmetic this library avoids, etc.). This is the "we tried and
 * it can't be done well" waiver, distinct from {@link BmcNotNeeded} ("it's not worth it").
 *
 * <p>It is <b>class-level and repeatable</b> because you cannot annotate a method that does not exist
 * on the model — the whole point is that the member is absent. Each declaration names the real
 * member and the reason it is unmodeled.
 *
 * <p>The model auditing gate (in {@code bmc-models-conformance}) treats every member declared here as
 * an accounted-for decision, and additionally <b>fails on a dangling declaration</b>: if the named
 * member does not exist on the real target class (a typo, or JDK drift removed it), the build fails.
 * The build-time loud-body synthesis pass also reads these declarations and gives any such member a
 * synthesized body that throws {@code AssertionError} naming the member and reason, so a proof that
 * reaches an unmodeled member fails loudly under JBMC instead of silently havocking.
 *
 * <h2>Member signature format</h2>
 * The {@link #member()} string is an <b>erased</b> signature: {@code name(paramType,...)} where each
 * parameter type is its erased source form — a fully-qualified type for reference types
 * ({@code java.lang.Object}, {@code java.util.Collection}), the primitive keyword for primitives
 * ({@code int}, {@code long}, {@code boolean}), and a trailing {@code []} for arrays
 * ({@code int[]}, {@code java.lang.Object[]}). Generics are erased (use the bound or
 * {@code java.lang.Object}). No return type, no spaces. A constructor uses the bare name
 * {@code <init>}. Examples: {@code "split(java.lang.String)"}, {@code "toArray(java.lang.Object[])"},
 * {@code "<init>(double)"}, {@code "forEach(java.util.function.BiConsumer)"}.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS} so the gate and the synthesis pass can read it off the
 * model bytecode; it is never needed at runtime.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Repeatable(BmcNotModelledList.class)
public @interface BmcNotModelled {

    /** Erased member signature {@code name(paramType,...)} — see the type-level doc for the format. */
    String member();

    /** Why this member cannot be modeled. Surfaces in the synthesized loud-failure body. */
    String reason();
}
