package org.bmc4j;

/**
 * Per-proof control over <b>how JBMC models {@code java.lang.String}</b>. Declared via
 * {@link BmcProof#stringMode()}; the build-wide default is
 * {@code bmc { stringMode = "refinement"|"none" }} / {@code -Dbmc.stringMode}.
 *
 * <p>This selects the string DECISION PROCEDURE, not the bytecode: under either mode the same
 * sound {@code String} bytecode + {@code org.cprover.CProverString} shim is analysed. What changes
 * is whether JBMC engages its dedicated string-refinement solver (a fixpoint over a string-constraint
 * theory) on top of that bytecode.
 *
 * <p><b>This is a COMPLETENESS knob, never a soundness one.</b> Switching away from
 * {@link #REFINEMENT} can only turn a would-be {@code VERIFIED}/{@code REFUTED} into {@code UNKNOWN}
 * (an honest "could not decide"), never a false {@code VERIFIED}. So {@link #NONE} is opt-in for the
 * proofs where refinement is the bottleneck; {@link #REFINEMENT} stays the default everywhere else.
 *
 * <p>The set of modes is deliberately open for extension (e.g. a future interned/enum-string mode);
 * today only {@link #REFINEMENT} and {@link #NONE} are implemented.
 */
public enum StringMode {

    /**
     * JBMC's <b>string refinement</b> is ON (the default, the pre-feature behaviour). The string
     * solver reasons about {@code String} CONTENT operations ({@code equals} / {@code contains} /
     * {@code indexOf} / {@code substring} / …) end-to-end, and {@code --max-nondet-string-length}
     * bounds nondeterministic (input) string length. This is the right mode for <b>string-content</b>
     * proofs.
     */
    REFINEMENT,

    /**
     * JBMC's string refinement is <b>OFF</b> ({@code --no-refine-strings}): strings are modelled
     * purely as their char-array bytecode plus the {@code org.cprover.CProverString} shim, with no
     * dedicated string solver layered on top.
     *
     * <p><b>Use it for string-as-DATA / throughput proofs</b> - encoding into buffers,
     * {@code byte}/{@code char} conversion, decimal/numeric formatting over a byte/char buffer -
     * where the refinement solver is the bottleneck and can otherwise explode formula construction.
     * Turning it off lets these proofs become tractable.
     *
     * <p><b>Tradeoff is COMPLETENESS, not soundness.</b> A string CONTENT operation that relies on
     * refinement (and any {@code CProverString} helper the shim does not implement) may fall back to a
     * nondet stub under {@code NONE}, yielding {@code UNKNOWN}. It never yields a false {@code VERIFIED}.
     * For {@code equals}/{@code contains}/{@code substring}-style content proofs, keep
     * {@link #REFINEMENT}.
     *
     * <p>Because refinement is off, {@code --max-nondet-string-length} is NOT passed under this mode
     * (JBMC rejects {@code --max-nondet-string-length} together with {@code --no-refine-strings}).
     */
    NONE
}
