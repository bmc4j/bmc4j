package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asserts that a model member (or, at class level, every implemented model member of the class)
 * <em>models</em> a member of the real target class and is covered by the conformance axes
 * (differential suite and/or {@code @BmcProof} laws).
 *
 * <p>The model auditing gate (in {@code bmc-models-conformance}) requires every public/protected
 * member of the real target class to be one of: implemented by the model and conforming (this
 * annotation), declared in a class-level {@link BmcNotModelled} / {@link BmcNotNeeded}, or absorbed by
 * a class-level {@link BmcModelTail}. The conforming surface resolves through the model's inheritance
 * chain — a member implemented by a modeled superclass counts (e.g. {@code LinkedList} inheriting the
 * {@code ArrayList} model).
 *
 * <h2>Class-level vs method-level</h2>
 * <ul>
 *   <li><b>Class level</b> ({@code @BmcModelConforms} on the model type): "every member this class
 *       implements that mirrors a real member is modeled and conforms." This is the normal form for
 *       these small, fully-audited models — one annotation covers the whole implemented surface.</li>
 *   <li><b>Method level</b>: pin a specific member as conforming, optionally with a note. Use it when
 *       a class is only <em>partially</em> blanket-conforming, or to document a per-member caveat.</li>
 * </ul>
 *
 * <p>Crucially, the dangerous direction — a real member with <em>no</em> model, silently stubbed to
 * nondet on the analysis path — is what the completeness check (tail / declaration required, plus the
 * build-time loud-body synthesis) enforces; a blanket class-level conforms never weakens that.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}: the annotation must survive into the model jar (the
 * gate reads it off the relocated bytecode) but is never needed at runtime — the models never run on
 * a real JVM.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface BmcModelConforms {

    /**
     * Optional note (e.g. "differential", "law: idempotent", a caveat). Informational only; the gate
     * does not parse it.
     */
    String value() default "";
}
