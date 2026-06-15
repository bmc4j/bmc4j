package org.bmc4j.engine

/**
 * Per-loop "smart" unwinding: give each loop its own minimal bound instead of one global bound.
 *
 * ## The problem with a single global bound
 * [AutoUnwind] climbs ONE bound applied to every loop. A single loop that genuinely needs a high bound
 * forces that global bound up, which multiplies the formula size on every OTHER loop in the proof;
 * conversely a global cap trips a loop that legitimately needs a longer bound (e.g. a fixed-length
 * string-literal char-array construction) even when every other loop is tiny. The bounds are coupled.
 *
 * ## The algorithm (one engine run discovers all under-bounded loops, then bump only those)
 * `--unwinding-assertions` reports EVERY loop whose bound was too small in a SINGLE run, so we never
 * binary-search a bound per loop (that would be many engine runs). Each round:
 *  1. Run the proof at the current per-loop [unwindset][BmcRequest.unwindSet] over a small [base] global
 *     bound, with `--unwinding-assertions` on (it always is).
 *  2. If the verdict is conclusive (VERIFIED / REFUTED / VACUOUS) -> done.
 *  3. If it is UNKNOWN[UNWINDING_ASSERTION], read which loops fired ([JbmcResult.unwindingLoops]) and
 *     RAISE the bound for ONLY those loops (step up by [step]x, capped at [cap]); every other loop stays
 *     put. Then re-run.
 *  4. Any OTHER UNKNOWN (timeout / OOM / parse / crash) won't be fixed by a bigger bound -> stop and
 *     surface it.
 *
 * ## Why it MUST cap (and what it can/can't do)
 * This helps a loop with a CONCRETE, discoverable trip count: it converges (its unwinding assertion
 * passes) once its own bound is high enough. It does NOT bound a loop with a SYMBOLIC/data-dependent
 * guard — that loop fails its unwinding assertion at EVERY finite bound, so a naive "bump the failing
 * loops and re-run" would loop forever. Two independent backstops make that impossible:
 *  - a hard [cap] on each loop's bound (a loop already at the cap is never raised again), and
 *  - a hard [maxRounds] on the number of engine rounds.
 * When progress stalls (a round raises no loop because every firing loop is already capped, or no firing
 * loop has a targetable id, or the round budget is spent) the climb STOPS and returns the last UNKNOWN
 * unchanged — the same fail-closed UNKNOWN[UNWINDING_ASSERTION] the global climb caps out with, naming
 * the offending loops so the caller can point at them. It never hangs.
 *
 * ## Soundness
 * Exactly like [AutoUnwind]: `--unwinding-assertions` stays on at every round, so a per-loop bound too
 * small fails closed to UNKNOWN, never a false VERIFIED. Raising a per-loop bound only ever adds covered
 * iterations; it can never turn a real verdict into a wrong one. The bound set is a completeness/cost
 * knob, never a soundness knob.
 *
 * Pure orchestration over [runAt] (which the caller wires to the engine), so it is unit-testable with a
 * stub `runAt` and a fake jbmc result — no engine knowledge lives here.
 */
object SmartUnwind {

    /** The outcome of a smart climb: the engine [result] to surface, the per-loop [unwindSet] it was
     *  produced under (the discovered minimal bounds, worth caching/surfacing on a conclusive landing),
     *  whether the landing is a conclusive verdict worth recording ([discovered]), and how many engine
     *  rounds ran (telemetry). */
    class Outcome internal constructor(
            @JvmField val result: JbmcResult,
            @JvmField val unwindSet: Map<String, Int>,
            @JvmField val discovered: Boolean,
            @JvmField val rounds: Int)

    /**
     * Run the per-loop climb. [base] is the global bound every loop starts at (and stays at unless it
     * fires); [cap] is the hard per-loop ceiling; [step] is the multiplicative bump for a firing loop
     * (>= 2); [maxRounds] is the hard round budget (>= 1). [runAt] runs the engine with the given per-loop
     * unwindset and returns its [JbmcResult] (the caller wires it to `backend.verify` with the unwindset
     * set on the request and the global bound pinned to [base]).
     */
    @JvmStatic
    @JvmOverloads
    fun climb(base: Int, cap: Int, step: Int = 2, maxRounds: Int = 8,
              runAt: (Map<String, Int>) -> JbmcResult): Outcome {
        val ceiling = if (cap < 1) 1 else cap
        val floor = base.coerceIn(1, ceiling)
        val factor = if (step < 2) 2 else step
        val rounds = if (maxRounds < 1) 1 else maxRounds
        // Current per-loop overrides; a loop absent here runs at the global [base]. We only ever ADD or
        // RAISE entries, so the map grows monotonically and each value is bounded by [ceiling].
        val unwindSet = LinkedHashMap<String, Int>()
        var round = 0
        var last: JbmcResult? = null
        while (round < rounds) {
            val result = runAt(unwindSet.toMap())
            round++
            last = result
            when {
                // Conclusive: a covered VERIFIED, a real REFUTED, or a VACUOUS (the vacuity check fired).
                // Stop and record the per-loop bounds that got us there.
                result.isVerified || result.isVacuous || isRefuted(result) ->
                    return Outcome(result, unwindSet.toMap(), discovered = true, rounds = round)
                // The bound truncated SOME loops: raise only those, if any can still be raised.
                isUnwindingTooSmall(result) -> {
                    if (!raiseFiringLoops(result, unwindSet, floor, ceiling, factor)) {
                        // No progress possible this round — every firing loop is already at the cap, or
                        // none carries a targetable loop id (e.g. a recursion overrun). Raising again
                        // would just re-run the identical command forever; stop on the last UNKNOWN.
                        return Outcome(result, unwindSet.toMap(), discovered = false, rounds = round)
                    }
                }
                // A round fell over for a reason a bigger bound won't fix (timeout / OOM / parse / crash):
                // stop and surface it rather than multiplying the cost across more rounds.
                else -> return Outcome(result, unwindSet.toMap(), discovered = false, rounds = round)
            }
        }
        // Round budget spent and still under-bounded: stop on the last UNKNOWN (it names the loops still
        // firing) rather than running forever. A symbolic-guard loop lands here — it never converges.
        return Outcome(last!!, unwindSet.toMap(), discovered = false, rounds = round)
    }

    /**
     * Raise the per-loop bound for every loop that fired this round, capped at [ceiling]. A loop not yet
     * in [unwindSet] starts from [floor] (its effective current bound = the global base) and is bumped to
     * `floor * factor`; a loop already overridden is bumped from its current override. A loop already at
     * [ceiling], or one with no targetable [loopId][JbmcResult.UnwindingLoop.loopId] (a recursion
     * overrun), is skipped. Returns true iff at least one loop's bound actually increased — the caller
     * uses that as the "made progress" signal that prevents an infinite climb.
     */
    private fun raiseFiringLoops(result: JbmcResult, unwindSet: MutableMap<String, Int>,
                                 floor: Int, ceiling: Int, factor: Int): Boolean {
        var raised = false
        for (loop in result.unwindingLoops) {
            val id = loop.loopId ?: continue // recursion overrun / unparsed id: not targetable per-loop
            val current = unwindSet[id] ?: floor
            if (current >= ceiling) {
                continue // already at the hard per-loop cap; raising it again is futile
            }
            val next = (current.toLong() * factor).coerceAtMost(ceiling.toLong()).toInt()
            if (next > current) {
                unwindSet[id] = next
                raised = true
            }
        }
        return raised
    }

    private fun isRefuted(result: JbmcResult): Boolean =
            !result.isVerified && !result.isUnknown && !result.isVacuous

    /** True when this UNKNOWN is the "some loop's bound too small" signal (an unwinding-assertion firing). */
    private fun isUnwindingTooSmall(result: JbmcResult): Boolean =
            result.isUnknown && result.undecidedKind == UnknownKind.UNWINDING_ASSERTION
}
