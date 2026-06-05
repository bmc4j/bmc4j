package org.bmc4j;

/**
 * The verdict a {@link BmcProof} can declare it expects via {@link BmcProof#expect()}.
 *
 * <p>Most proofs expect {@link #VERIFIED} (the default). A <em>fail-on-purpose</em> proof — a
 * deliberately false claim guarding a soundness property, a vacuity demo, an undecidability demo —
 * declares the non-VERIFIED verdict it exists to produce, and the test then <b>passes only if the
 * actual verdict matches</b>. A false claim that stops being refutable (verdict drifts to VERIFIED)
 * becomes a loud failure instead of an unobserved regression.
 *
 * <p>Deliberately named after the verdict, not "unsound" or "fail": a refuted bug-demo is the tool
 * working <em>correctly</em> — "unsound" is reserved for a false green (see SECURITY.md).
 */
public enum Verdict {

    /** The property holds for every input within the bound (the default expectation). */
    VERIFIED,

    /** JBMC produced a counterexample. */
    REFUTED,

    /**
     * Undecided within budget: timeout, solver gave up, or unparseable engine output.
     * Expecting UNKNOWN accepts <em>any</em> genuine undecided outcome, {@link #TIMEOUT} included.
     * An engine <em>infrastructure</em> failure (the engine couldn't run at all) does NOT satisfy an
     * expected-UNKNOWN — that would let a broken engine masquerade as an undecidability demo.
     */
    UNKNOWN,

    /**
     * Undecided specifically because the per-proof wall-clock budget expired and the engine
     * process tree was force-killed — the structured subtype of {@link #UNKNOWN}. Declaring
     * {@code expect = TIMEOUT} asserts the budget actually fired: a solver crash or unparseable
     * output will NOT satisfy it (use {@link #UNKNOWN} for "any undecided"). Inherently
     * machine-speed dependent — a solver finishing inside the budget flips the verdict — so size
     * the formula well past the budget (the bundled demo pits a minutes-scale formula against a
     * 1-second budget).
     */
    TIMEOUT,

    /**
     * The proof's assumptions are jointly unsatisfiable — it would have "verified" over an empty
     * input domain and checked nothing (the vacuity check's dedicated verdict).
     */
    VACUOUS
}
