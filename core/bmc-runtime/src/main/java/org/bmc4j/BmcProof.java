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

    /**
     * Loop/recursion unwinding bound.
     *
     * <p>By DEFAULT ({@link #AUTO}) bmc4j <b>auto-discovers</b> the bound: it runs the engine at
     * increasing bounds (with {@code --unwinding-assertions} on throughout, so an under-unwind is a
     * fail-closed UNKNOWN, never a false VERIFIED) and stops at the first conclusive verdict — the
     * minimal covering bound. A beginner never has to understand loop unwinding or decode an OOM. The
     * discovered bound is reported and cached, so steady-state runs skip the search.
     *
     * <p>A <b>positive</b> {@code N} opts out and PINS the bound (the expert override) — exactly the
     * pre-AUTO behaviour. {@code 0} uses the build-configured default ({@code -Dbmc.unwind}, else the
     * cap, currently 16).
     */
    int unwind() default AUTO;

    /**
     * The sentinel {@link #unwind()} value selecting automatic unwind discovery (the default). A
     * positive bound opts out and pins it; {@code 0} pins the build default. Public so a proof can
     * write {@code @BmcProof(unwind = BmcProof.AUTO)} to request auto-discovery explicitly.
     */
    int AUTO = -1;

    /**
     * The MAXIMUM bound that AUTO unwind-discovery (and per-loop smart unwinding) may climb to for THIS
     * proof, when {@link #unwind()} is left on {@link #AUTO}.
     *
     * <p>Raises the per-proof climb CAP above the build default (currently 16) WITHOUT pinning the
     * bound: the climb still discovers the smallest sufficient bound per loop, it is just permitted to
     * go higher. Use this instead of a fixed {@code unwind = N} when a single loop needs a bound over
     * the default cap but you still want auto/smart unwinding (a fixed {@code unwind = N} is non-AUTO,
     * so it pins one global bound on EVERY loop and disables discovery).
     *
     * <p>{@code 0} (default) uses the build-configured cap ({@code -Dbmc.unwindCap}, else 16). Ignored
     * when {@link #unwind()} pins a concrete bound (a pin runs that bound directly, no climb).
     */
    int unwindMax() default 0;

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
     * Acknowledge real JDK members this proof knowingly reaches that bmc4j cannot model — members
     * a bundled model marks {@code @BmcUnmodelable} (loud-if-reached), or absorbs into its
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
     * Whether bmc4j may <b>elide the construction of a thrown exception's message</b> for this proof —
     * dropping the (often expensive) computation that builds an error string and passing {@code null}
     * instead, so the exception is still constructed and thrown but its message is never built. This
     * makes a proof over a function that builds a dynamic error message on a branch the proof never
     * takes (e.g. a byte&rarr;String materialization on an overflow path) tractable instead of
     * timing out: the message is never <em>read</em>, so dropping its construction cannot change the
     * verdict, only removes the symbolic cost that poisoned the proof.
     *
     * <ul>
     *   <li>{@link RemoveExceptionMessages#AUTO} (default) — elide an exception message <b>iff a coarse
     *       observability gate clears</b>: bmc4j scans this proof's reachable cone for ANY code that
     *       observes a {@code Throwable}'s message ({@code getMessage} / {@code getLocalizedMessage} /
     *       {@code getStackTrace} / {@code printStackTrace} / {@code toString}). If none exists, no code
     *       reads any exception message, so eliding every exception message is <b>fully sound</b>. If any
     *       observer exists (or the cone can't be bounded), AUTO does not elide. Never a caveat — when
     *       AUTO elides, the value really was dead.</li>
     *   <li>{@link RemoveExceptionMessages#ON} — <b>force</b> elision even if an observer exists. This is a
     *       <em>user assertion</em> that the elided messages don't affect what you prove; a
     *       VERIFIED reached via forced elision is surfaced with a footnote so it is never read as
     *       unconditional.</li>
     *   <li>{@link RemoveExceptionMessages#OFF} — never elide (the pre-feature behaviour).</li>
     * </ul>
     *
     * <p>The build-wide default is {@code bmc { removeExceptionMessages = "auto"|"on"|"off" }} /
     * {@code -Dbmc.removeExceptionMessages}; a per-proof value other than {@link RemoveExceptionMessages#AUTO} overrides it.
     */
    RemoveExceptionMessages removeExceptionMessages() default RemoveExceptionMessages.AUTO;

    /**
     * How JBMC models {@code java.lang.String} for this proof. See {@link StringMode} for the full
     * semantics; in short:
     *
     * <ul>
     *   <li>{@link StringMode#REFINEMENT} (default) — JBMC's string-refinement solver is ON, so
     *       {@code String} CONTENT operations ({@code equals}/{@code contains}/{@code substring}/…)
     *       are reasoned end-to-end and {@code --max-nondet-string-length} bounds symbolic input
     *       strings. The right mode for <b>string-content</b> proofs.</li>
     *   <li>{@link StringMode#CHAR_ARRAY_MODEL} — substitutes a char-array {@code String} model with string
     *       refinement OFF ({@code --no-refine-strings}): {@code java.lang.String} is the bundled
     *       char-array-backed model + the {@code org.cprover.CProverString} shim. Use it for
     *       <b>string-as-DATA / throughput</b> proofs (encoding into buffers,
     *       {@code byte}&lt;-&gt;{@code char}, decimal/numeric formatting) where refinement is the
     *       bottleneck and can explode formula construction.</li>
     * </ul>
     *
     * <p><b>This is a COMPLETENESS knob, never a soundness one.</b> {@link StringMode#CHAR_ARRAY_MODEL} can only
     * turn a would-be verdict into {@code UNKNOWN} (a string-content op the shim cannot decide
     * nondet-stubs), never a false {@code VERIFIED}. So it is opt-in for string-encoding/throughput
     * proofs; {@link StringMode#REFINEMENT} stays the default for string-content proofs. Note that
     * {@code --max-nondet-string-length} is NOT passed under {@code CHAR_ARRAY_MODEL} (JBMC rejects it
     * together with {@code --no-refine-strings}).
     *
     * <p>The build-wide default is {@code bmc { stringMode = "refinement"|"char_array_model" }} /
     * {@code -Dbmc.stringMode}; a per-proof value other than {@link StringMode#REFINEMENT} overrides it.
     */
    StringMode stringMode() default StringMode.REFINEMENT;

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
