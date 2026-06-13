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
 * the cap. The climb itself is the discriminator: a loop that just needs a bigger FINITE bound converges
 * (its unwinding assertion passes) at some rung, so a climb that reaches the cap STILL firing the same
 * assertion means the loop's trip count is DATA-DEPENDENT (symbolic) and a larger fixed `unwind` can never
 * cover it. We report that as a clear UNKNOWN naming the offending loop (method + file:line from the
 * failing unwinding property) and pointing at the levers that actually help — constrain the symbolic
 * input, split the proof, or contract the heavy callee. The climb never runs forever.
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
                        // Cap reached and the loops still don't cover. The climb ran `--unwinding-assertions`
                        // at every bound 1..cap and the SAME unwinding assertion fired at each — the
                        // discriminator: a loop that just needs a bigger FINITE bound CONVERGES (passes) at
                        // some rung, so reaching the cap still failing means the trip count is
                        // DATA-DEPENDENT (symbolic) and raising unwind will not help. Carry the loops the
                        // last (capping) rung named so the diagnostic can point at the offending method:line.
                        return Outcome(cappedUnknown(ceiling, result.unwindingLoops), ceiling,
                                discovered = false, rungs = rungs)
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
     * matches. [loops] are the offending loops the capping rung named; when present the reason is the
     * actionable DATA-DEPENDENT-bound diagnostic, and the result carries them onward.
     */
    @JvmStatic
    @JvmOverloads
    internal fun cappedUnknown(cap: Int, loops: List<JbmcResult.UnwindingLoop> = emptyList()): JbmcResult =
            JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, cappedReason(cap, loops), null)
                    .withUnwindingLoops(loops)

    /**
     * The message body for a capped climb (the extension prepends/follows it with the usual framing).
     *
     * Capping out IS the data-dependent signal: `--unwinding-assertions` ran at every bound 1..[cap] and
     * the loop's assertion fired at all of them, so a larger FIXED unwind would not converge — the trip
     * count is symbolic. When the offending [loops] are known we name them and say so plainly; otherwise
     * we fall back to the older "unbounded or too-deep" wording (which still leaves room for a larger
     * fixed bound). Either way `@BmcProof(expect = TIMEOUT/UNKNOWN)` demos keep matching the kind.
     */
    internal fun cappedReason(cap: Int, loops: List<JbmcResult.UnwindingLoop> = emptyList()): String {
        if (loops.isEmpty()) {
            return "auto-unwind found no conclusive result up to unwind=$cap (the cap) — every bound " +
                    "tried hit the unwinding assertion, so this proof may have an unbounded or too-deep " +
                    "loop. Set an explicit @BmcProof(unwind = N) above $cap if a larger fixed bound " +
                    "covers it, or restructure the loop so its trip count is bounded."
        }
        val named = loops.joinToString("; ") { it.describe() }
        val subject = if (loops.size == 1) "this loop's trip count is" else "these loops' trip counts are"
        return "auto-unwind climbed to the cap (unwind=$cap) and the unwinding assertion still fired at " +
                "every bound 1..$cap, so $subject DATA-DEPENDENT (bounded by a symbolic value, not a " +
                "constant) — raising `unwind` will NOT help; a larger fixed bound can never cover a " +
                "symbolic trip count. Offending loop: $named. " +
                "Instead: constrain the symbolic input that bounds it with assume(...) / a tighter range, " +
                "split the proof so the loop runs over a fixed-size input, or model/contract " +
                "(@Requires/@Ensures) the heavy callee whose state feeds the loop."
    }

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
