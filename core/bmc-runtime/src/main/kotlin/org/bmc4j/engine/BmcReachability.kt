package org.bmc4j.engine

/**
 * Constants for the **vacuity check**.
 *
 * A `@BmcProof` (or generated enforce-proof) whose assumptions are contradictory passes
 * *vacuously* — JBMC reports SUCCESS over an empty input domain, so the proof checks nothing
 * yet shows green. To make that visible (this tool's "visible over silent" discipline), the bytecode
 * pass [ReachabilityBytecode] injects a *reachability marker* — a synthetic
 * `throw new AssertionError(...)` — immediately before **every** normal `return` of the
 * proof method. JBMC then turns each marker into an `assertion` property:
 *
 * - marker **FAILED** ⇒ that normal exit *is reachable* under the assumptions — good;
 * - marker **SUCCESS** ⇒ that exit is unreachable (the `throw` can never execute).
 *
 * A proof is non-vacuous iff *at least one* of its markers FAILED (some normal exit is
 * reachable). If every marker is SUCCESS, all normal exits are dead ⇒ the assumptions are
 * unsatisfiable ⇒ [VACUOUS_MESSAGE]. Injecting before *every* return (not just the
 * textual end) is what makes early-return / expected-exception proofs sound: an
 * `assumeUnreachable()` in a `catch` legitimately makes that path's marker SUCCESS, but the
 * success path's marker still FAILS, so the proof is correctly non-vacuous.
 *
 * The marker is identified in the parsed JBMC output by a [sentinel source line][SENTINEL_LINE]
 * stamped on the injected instructions — robust against any assertion the user might
 * write inside a proof.
 */
object BmcReachability {

    /**
     * Synthetic source line stamped on every injected marker instruction. JVM `LineNumberTable`
     * entries are `u2`, so this must fit in `[0, 65535]`; `65535` is the maximum and
     * is effectively unreachable as a real source line, so a marker's `sourceLocation.line` is
     * unambiguous in JBMC's `--json-ui` output. (Values above `65535` get truncated by the
     * class-file format and would collide with real lines.)
     */
    const val SENTINEL_LINE = 65_535

    /** Message carried by the injected `AssertionError` (informational; JBMC re-describes it). */
    const val MARKER_TEXT = "bmc4j.reachability"

    /** The dedicated verdict shown when a proof's assumptions are unsatisfiable (every exit dead). */
    const val VACUOUS_MESSAGE = "assumptions are unsatisfiable - this proof checks nothing"

    /** True if [line] is the marker sentinel (i.e. this property is an injected reachability marker). */
    @JvmStatic
    fun isMarkerLine(line: Int): Boolean = line == SENTINEL_LINE
}
