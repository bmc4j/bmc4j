package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level catch-all for the <em>exotic tail</em> of a real target class's surface: every public /
 * protected member of the real class that this model neither implements (with
 * {@link BmcModelConforms}) nor names in a per-member {@link BmcNotModelled} / {@link BmcNotNeeded}
 * is, by this annotation, deliberately unmodeled for the stated reason.
 *
 * <p>Why this exists: some JDK targets expose hundreds of members ({@code Arrays} ~214,
 * {@code CompletableFuture} ~79, the full {@code LocalDateTime}/{@code BigDecimal} surface) whose vast
 * majority are formatting/parsing/IO/double/reflective/exotic methods that are out of scope for a
 * bounded model. Enumerating each as an individual waiver is noise that obscures the meaningful
 * decisions. {@code @BmcModelTail} records the single decision "the rest is intentionally absent"
 * <b>without weakening the soundness guarantee</b>: the build-time loud-body synthesis pass gives a
 * loud {@code AssertionError} body to <em>every</em> tail member too (not just the named ones), so a
 * proof reaching any unmodeled tail member still fails NAMED AND LOUD under JBMC — never a silent
 * nondet stub. It is strictly the safer of "silent stub" vs "loud body for the whole tail".
 *
 * <p>The auditing gate still enforces the meaningful parts around the tail: every <em>implemented</em>
 * model method must carry {@link BmcModelConforms}; per-member {@link BmcNotModelled}/{@link
 * BmcNotNeeded} that name a member the real class lacks still fail (dangling-declaration check); and a
 * registered model class with zero audit annotations still fails. The tail only absorbs the
 * <em>undeclared real members</em> that would otherwise fail the per-member enumeration.
 *
 * <p>Prefer an explicit per-member {@link BmcNotModelled}/{@link BmcNotNeeded} for any member a real
 * proof is plausibly going to call (so the decision is visible and documented); reserve the tail for
 * the genuinely-exotic remainder.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS} so the gate and the synthesis pass read it off the
 * model bytecode; never needed at runtime.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BmcModelTail {

    /** Why the remaining (undeclared) real members are intentionally unmodeled. */
    String reason();
}
