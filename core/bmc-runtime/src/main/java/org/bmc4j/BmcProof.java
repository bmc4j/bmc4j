package org.bmc4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.annotation.Testable;
import org.bmc4j.junit.BmcProofExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a bounded-model-checking proof. It is discovered like a
 * normal JUnit 5 {@link Test} (so it shows up in test reports and IDEs), but
 * instead of executing the body, the {@link BmcProofExtension} runs JBMC against
 * the method as a verification harness and fails the test — with a synthesized
 * stack trace at the offending line — if any allowed input triggers a violation.
 *
 * <pre>{@code
 * @BmcProof
 * void gradeBand_never_throws_for_valid_scores() {
 *     int score = Bmc.anyInt();
 *     Bmc.assume(score >= 1 && score <= 100);
 *     Example.gradeBand(score);   // proven for ALL valid scores
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Testable // make IDE run/debug gutter icons appear on @BmcProof methods
@ExtendWith(BmcProofExtension.class)
public @interface BmcProof {

    /** Loop/recursion unwinding bound. {@code 0} uses the build-configured default. */
    int unwind() default 0;

    /**
     * Bound on the length of symbolic ({@link org.bmc4j.Bmc#anyString} / parameter) strings for this
     * proof. The bundled sound string operations reason character-by-character, so a tighter bound
     * keeps a proof tractable while a looser one covers longer inputs — exactly the kind of thing you
     * tune per proof. {@code 0} uses the build default ({@code -Dbmc.maxStringLength}, else 16).
     */
    int maxStringLength() default 0;

    /**
     * When true, pass {@code --unwinding-assertions} so JBMC reports a failure if
     * the unwind bound is too small to be a real proof, rather than silently
     * trusting an under-explored loop.
     */
    boolean unwindingAssertions() default true;

    /**
     * Explore concurrent interleavings (experimental). Today this enables JBMC's
     * thread analysis ({@code --java-threading}); the attribute is intentionally
     * concurrency-general so the same proofs can target other concurrency models
     * (e.g. Kotlin coroutines) as support lands.
     *
     * <p>Note: write the safety assertion at the point of interest and let the
     * concurrent code race — do not rely on {@code Thread.join()} to sequence
     * (it is not modeled as a barrier).
     */
    boolean concurrent() default false;

    /**
     * Override the SAT/SMT solver for this proof — e.g. {@code "z3"}, {@code "boolector"},
     * {@code "cvc5"} (must be on {@code PATH}). Empty uses the build / {@code -Dbmc.solver} default
     * (JBMC's built-in MiniSat). An SMT backend can be much faster on division- or array-heavy
     * proofs; set it per-proof for the few that need it rather than globally.
     */
    String solver() default "";

    /**
     * Acknowledge methods this proof knowingly relies on as nondet stubs, so they stop
     * warning. JBMC stubs any callee it has no body for to a nondet result; bmc4j surfaces that as a
     * footnote on a green proof (and, under {@code strictStubs}, turns an <em>unacknowledged</em> stub
     * into an UNKNOWN verdict). Listing a stub here says "I've reasoned about this — nondet is sound for
     * what I'm proving" and silences it, in both lenient and strict mode.
     *
     * <p>Each entry is a fully-qualified method name with an optional trailing wildcard:
     * {@code "java.util.Formatter.format"} (exact), {@code "java.util.Formatter.*"} (any method of the
     * class), or {@code "java.util.*"} (any method under the package). The build-wide equivalent is
     * {@code bmc { allowStubs = [...] }}; both sets apply.
     */
    String[] allowStubs() default {};

    /**
     * Acknowledge real JDK members this proof knowingly reaches that bmc4j does NOT model — members
     * a bundled model marks {@code @BmcNotModelled} / {@code @BmcNotNeeded}, or absorbs into its
     * {@code @BmcModelTail}. By DEFAULT, reaching such a member fails the proof as {@code UNKNOWN}
     * (verdict honesty: a model gap is bmc4j's own limitation, not a counterexample in your code — the
     * synthesized loud body trips and the verdict interpreter demotes the would-be refutation, naming
     * the member). Listing a member here OPTS OUT: it degrades to the classic nondet-stub behavior —
     * the member is treated as an unconstrained havoc and the proof proceeds with a loud footnote
     * (NEVER silent), exactly like an acknowledged {@link #allowStubs() stub}.
     *
     * <p>Each entry is a fully-qualified member name with an optional trailing wildcard, matched against
     * the rendered {@code pkg.Class.method} form: {@code "java.util.ArrayList.sort"} (exact name),
     * {@code "java.util.ArrayList.*"} (any method of the class), or {@code "java.util.*"} (any member
     * under the package). The build-wide equivalent is {@code bmc { acknowledgeUnmodelled = [...] }} /
     * {@code -Dbmc.acknowledgeUnmodelled}; both sets apply. Sibling of {@link #allowStubs()}: prefer
     * modeling the member, or restructuring the proof to avoid it, over acknowledging it.
     */
    String[] acknowledgeUnmodelled() default {};

    /**
     * Per-proof wall-clock budget in seconds. If the engine doesn't reach a verdict in
     * time, its process tree is force-killed and the proof is reported as {@code UNKNOWN} (undecided) —
     * which still fails the test, but distinctly from a refutation (no counterexample; with guidance
     * on how to make it decidable). Use it to keep a SAT-pathological proof from hanging the build.
     *
     * <p>{@code 0} uses the build default ({@code bmc { timeoutSeconds = N }} / {@code
     * -Dbmc.timeoutSeconds}); if that is also unset, there is no timeout (the proof runs to
     * completion).
     */
    int timeoutSeconds() default 0;

    /**
     * The verdict this proof is expected to produce; the test <b>passes only if the actual verdict
     * matches</b>. Defaults to {@link Verdict#VERIFIED} — the normal "prove it holds" mode.
     *
     * <p>Declare {@link Verdict#REFUTED} / {@link Verdict#VACUOUS} / {@link Verdict#UNKNOWN} on a
     * <em>fail-on-purpose</em> proof (a deliberately false claim guarding a soundness property, a
     * vacuity demo, an undecidability demo) to turn it into a real regression test: if the guard it
     * protects regresses and the verdict drifts — most dangerously, a false claim coming back
     * VERIFIED — the test fails loudly naming both verdicts, instead of the drift passing unobserved.
     */
    Verdict expect() default Verdict.VERIFIED;
}
