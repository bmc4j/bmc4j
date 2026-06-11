package org.bmc4j.engine

/**
 * Automatic loop-unwind discovery: the climb behind `@BmcProof` with no explicit `unwind`.
 *
 * A beginner should never have to understand loop unwinding or decode a cryptic OOM. When a proof
 * gives no explicit bound (the [org.bmc4j.BmcProof.AUTO] sentinel), bmc4j discovers the smallest
 * bound that yields a CONCLUSIVE verdict by running the engine at increasing bounds and stopping at
 * the first one. An explicit positive `unwind = N` opts out — it pins the bound and never enters here.
 *
 * ## Sound by construction
 * `--unwinding-assertions` stays ON throughout (it already is for every proof). A bound too small to
 * cover the loops therefore fires an unwinding assertion and is classified
 * [UnknownKind.UNWINDING_ASSERTION] — a fail-closed UNKNOWN, never a false VERIFIED. So the search can
 * only ever accept:
 *  - a **VERIFIED** at a bound that fully covered its loops (sound — nothing was truncated), or
 *  - a **REFUTED** with a concrete counterexample (a trace within the bound is a real trace), or
 *  - a **VACUOUS** result (assumptions unsatisfiable) — surfaced as its own verdict via the existing
 *    reachability/vacuity check, NOT accepted as a "the bound works" green. A bound-1 trivial pass on
 *    unsat assumptions thus reports VACUOUS, exactly as a pinned proof would.
 *
 * The bound is a completeness/cost knob, not a soundness knob: under-unwinding only ever costs an
 * extra rung, never a wrong verdict.
 *
 * ## The climb
 * From a [seed] (default 1; a bytecode scan may start nearer the answer), double up — 1, 2, 4, … —
 * never exceeding the [cap] (the build default, currently 16), and finishing with exactly the cap if
 * the doubling would skip past it. At each rung:
 *  - VERIFIED / REFUTED / VACUOUS  -> conclusive: stop and return it, recording the discovered bound;
 *  - UNKNOWN[UNWINDING_ASSERTION]  -> the bound truncated exploration: climb to the next rung;
 *  - any OTHER UNKNOWN (timeout / OOM / parse / crash) -> a rung fell over and a higher bound will not
 *    help: stop and report a clear UNKNOWN (the proof may have an unbounded / too-deep loop).
 *
 * If the cap is reached and every rung only ever hit the unwinding bound, the loops never cover within
 * the cap: report a clear UNKNOWN telling the user this may be an unbounded / too-deep loop and to set
 * an explicit `unwind`. The climb never runs forever.
 */
object AutoUnwind {

    /** The outcome of a climb: the engine [result] to surface, the [bound] it was produced at, and
     *  whether a genuine SEARCH happened (more than the single seed rung) — used only for messaging. */
    class Outcome internal constructor(
            @JvmField val result: JbmcResult,
            @JvmField val bound: Int,
            /** True when the result is a conclusive VERIFIED/REFUTED/VACUOUS that the search LANDED on
             *  (so the discovered bound is worth surfacing/pinning); false for a capped/fell-over UNKNOWN. */
            @JvmField val discovered: Boolean,
            /** Number of engine runs the climb performed (>= 1) — telemetry only. */
            @JvmField val rungs: Int)

    /**
     * Run the climb. [runAt] executes the engine at a given bound and returns its [JbmcResult] (the
     * caller wires it to `backend.verify` with `unwind` set to the bound). [seed] is the first bound
     * (clamped to `1..cap`); [cap] is the highest bound tried (the build default).
     *
     * Pure orchestration over [runAt]: no engine knowledge here, which is what makes it unit-testable
     * with a stub `runAt`.
     */
    @JvmStatic
    fun climb(seed: Int, cap: Int, runAt: (Int) -> JbmcResult): Outcome {
        val ceiling = if (cap < 1) 1 else cap
        var bound = seed.coerceIn(1, ceiling)
        var rungs = 0
        while (true) {
            val result = runAt(bound)
            rungs++
            when {
                // Conclusive: a covered VERIFIED, a real REFUTED, or a VACUOUS (the vacuity check fired —
                // surfaced as its own verdict, never accepted as "the bound works"). Stop and record it.
                result.isVerified || result.isVacuous || isRefuted(result) ->
                    return Outcome(result, bound, discovered = true, rungs = rungs)
                // The bound truncated exploration: climb if we can, else cap out.
                isUnwindingTooSmall(result) -> {
                    if (bound >= ceiling) {
                        // Cap reached and the loops still don't cover — never a false pass; a clear UNKNOWN.
                        return Outcome(cappedUnknown(ceiling), ceiling, discovered = false, rungs = rungs)
                    }
                    bound = nextBound(bound, ceiling)
                }
                // A rung fell over for a reason a higher bound won't fix (timeout / OOM / parse / crash).
                // Climbing would just multiply that cost; stop and surface it as the clear UNKNOWN.
                else -> return Outcome(result, bound, discovered = false, rungs = rungs)
            }
        }
        // (unreachable: every branch returns)
    }

    /** The next bound: double, but never overshoot the cap — land exactly on the cap as the last rung. */
    private fun nextBound(current: Int, cap: Int): Int {
        val doubled = if (current >= cap / 2) cap else current * 2
        return doubled.coerceAtMost(cap)
    }

    private fun isRefuted(result: JbmcResult): Boolean =
            !result.isVerified && !result.isUnknown && !result.isVacuous

    /** True when this UNKNOWN is the "bound too small, climb" signal (an unwinding-assertion firing). */
    private fun isUnwindingTooSmall(result: JbmcResult): Boolean =
            result.isUnknown && result.undecidedKind == UnknownKind.UNWINDING_ASSERTION

    /**
     * The clear UNKNOWN for a proof whose loops never cover within the cap: the climb hit the unwinding
     * bound at every rung up to [cap]. Non-retryable (deterministic), kind UNWINDING_ASSERTION — the
     * same kind a pinned under-unwind reports, so an `expect = UNKNOWN` demo of an unbounded loop still
     * matches.
     */
    internal fun cappedUnknown(cap: Int): JbmcResult =
            JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, cappedReason(cap), null)

    /** The message body for a capped climb (the extension prepends/follows it with the usual framing). */
    internal fun cappedReason(cap: Int): String =
            "auto-unwind found no conclusive result up to unwind=$cap (the cap) — every bound tried hit " +
                    "the unwinding assertion, so this proof may have an unbounded or too-deep loop. " +
                    "Set an explicit @BmcProof(unwind = N) above $cap if a larger fixed bound covers it, " +
                    "or restructure the loop so its trip count is bounded."

    /**
     * The one-line note announcing a DISCOVERED bound, for the rendered proof output and the structured
     * summary detail. Pairs with the cached-bound record: it tells the user the exact value to pin to
     * skip the search next time.
     */
    @JvmStatic
    fun discoveredNote(entryFunction: String, bound: Int): String =
            "  bmc4j: $entryFunction -> auto-unwind: discovered unwind=$bound — pin with " +
                    "@BmcProof(unwind = $bound) to skip the search."
}
