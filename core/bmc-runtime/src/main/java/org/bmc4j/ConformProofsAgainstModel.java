package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conform a {@code src/bmcModel} user model against the <b>real implementation</b> for one proof, by
 * running that proof <b>twice</b> and requiring BOTH runs to reach the proof's expected verdict:
 * <ul>
 *   <li>the <b>model leg</b> &mdash; the normal run, with the named model(s) substituted onto the
 *       analysis classpath (exactly as every proof runs today); and</li>
 *   <li>the <b>real leg</b> &mdash; the SAME proof with the named model(s) <b>excluded</b> from the
 *       model overlay, so the proof is analysed against the real class instead of the model.</li>
 * </ul>
 *
 * <pre>{@code
 * @BmcProof
 * @ConformProofsAgainstModel(NoCollisionMap.class)
 * void put_then_get_returns_value() { ... }
 * }</pre>
 *
 * <p>The proof PASSES only if both legs reach {@link BmcProof#expect()} (default
 * {@link Verdict#VERIFIED}). If either leg fails &mdash; UNKNOWN, a timeout, or a verdict that does
 * not match {@code expect} &mdash; the proof fails, naming WHICH leg (real vs model) failed and its
 * verdict. That is the whole semantics: there is no real-vs-model verdict-diff to reason about,
 * because failing either leg fails the proof. An <b>unsound</b> model &mdash; one that VERIFIES while
 * the real implementation REFUTES the same property &mdash; makes the real leg fail, so the proof
 * fails and the unsoundness is surfaced.
 *
 * <p>This is how a user gains confidence that the model the rest of their proofs lean on is sound for
 * what those proofs actually exercise.
 *
 * <h2>Scope &mdash; this is the author's responsibility</h2>
 * <p>It is <b>opt-in</b> and <b>proof-scoped</b>: it only conforms what the annotated proof exercises.
 * Coverage is the proof author's responsibility &mdash; passing this does NOT prove the model fully
 * sound, only that it agrees with the real implementation on the paths this proof drives. If a proof's
 * real leg is intractable (the real implementation is exactly what the model exists to tame), simply
 * do not annotate that proof: the model stays usable everywhere else, and you conform it with the
 * proofs whose real legs ARE tractable.
 *
 * <p>Placed on a TYPE it applies to every {@code @BmcProof} method in that class; placed on a METHOD
 * it applies to that proof (its value is merged with any class-level value). Listing a class this
 * proof does not actually exercise is harmless &mdash; excluding an unreached model changes nothing.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConformProofsAgainstModel {

    /** The user model class(es) to conform: each is excluded from the model overlay on the real leg. */
    Class<?>[] value();
}
