package org.bmc4j;

/**
 * A named, prep-time CONDITION a {@link ConditionalOn} override is gated on. At analysis preparation
 * bmc4j evaluates each condition against the proof's RESOLVED configuration; when it HOLDS, the
 * annotated override body is swapped in for its {@link ConditionalOn#target() target} (every call site
 * of the target is redirected to the override). When it does NOT hold, nothing happens and the target
 * keeps its default body.
 *
 * <p><b>Why this exists.</b> A bmc-models method is the ALWAYS-ON overlay: it applies under BOTH
 * {@link StringMode#REFINEMENT} and {@link StringMode#CHAR_ARRAY_MODEL}. Some JDK methods are sound and
 * fast via a refinement INTRINSIC but WRONG with refinement off, so they need a DIFFERENT body per mode
 * without a blanket override that would degrade the refinement path. {@code @ConditionalOn} lets a model
 * carry a mode-specific alternative beside the default, selected at prep time.
 *
 * <h2>Extensibility</h2>
 * The set of conditions is deliberately OPEN: new conditions (e.g. a future
 * solver-, unwind-, or platform-keyed condition) are added by extending this enum and adding the single
 * matching arm in the one place conditions are evaluated ({@code ConditionalOnBytecode.holds}). Every
 * condition is evaluated there, keyed off this enum, so a new condition is one enum constant plus one
 * arm — no other wiring. Because this is user-facing, document each condition with the resolved-config
 * fact it tests.
 */
public enum BmcCondition {

    /**
     * Holds when this proof runs with JBMC's string REFINEMENT ON ({@link StringMode#REFINEMENT}, the
     * default). Use it for an override that should win on the refinement path (rarely needed: the default
     * body is usually the refinement-correct one).
     */
    STRING_REFINEMENT_ON,

    /**
     * Holds when this proof runs with string refinement OFF ({@link StringMode#CHAR_ARRAY_MODEL} /
     * {@code --no-refine-strings}). Use it for an override that must replace a body which bottoms out in a
     * refinement-only intrinsic. The motivating case: {@code Integer.toString(int)} /
     * {@code Long.toString(long)} delegate to {@code org.cprover.CProverString.toString}, a refinement
     * primitive; with refinement off that primitive yields an UNCONSTRAINED (nondet-length) String, so a
     * no-refine override does a bounded digit build instead.
     */
    STRING_REFINEMENT_OFF
}
