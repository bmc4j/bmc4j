package org.bmc4j.engine;

/**
 * Constants for the <b>vacuity check</b>.
 *
 * <p>A {@code @BmcProof} (or generated enforce-proof) whose assumptions are contradictory passes
 * <em>vacuously</em> — JBMC reports SUCCESS over an empty input domain, so the proof checks nothing
 * yet shows green. To make that visible (this tool's "visible over silent" discipline), the bytecode
 * pass {@link ReachabilityBytecode} injects a <em>reachability marker</em> — a synthetic
 * {@code throw new AssertionError(...)} — immediately before <b>every</b> normal {@code return} of the
 * proof method. JBMC then turns each marker into an {@code assertion} property:
 *
 * <ul>
 *   <li>marker <b>FAILED</b> ⇒ that normal exit <em>is reachable</em> under the assumptions — good;</li>
 *   <li>marker <b>SUCCESS</b> ⇒ that exit is unreachable (the {@code throw} can never execute).</li>
 * </ul>
 *
 * A proof is non-vacuous iff <em>at least one</em> of its markers FAILED (some normal exit is
 * reachable). If every marker is SUCCESS, all normal exits are dead ⇒ the assumptions are
 * unsatisfiable ⇒ {@link #VACUOUS_MESSAGE}. Injecting before <em>every</em> return (not just the
 * textual end) is what makes early-return / expected-exception proofs sound: an {@code
 * assumeUnreachable()} in a {@code catch} legitimately makes that path's marker SUCCESS, but the
 * success path's marker still FAILS, so the proof is correctly non-vacuous.
 *
 * <p>The marker is identified in the parsed JBMC output by a {@linkplain #SENTINEL_LINE sentinel
 * source line} stamped on the injected instructions — robust against any assertion the user might
 * write inside a proof.
 */
public final class BmcReachability {

    private BmcReachability() {
    }

    /**
     * Synthetic source line stamped on every injected marker instruction. JVM {@code LineNumberTable}
     * entries are {@code u2}, so this must fit in {@code [0, 65535]}; {@code 65535} is the maximum and
     * is effectively unreachable as a real source line, so a marker's {@code sourceLocation.line} is
     * unambiguous in JBMC's {@code --json-ui} output. (Values above {@code 65535} get truncated by the
     * class-file format and would collide with real lines.)
     */
    public static final int SENTINEL_LINE = 65_535;

    /** Message carried by the injected {@code AssertionError} (informational; JBMC re-describes it). */
    public static final String MARKER_TEXT = "bmc4j.reachability";

    /** The dedicated verdict shown when a proof's assumptions are unsatisfiable (every exit dead). */
    public static final String VACUOUS_MESSAGE =
            "assumptions are unsatisfiable - this proof checks nothing";

    /** True if {@code line} is the marker sentinel (i.e. this property is an injected reachability marker). */
    public static boolean isMarkerLine(int line) {
        return line == SENTINEL_LINE;
    }
}
