package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts that the annotated model <em>method</em> <em>models</em> a member of the real target class
 * and is covered by the conformance axes (differential suite and/or {@code @BmcProof} laws).
 *
 * <p><b>Method-level only.</b> Every conforming member is pinned individually: place this annotation
 * on each model method that mirrors-and-conforms to a real member. There is no class-level ("blanket")
 * form — a single annotation on the type used to mean "every implemented member conforms", which
 * obscured exactly <em>which</em> members were audited and let new, unaudited members ride in silently.
 * Each member now carries its own explicit, audited decision.
 *
 * <p>The model auditing gate (in {@code bmc-models-conformance}) requires every public/protected
 * member of the real target class to be one of: implemented by the model and carrying this annotation,
 * waived by a {@link BmcUnmodelable} (loud-if-reached) or {@link BmcNotNeeded} (green-if-reached) member
 * — method-level or class-level ({@code member=…}) — or absorbed by a class-level {@link BmcModelTail}.
 * The conforming surface resolves through the model's
 * inheritance chain — a member implemented (and {@code @BmcModelConforms}-annotated) by a modeled
 * superclass counts (e.g. {@code LinkedList} inheriting the annotated {@code ArrayList} model methods).
 *
 * <p>Crucially, the dangerous direction — a real member with <em>no</em> model, silently stubbed to
 * nondet on the analysis path — is what the completeness check (tail / declaration required, plus the
 * build-time loud-body synthesis) enforces.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}: the annotation must survive into the model jar (the
 * gate reads it off the relocated bytecode) but is never needed at runtime — the models never run on
 * a real JVM.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface BmcModelConforms {

    /**
     * Optional note (e.g. "differential", "law: idempotent", a caveat). Informational only; the gate
     * does not parse it.
     */
    String value() default "";
}
